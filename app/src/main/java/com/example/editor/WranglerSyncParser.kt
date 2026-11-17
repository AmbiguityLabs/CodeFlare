package com.example.editor

import com.example.data.remote.CfWorkerBinding
import com.example.data.remote.WorkerLanguage
import com.example.data.remote.WorkerWorkspace
import org.json.JSONArray
import org.json.JSONObject

data class ParsedWranglerConfig(
  val scriptName: String? = null,
  val main: String? = null,
  val compatibilityDate: String? = null,
  val compatibilityFlags: List<String> = emptyList(),
  val workersDev: Boolean? = null,
  val bindings: List<CfWorkerBinding> = emptyList(),
  val crons: List<String> = emptyList(),
)

data class CodeSymbol(
  val name: String,
  val kind: String,
  val line: Int,
)

data class CompletionSuggestion(
  val label: String,
  val detail: String,
  val insertText: String,
  val cursorBackOffset: Int = 0,
)

object WranglerSyncParser {
  fun parse(path: String, content: String): Result<ParsedWranglerConfig> {
    return runCatching {
      if (path.endsWith(".toml", ignoreCase = true)) {
        parseToml(content)
      } else {
        parseJsonc(content)
      }
    }
  }

  fun stripJsoncCommentsAndTrailingCommas(raw: String): String {
    val noComments = StringBuilder(raw.length)
    var inString = false
    var escaped = false
    var i = 0
    while (i < raw.length) {
      val ch = raw[i]
      val next = raw.getOrNull(i + 1)
      if (inString) {
        noComments.append(ch)
        if (escaped) {
          escaped = false
        } else if (ch == '\\') {
          escaped = true
        } else if (ch == '"') {
          inString = false
        }
        i++
        continue
      }
      if (ch == '"') {
        inString = true
        noComments.append(ch)
        i++
        continue
      }
      if (ch == '/' && next == '/') {
        i += 2
        while (i < raw.length && raw[i] != '\n') i++
        continue
      }
      if (ch == '/' && next == '*') {
        i += 2
        while (i < raw.length - 1 && !(raw[i] == '*' && raw[i + 1] == '/')) i++
        i = (i + 2).coerceAtMost(raw.length)
        continue
      }
      noComments.append(ch)
      i++
    }

    // Remove trailing commas outside strings
    val cleaned = noComments.toString()
    val out = StringBuilder(cleaned.length)
    inString = false
    escaped = false
    var j = 0
    while (j < cleaned.length) {
      val ch = cleaned[j]
      if (inString) {
        out.append(ch)
        if (escaped) escaped = false
        else if (ch == '\\') escaped = true
        else if (ch == '"') inString = false
        j++
        continue
      }
      if (ch == '"') {
        inString = true
        out.append(ch)
        j++
        continue
      }
      if (ch == ',') {
        var lookahead = j + 1
        while (lookahead < cleaned.length && cleaned[lookahead].isWhitespace()) {
          lookahead++
        }
        val nextNonWs = cleaned.getOrNull(lookahead)
        if (nextNonWs == '}' || nextNonWs == ']') {
          j++
          continue
        }
      }
      out.append(ch)
      j++
    }
    return out.toString().trim()
  }

  private fun parseJsonc(content: String): ParsedWranglerConfig {
    val clean = stripJsoncCommentsAndTrailingCommas(content)
    val json = JSONObject(clean)
    val bindings = mutableListOf<CfWorkerBinding>()

    val flags = mutableListOf<String>()
    json.optJSONArray("compatibility_flags")?.let { arr ->
      for (i in 0 until arr.length()) {
        val f = arr.optString(i).trim()
        if (f.isNotEmpty()) flags.add(f)
      }
    }

    json.optJSONObject("vars")?.let { varsObj ->
      val keys = varsObj.keys()
      while (keys.hasNext()) {
        val k = keys.next()
        val v = varsObj.opt(k)?.toString().orEmpty()
        if (k.isNotBlank()) {
          bindings.add(CfWorkerBinding(name = k, type = "plain_text", text = v))
        }
      }
    }

    json.optJSONArray("kv_namespaces")?.let { arr ->
      for (i in 0 until arr.length()) {
        val obj = arr.optJSONObject(i) ?: continue
        val binding = obj.optString("binding").trim()
        val id = obj.optString("id").trim()
        if (binding.isNotEmpty()) {
          bindings.add(CfWorkerBinding(name = binding, type = "kv_namespace", namespaceId = id))
        }
      }
    }

    json.optJSONArray("d1_databases")?.let { arr ->
      for (i in 0 until arr.length()) {
        val obj = arr.optJSONObject(i) ?: continue
        val binding = obj.optString("binding").trim()
        val dbId = obj.optString("database_id").trim()
        if (binding.isNotEmpty()) {
          bindings.add(CfWorkerBinding(name = binding, type = "d1", databaseId = dbId))
        }
      }
    }

    json.optJSONArray("r2_buckets")?.let { arr ->
      for (i in 0 until arr.length()) {
        val obj = arr.optJSONObject(i) ?: continue
        val binding = obj.optString("binding").trim()
        val bucket = obj.optString("bucket_name").trim()
        if (binding.isNotEmpty()) {
          bindings.add(CfWorkerBinding(name = binding, type = "r2_bucket", bucketName = bucket))
        }
      }
    }

    json.optJSONArray("services")?.let { arr ->
      for (i in 0 until arr.length()) {
        val obj = arr.optJSONObject(i) ?: continue
        val binding = obj.optString("binding").trim()
        val service = obj.optString("service").trim()
        val env = obj.optString("environment").takeIf { it.isNotBlank() }
        if (binding.isNotEmpty() && service.isNotEmpty()) {
          bindings.add(CfWorkerBinding(name = binding, type = "service", service = service, environment = env))
        }
      }
    }

    json.optJSONObject("ai")?.let { aiObj ->
      val binding = aiObj.optString("binding").trim()
      if (binding.isNotEmpty()) {
        bindings.add(CfWorkerBinding(name = binding, type = "ai"))
      }
    }

    json.optJSONObject("durable_objects")?.optJSONArray("bindings")?.let { arr ->
      for (i in 0 until arr.length()) {
        val obj = arr.optJSONObject(i) ?: continue
        val name = obj.optString("name").trim()
        val cls = obj.optString("class_name").takeIf { it.isNotBlank() }
        val script = obj.optString("script_name").takeIf { it.isNotBlank() }
        if (name.isNotEmpty()) {
          bindings.add(
            CfWorkerBinding(
              name = name,
              type = "durable_object_namespace",
              className = cls,
              scriptName = script,
            ),
          )
        }
      }
    }

    json.optJSONObject("queues")?.optJSONArray("producers")?.let { arr ->
      for (i in 0 until arr.length()) {
        val obj = arr.optJSONObject(i) ?: continue
        val binding = obj.optString("binding").trim()
        val queue = obj.optString("queue").takeIf { it.isNotBlank() }
        if (binding.isNotEmpty()) {
          bindings.add(CfWorkerBinding(name = binding, type = "queue", queueName = queue))
        }
      }
    }

    val crons = mutableListOf<String>()
    json.optJSONObject("triggers")?.optJSONArray("crons")?.let { arr ->
      for (i in 0 until arr.length()) {
        val expr = arr.optString(i).trim()
        if (expr.isNotEmpty()) crons.add(expr)
      }
    }

    return ParsedWranglerConfig(
      scriptName = json.optString("name").takeIf { it.isNotBlank() },
      main = json.optString("main").takeIf { it.isNotBlank() }?.removePrefix("./"),
      compatibilityDate = json.optString("compatibility_date").takeIf { it.isNotBlank() },
      compatibilityFlags = flags,
      workersDev = if (json.has("workers_dev")) json.optBoolean("workers_dev") else null,
      bindings = bindings,
      crons = crons,
    )
  }

  private fun parseToml(content: String): ParsedWranglerConfig {
    var currentSection = ""
    var scriptName: String? = null
    var main: String? = null
    var compatDate: String? = null
    val compatFlags = mutableListOf<String>()
    var workersDev: Boolean? = null
    val bindings = mutableListOf<CfWorkerBinding>()
    val crons = mutableListOf<String>()

    var pendingType = ""
    val pendingProps = mutableMapOf<String, String>()

    fun flushPendingTable() {
      if (pendingProps.isEmpty()) return
      when (pendingType) {
        "kv_namespaces" -> {
          val b = pendingProps["binding"].orEmpty()
          val id = pendingProps["id"].orEmpty()
          if (b.isNotBlank()) bindings.add(CfWorkerBinding(name = b, type = "kv_namespace", namespaceId = id))
        }
        "d1_databases" -> {
          val b = pendingProps["binding"].orEmpty()
          val id = pendingProps["database_id"].orEmpty()
          if (b.isNotBlank()) bindings.add(CfWorkerBinding(name = b, type = "d1", databaseId = id))
        }
        "r2_buckets" -> {
          val b = pendingProps["binding"].orEmpty()
          val bucket = pendingProps["bucket_name"].orEmpty()
          if (b.isNotBlank()) bindings.add(CfWorkerBinding(name = b, type = "r2_bucket", bucketName = bucket))
        }
        "services" -> {
          val b = pendingProps["binding"].orEmpty()
          val svc = pendingProps["service"].orEmpty()
          val env = pendingProps["environment"]
          if (b.isNotBlank() && svc.isNotBlank()) {
            bindings.add(CfWorkerBinding(name = b, type = "service", service = svc, environment = env))
          }
        }
        "durable_objects.bindings" -> {
          val b = pendingProps["name"].orEmpty()
          val cls = pendingProps["class_name"]
          val scr = pendingProps["script_name"]
          if (b.isNotBlank()) {
            bindings.add(CfWorkerBinding(name = b, type = "durable_object_namespace", className = cls, scriptName = scr))
          }
        }
        "queues.producers" -> {
          val b = pendingProps["binding"].orEmpty()
          val q = pendingProps["queue"]
          if (b.isNotBlank()) bindings.add(CfWorkerBinding(name = b, type = "queue", queueName = q))
        }
      }
      pendingProps.clear()
      pendingType = ""
    }

    for (rawLine in content.lines()) {
      val line = rawLine.substringBefore('#').trim()
      if (line.isEmpty()) continue
      if (line.startsWith("[[") && line.endsWith("]]")) {
        flushPendingTable()
        pendingType = line.removePrefix("[[").removeSuffix("]]").trim()
        currentSection = pendingType
        continue
      }
      if (line.startsWith("[") && line.endsWith("]")) {
        flushPendingTable()
        currentSection = line.removePrefix("[").removeSuffix("]").trim()
        continue
      }
      val eq = line.indexOf('=')
      if (eq <= 0) continue
      val key = line.substring(0, eq).trim()
      val rawVal = line.substring(eq + 1).trim()
      val unquoted = rawVal.removeSurrounding("\"").removeSurrounding("'")

      if (pendingType.isNotEmpty()) {
        pendingProps[key] = unquoted
        continue
      }

      when (currentSection) {
        "" ->
          when (key) {
            "name" -> scriptName = unquoted
            "main" -> main = unquoted.removePrefix("./")
            "compatibility_date" -> compatDate = unquoted
            "workers_dev" -> workersDev = unquoted.toBooleanStrictOrNull()
            "compatibility_flags" -> {
              Regex(""""([^"]+)"""").findAll(rawVal).forEach { compatFlags.add(it.groupValues[1]) }
            }
          }
        "vars" -> bindings.add(CfWorkerBinding(name = key, type = "plain_text", text = unquoted))
        "ai" -> if (key == "binding" && unquoted.isNotBlank()) {
          bindings.add(CfWorkerBinding(name = unquoted, type = "ai"))
        }
        "triggers" -> if (key == "crons") {
          Regex(""""([^"]+)"""").findAll(rawVal).forEach { crons.add(it.groupValues[1]) }
        }
      }
    }
    flushPendingTable()

    return ParsedWranglerConfig(
      scriptName = scriptName,
      main = main,
      compatibilityDate = compatDate,
      compatibilityFlags = compatFlags,
      workersDev = workersDev,
      bindings = bindings,
      crons = crons,
    )
  }

  private val quotedStringRegex = Regex(""""([^"]+)"""")
  private val fnRegex = Regex("""^\s*(?:export\s+)?(?:async\s+)?function\s+([A-Za-z_$][A-Za-z0-9_$]*)\s*\(""")
  private val methodRegex = Regex("""^\s*(?:async\s+)?(fetch|scheduled|queue|tail|email|onRequest|onRequestGet|onRequestPost|onRequestPut|onRequestDelete)\s*\(""")
  private val classRegex = Regex("""^\s*(?:export\s+)?class\s+([A-Za-z_$][A-Za-z0-9_$]*)""")
  private val arrowRegex = Regex("""^\s*(?:export\s+)?const\s+([A-Za-z_$][A-Za-z0-9_$]*)\s*=\s*(?:async\s+)?(?:\([^)]*\)|[A-Za-z_$][A-Za-z0-9_$]*)\s*=>""")
  private val honoRouteRegex = Regex("""^\s*(?:app|router)\.(get|post|put|delete|patch|all)\s*\(\s*["']([^"']+)["']""")
  private val topKeyRegex = Regex("""^\s{2}"([^"]+)"\s*:""")

  fun extractCodeSymbols(code: String, language: WorkerLanguage): List<CodeSymbol> {
    if (code.length > 80_000) return emptyList()
    val symbols = mutableListOf<CodeSymbol>()
    val lines = code.lines()
    when (language) {
      WorkerLanguage.JAVASCRIPT, WorkerLanguage.TYPESCRIPT -> {
        lines.forEachIndexed { idx, line ->
          val lineNum = idx + 1
          when {
            line.trimStart().startsWith("export default") ->
              symbols.add(CodeSymbol("export default", "default", lineNum))
            else -> {
              val fn = fnRegex.find(line)
              if (fn != null) {
                symbols.add(CodeSymbol("${fn.groupValues[1]}()", "fn", lineNum))
              } else {
                val method = methodRegex.find(line)
                if (method != null) {
                  symbols.add(CodeSymbol("${method.groupValues[1]}()", "handler", lineNum))
                } else {
                  val cls = classRegex.find(line)
                  if (cls != null) {
                    symbols.add(CodeSymbol(cls.groupValues[1], "class", lineNum))
                  } else {
                    val arrow = arrowRegex.find(line)
                    if (arrow != null) {
                      symbols.add(CodeSymbol(arrow.groupValues[1], "const", lineNum))
                    } else {
                      val route = honoRouteRegex.find(line)
                      if (route != null) {
                        symbols.add(CodeSymbol("${route.groupValues[1].uppercase()} ${route.groupValues[2]}", "route", lineNum))
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
      WorkerLanguage.JSON -> {
        lines.forEachIndexed { idx, line ->
          topKeyRegex.find(line)?.let { m ->
            symbols.add(CodeSymbol(m.groupValues[1], "key", idx + 1))
          }
        }
      }
      WorkerLanguage.TEXT -> Unit
    }
    return symbols.take(60)
  }

  fun buildWorkersCompletions(
    workspace: WorkerWorkspace?,
    language: WorkerLanguage,
    currentWordPrefix: String,
  ): List<CompletionSuggestion> {
    val base = mutableListOf<CompletionSuggestion>()
    if (language == WorkerLanguage.JSON) {
      base.addAll(
        listOf(
          CompletionSuggestion("kv_namespaces", "Bind KV", "\"kv_namespaces\": [\n    { \"binding\": \"MY_KV\", \"id\": \"\" }\n  ],", 6),
          CompletionSuggestion("d1_databases", "Bind D1 SQL", "\"d1_databases\": [\n    { \"binding\": \"DB\", \"database_id\": \"\" }\n  ],", 6),
          CompletionSuggestion("r2_buckets", "Bind R2 Bucket", "\"r2_buckets\": [\n    { \"binding\": \"BUCKET\", \"bucket_name\": \"\" }\n  ],", 6),
          CompletionSuggestion("vars", "Env variables", "\"vars\": {\n    \"API_MODE\": \"production\"\n  },", 0),
          CompletionSuggestion("ai", "Workers AI", "\"ai\": {\n    \"binding\": \"AI\"\n  },", 0),
          CompletionSuggestion("triggers.crons", "Cron schedule", "\"triggers\": {\n    \"crons\": [\"*/15 * * * *\"]\n  },", 0),
          CompletionSuggestion("compatibility_flags", "Nodejs compat", "\"compatibility_flags\": [\"nodejs_compat\"],", 0),
        ),
      )
    } else {
      workspace?.bindings.orEmpty().forEach { b ->
        when (b.type) {
          "kv_namespace" ->
            base.add(
              CompletionSuggestion(
                label = "env.${b.name}.get()",
                detail = "KV read binding",
                insertText = "await env.${b.name}.get(\"\")",
                cursorBackOffset = 2,
              ),
            )
          "d1" ->
            base.add(
              CompletionSuggestion(
                label = "env.${b.name}.prepare()",
                detail = "D1 SQL query",
                insertText = "await env.${b.name}.prepare(\"SELECT 1\").all()",
                cursorBackOffset = 0,
              ),
            )
          "r2_bucket" ->
            base.add(
              CompletionSuggestion(
                label = "env.${b.name}.get()",
                detail = "R2 object fetch",
                insertText = "await env.${b.name}.get(\"\")",
                cursorBackOffset = 2,
              ),
            )
          "ai" ->
            base.add(
              CompletionSuggestion(
                label = "env.${b.name}.run()",
                detail = "Workers AI inference",
                insertText = "await env.${b.name}.run(\"@cf/meta/llama-3.1-8b-instruct\", { prompt: \"Hello\" })",
                cursorBackOffset = 0,
              ),
            )
          "service" ->
            base.add(
              CompletionSuggestion(
                label = "env.${b.name}.fetch()",
                detail = "Service RPC/fetch",
                insertText = "await env.${b.name}.fetch(request)",
                cursorBackOffset = 0,
              ),
            )
          else ->
            base.add(
              CompletionSuggestion(
                label = "env.${b.name}",
                detail = b.type,
                insertText = "env.${b.name}",
                cursorBackOffset = 0,
              ),
            )
        }
      }

      base.addAll(
        listOf(
          CompletionSuggestion("Response.json()", "JSON response", "Response.json({ ok: true })", 0),
          CompletionSuggestion("new URL()", "Parse URL & query", "const url = new URL(request.url);", 0),
          CompletionSuggestion("request.json()", "Parse JSON body", "const body = await request.json().catch(() => ({}));", 0),
          CompletionSuggestion("ctx.waitUntil()", "Background task", "ctx.waitUntil()", 1),
          CompletionSuggestion("onRequest", "Pages Function", "export async function onRequest(context) {\n  const { request, env, params } = context;\n  return Response.json({ ok: true, params });\n}\n", 0),
          CompletionSuggestion("fetch()", "Workers fetch", "const res = await fetch(\"\");", 3),
          CompletionSuggestion("caches.default", "Edge Cache API", "const cache = caches.default;", 0),
        ),
      )
    }

    val cleanPrefix = currentWordPrefix.trim()
    return if (cleanPrefix.isEmpty()) {
      base.take(10)
    } else {
      val matching = base.filter { it.label.contains(cleanPrefix, ignoreCase = true) }
      (matching + base.filterNot { it in matching }).take(10)
    }
  }
}
