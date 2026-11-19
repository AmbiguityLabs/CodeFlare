package com.example.editor

import com.example.data.remote.CfD1Database
import com.example.data.remote.CfDurableObjectNamespace
import com.example.data.remote.CfKvNamespace
import com.example.data.remote.CfQueue
import com.example.data.remote.CfR2Bucket
import com.example.data.remote.CodeBindingUsage
import com.example.data.remote.D1SchemaGraph
import com.example.data.remote.DiagnosticItem
import com.example.data.remote.DiagnosticSeverity
import com.example.data.remote.EdgeTopologyGraph
import com.example.data.remote.LinkedGitRepoState
import com.example.data.remote.TopologyEdge
import com.example.data.remote.TopologyEdgeStatus
import com.example.data.remote.TopologyNode
import com.example.data.remote.TopologyNodeKind
import com.example.data.remote.WorkerFile
import com.example.data.remote.WorkerLanguage
import com.example.data.remote.WorkerWorkspace

object EdgeTopologyAnalyzer {
  private val envMemberRegex = Regex("""\benv\.([A-Za-z_][A-Za-z0-9_]*)(?:\.([A-Za-z_][A-Za-z0-9_]*))?""")

  private fun maskStringsAndComments(code: String): String {
    val len = code.length
    val out = StringBuilder(len)
    var i = 0
    while (i < len) {
      val c = code[i]
      val next = if (i + 1 < len) code[i + 1] else '\u0000'
      if (c == '/' && next == '/') {
        out.append("  ")
        i += 2
        while (i < len && code[i] != '\n') {
          out.append(' ')
          i++
        }
        continue
      }
      if (c == '/' && next == '*') {
        out.append("  ")
        i += 2
        while (i < len) {
          val ch = code[i]
          if (ch == '*' && i + 1 < len && code[i + 1] == '/') {
            out.append("  ")
            i += 2
            break
          }
          out.append(if (ch == '\n') '\n' else ' ')
          i++
        }
        continue
      }
      if (c == '\'' || c == '"' || c == '`') {
        val q = c
        out.append(' ')
        i++
        while (i < len) {
          val ch = code[i]
          if (ch == '\\' && i + 1 < len) {
            out.append(' ')
            val esc = code[i + 1]
            out.append(if (esc == '\n') '\n' else ' ')
            i += 2
            continue
          }
          out.append(if (ch == '\n') '\n' else ' ')
          i++
          if (ch == q) break
        }
        continue
      }
      out.append(c)
      i++
    }
    return out.toString()
  }

  private fun offsetToLineCol(code: String, offset: Int): Pair<Int, Int> {
    val clamped = offset.coerceIn(0, code.length)
    var line = 1
    var col = 1
    for (i in 0 until clamped) {
      if (code[i] == '\n') {
        line++
        col = 1
      } else {
        col++
      }
    }
    return line to col
  }

  fun scanWorkspaceBindingUsages(files: List<WorkerFile>): List<CodeBindingUsage> {
    val usages = mutableListOf<CodeBindingUsage>()
    files.forEach { file ->
      if (file.isVirtualReadOnly) return@forEach
      if (file.language !in setOf(WorkerLanguage.JAVASCRIPT, WorkerLanguage.TYPESCRIPT)) return@forEach
      val masked = maskStringsAndComments(file.content)
      envMemberRegex.findAll(masked).forEach { match ->
        val name = match.groupValues[1]
        val member = match.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }
        val (line, col) = offsetToLineCol(file.content, match.range.first)
        usages.add(
          CodeBindingUsage(
            bindingName = name,
            filePath = file.path,
            line = line,
            column = col,
            memberAccess = member,
          ),
        )
      }
    }
    return usages
  }

  fun findBindingAtCursor(code: String, cursorOffset: Int): String? {
    if (code.isEmpty()) return null
    val clamped = cursorOffset.coerceIn(0, code.length)
    val lineStart = code.lastIndexOf('\n', (clamped - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
    val lineEnd = code.indexOf('\n', clamped).let { if (it < 0) code.length else it }
    if (lineStart >= lineEnd) return null
    val lineText = code.substring(lineStart, lineEnd)
    val localOffset = (clamped - lineStart).coerceIn(0, lineText.length)
    val matches = envMemberRegex.findAll(lineText).toList()
    val exact = matches.firstOrNull { localOffset in it.range.first..(it.range.last + 1) }
    if (exact != null) return exact.groupValues[1]
    return matches.minByOrNull { m ->
      val center = (m.range.first + m.range.last) / 2
      kotlin.math.abs(center - localOffset)
    }?.takeIf { matches.size == 1 }?.groupValues?.get(1)
  }

  fun buildBindingDriftDiagnostics(
    workspace: WorkerWorkspace?,
    files: List<WorkerFile>,
  ): List<DiagnosticItem> {
    if (workspace == null) return emptyList()
    val usages = scanWorkspaceBindingUsages(files)
    val declaredNames = workspace.bindings.map { it.name }.toSet()
    val wranglerFile =
      files.firstOrNull {
        it.path.equals("wrangler.jsonc", true) ||
          it.path.equals("wrangler.json", true) ||
          it.path.equals("wrangler.toml", true)
      }
    val wranglerBindings =
      wranglerFile?.let { WranglerSyncParser.parse(it.path, it.content).getOrNull()?.bindings }
        ?.map { it.name }
        ?.toSet()
        .orEmpty()
    val allKnownDeclared = declaredNames + wranglerBindings
    val out = mutableListOf<DiagnosticItem>()

    usages
      .filter { it.bindingName !in allKnownDeclared }
      .groupBy { it.bindingName }
      .forEach { (name, hits) ->
        val first = hits.first()
        out.add(
          DiagnosticItem(
            filePath = first.filePath,
            line = first.line,
            column = first.column + 4,
            message = "Unbound Worker env.$name referenced in code (bind in Config -> Bindings)",
            severity = DiagnosticSeverity.WARNING,
            length = name.length,
          ),
        )
      }

    val usedNames = usages.map { it.bindingName }.toSet()
    val configTargetPath = wranglerFile?.path ?: workspace.entryModulePath
    workspace.bindings.forEach { b ->
      if (b.name !in usedNames && b.type != "secret_text") {
        out.add(
          DiagnosticItem(
            filePath = configTargetPath,
            line = 1,
            column = 1,
            message = "Binding '${b.name}' (${b.type}) is configured on Worker but not referenced as env.${b.name}",
            severity = DiagnosticSeverity.INFO,
            length = 1,
          ),
        )
      }
    }
    return out
  }

  fun buildTopologyGraph(
    workspace: WorkerWorkspace?,
    d1Databases: List<CfD1Database>,
    kvNamespaces: List<CfKvNamespace>,
    r2Buckets: List<CfR2Bucket>,
    durableObjects: List<CfDurableObjectNamespace>,
    queues: List<CfQueue>,
    linkedGitRepo: LinkedGitRepoState?,
    activeD1Schema: D1SchemaGraph?,
    activeD1DatabaseId: String?,
  ): EdgeTopologyGraph {
    if (workspace == null) return EdgeTopologyGraph()
    val nodes = mutableListOf<TopologyNode>()
    val edges = mutableListOf<TopologyEdge>()
    val usages = scanWorkspaceBindingUsages(workspace.files)
    val usageByName = usages.groupBy { it.bindingName }

    val workerNodeId = "worker:${workspace.scriptName}"
    nodes.add(
      TopologyNode(
        id = workerNodeId,
        label = workspace.scriptName,
        subtitle = "${workspace.files.count { !it.isVirtualReadOnly }} files · ${workspace.bindings.size} bindings",
        kind = TopologyNodeKind.WORKER,
        columnLayer = 1,
        badgeCount = usages.size.takeIf { it > 0 },
      ),
    )

    if (linkedGitRepo != null) {
      val gitId = "git:${linkedGitRepo.fullName}"
      nodes.add(
        TopologyNode(
          id = gitId,
          label = linkedGitRepo.fullName,
          subtitle = "branch: ${linkedGitRepo.branch}",
          kind = TopologyNodeKind.GITHUB_REPO,
          columnLayer = 0,
        ),
      )
      edges.add(
        TopologyEdge(
          id = "edge_git_$workerNodeId",
          fromNodeId = gitId,
          toNodeId = workerNodeId,
          label = "sync",
          status = TopologyEdgeStatus.WIRED,
        ),
      )
    }

    if (workspace.workersDevEnabled && !workspace.workersDevUrl.isNullOrBlank()) {
      val devId = "route:workers_dev"
      nodes.add(
        TopologyNode(
          id = devId,
          label = "*.workers.dev",
          subtitle = workspace.workersDevUrl!!.removePrefix("https://"),
          kind = TopologyNodeKind.ROUTE_DOMAIN,
          columnLayer = 0,
        ),
      )
      edges.add(
        TopologyEdge(
          id = "edge_dev_route",
          fromNodeId = devId,
          toNodeId = workerNodeId,
          label = "HTTPS",
          status = TopologyEdgeStatus.WIRED,
        ),
      )
    }

    workspace.domains.take(4).forEach { dom ->
      val domId = "domain:${dom.id.ifBlank { dom.hostname }}"
      nodes.add(
        TopologyNode(
          id = domId,
          label = dom.hostname,
          subtitle = "Custom Domain",
          kind = TopologyNodeKind.ROUTE_DOMAIN,
          columnLayer = 0,
        ),
      )
      edges.add(
        TopologyEdge(
          id = "edge_$domId",
          fromNodeId = domId,
          toNodeId = workerNodeId,
          label = "DNS",
          status = TopologyEdgeStatus.WIRED,
        ),
      )
    }

    workspace.routes.take(4).forEach { route ->
      val rId = "route:${route.id.ifBlank { route.pattern }}"
      nodes.add(
        TopologyNode(
          id = rId,
          label = route.pattern,
          subtitle = "Zone Route",
          kind = TopologyNodeKind.ROUTE_DOMAIN,
          columnLayer = 0,
        ),
      )
      edges.add(
        TopologyEdge(
          id = "edge_$rId",
          fromNodeId = rId,
          toNodeId = workerNodeId,
          label = "HTTP",
          status = TopologyEdgeStatus.WIRED,
        ),
      )
    }

    workspace.schedules.take(4).forEach { cron ->
      val cId = "cron:${cron.cron}"
      nodes.add(
        TopologyNode(
          id = cId,
          label = cron.cron,
          subtitle = "Cron Trigger",
          kind = TopologyNodeKind.CRON,
          columnLayer = 0,
        ),
      )
      edges.add(
        TopologyEdge(
          id = "edge_$cId",
          fromNodeId = cId,
          toNodeId = workerNodeId,
          label = "scheduled",
          status = TopologyEdgeStatus.WIRED,
        ),
      )
    }

    val unusedNames = mutableListOf<String>()
    val declaredNames = mutableSetOf<String>()

    workspace.bindings.forEach { binding ->
      declaredNames.add(binding.name)
      val usedHits = usageByName[binding.name].orEmpty()
      val edgeStatus =
        if (usedHits.isEmpty() && binding.type != "secret_text") {
          unusedNames.add(binding.name)
          TopologyEdgeStatus.UNUSED_IN_CODE
        } else {
          TopologyEdgeStatus.WIRED
        }

      val (kind, sub, resId, badgeCount) =
        when (binding.type) {
          "d1" -> {
            val db = d1Databases.firstOrNull { it.uuid == binding.databaseId }
            val tblCount =
              if (activeD1DatabaseId == binding.databaseId && activeD1Schema != null && activeD1Schema.tables.isNotEmpty()) {
                activeD1Schema.tables.size
              } else {
                db?.numTables
              }
            Quad(
              TopologyNodeKind.D1_DB,
              db?.name ?: binding.databaseId?.take(10).orEmpty(),
              binding.databaseId,
              tblCount,
            )
          }
          "kv_namespace" -> {
            val kv = kvNamespaces.firstOrNull { it.id == binding.namespaceId }
            Quad(
              TopologyNodeKind.KV_NS,
              kv?.title ?: binding.namespaceId?.take(10).orEmpty(),
              binding.namespaceId,
              null,
            )
          }
          "r2_bucket" -> {
            val r2 = r2Buckets.firstOrNull { it.name == binding.bucketName }
            Quad(
              TopologyNodeKind.R2_BUCKET,
              r2?.name ?: binding.bucketName.orEmpty(),
              binding.bucketName,
              null,
            )
          }
          "queue" -> {
            val q = queues.firstOrNull { it.queueName == binding.queueName }
            Quad(
              TopologyNodeKind.QUEUE,
              q?.queueName ?: binding.queueName.orEmpty(),
              q?.queueId ?: binding.queueName,
              null,
            )
          }
          "durable_object_namespace" -> {
            val doNs = durableObjects.firstOrNull { it.className == binding.className || it.name == binding.className }
            Quad(
              TopologyNodeKind.DO_NAMESPACE,
              doNs?.className ?: binding.className.orEmpty(),
              doNs?.id ?: binding.className,
              null,
            )
          }
          "service" ->
            Quad(
              TopologyNodeKind.SERVICE_WORKER,
              binding.service ?: "worker",
              binding.service,
              null,
            )
          "ai" ->
            Quad(TopologyNodeKind.AI_BINDING, "Workers AI", "ai", null)
          else ->
            Quad(
              TopologyNodeKind.ENV_VAR,
              if (binding.type == "secret_text") "encrypted secret" else binding.text.orEmpty().take(18),
              binding.name,
              null,
            )
        }

      val firstHit = usedHits.firstOrNull()
      val nodeId = "bind:${binding.name}"
      nodes.add(
        TopologyNode(
          id = nodeId,
          label = "env.${binding.name}",
          subtitle = sub,
          kind = kind,
          columnLayer = 2,
          bindingName = binding.name,
          resourceId = resId,
          filePath = firstHit?.filePath,
          lineNumber = firstHit?.line,
          status = edgeStatus,
          badgeCount = badgeCount,
        ),
      )
      edges.add(
        TopologyEdge(
          id = "edge_$nodeId",
          fromNodeId = workerNodeId,
          toNodeId = nodeId,
          label = if (usedHits.isEmpty()) "unused" else "${usedHits.size} ref",
          status = edgeStatus,
        ),
      )
    }

    val missingNames = mutableListOf<String>()
    usageByName.forEach { (name, hits) ->
      if (name !in declaredNames) {
        missingNames.add(name)
        val first = hits.first()
        val nodeId = "missing:$name"
        nodes.add(
          TopologyNode(
            id = nodeId,
            label = "env.$name",
            subtitle = "Unbound in ${first.filePath}:${first.line}",
            kind = TopologyNodeKind.MISSING_BINDING,
            columnLayer = 2,
            bindingName = name,
            filePath = first.filePath,
            lineNumber = first.line,
            status = TopologyEdgeStatus.UNDECLARED_BINDING,
            badgeCount = hits.size,
          ),
        )
        edges.add(
          TopologyEdge(
            id = "edge_$nodeId",
            fromNodeId = workerNodeId,
            toNodeId = nodeId,
            label = "missing",
            status = TopologyEdgeStatus.UNDECLARED_BINDING,
          ),
        )
      }
    }

    return EdgeTopologyGraph(
      nodes = nodes,
      edges = edges,
      codeUsages = usages,
      missingBindingNames = missingNames.sorted(),
      unusedBindingNames = unusedNames.sorted(),
    )
  }

  private data class Quad<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
  )
}
