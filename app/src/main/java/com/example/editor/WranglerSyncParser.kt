package com.example.editor

import com.example.data.remote.CfWorkerBinding
import com.example.data.remote.WorkerLanguage
import com.example.data.remote.WorkerWorkspace
import org.json.JSONArray
import org.json.JSONObject

data class ParsedWranglerEnv(
  val scriptName: String? = null,
  val main: String? = null,
  val compatibilityDate: String? = null,
  val compatibilityFlags: List<String> = emptyList(),
  val workersDev: Boolean? = null,
  val bindings: List<CfWorkerBinding> = emptyList(),
  val crons: List<String> = emptyList(),
  val hasCompatibilityFlags: Boolean = false,
  val hasCrons: Boolean = false,
)

data class ParsedWranglerConfig(
  val scriptName: String? = null,
  val main: String? = null,
  val compatibilityDate: String? = null,
  val compatibilityFlags: List<String> = emptyList(),
  val workersDev: Boolean? = null,
  val bindings: List<CfWorkerBinding> = emptyList(),
  val crons: List<String> = emptyList(),
  val environments: Map<String, ParsedWranglerEnv> = emptyMap(),
  val hasCompatibilityFlags: Boolean = false,
  val hasCrons: Boolean = false,
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
  fun parse(path: String, content: String, environment: String? = null): Result<ParsedWranglerConfig> {
    return runCatching {
      val base =
        if (path.endsWith(".toml", ignoreCase = true)) {
          parseToml(content)
        } else {
          parseJsonc(content)
        }
      applyEnvironmentOverride(base, environment)
    }
  }

  private fun applyEnvironmentOverride(
    base: ParsedWranglerConfig,
    environment: String?,
  ): ParsedWranglerConfig {
    val envKey = environment?.trim()?.takeIf { it.isNotEmpty() } ?: return base
    val envOverride = base.environments[envKey] ?: return base
    val mergedBindings = linkedMapOf<String, CfWorkerBinding>()
    base.bindings.forEach { mergedBindings[it.name] = it }
    envOverride.bindings.forEach { mergedBindings[it.name] = it }
    val resolvedFlags =
      if (envOverride.hasCompatibilityFlags) envOverride.compatibilityFlags
      else base.compatibilityFlags
    val resolvedCrons =
      if (envOverride.hasCrons) envOverride.crons
      else base.crons
    return base.copy(
      scriptName = envOverride.scriptName ?: base.scriptName,
      main = envOverride.main ?: base.main,
      compatibilityDate = envOverride.compatibilityDate ?: base.compatibilityDate,
      compatibilityFlags = resolvedFlags,
      workersDev = envOverride.workersDev ?: base.workersDev,
      bindings = mergedBindings.values.toList(),
      crons = resolvedCrons,
      hasCompatibilityFlags = base.hasCompatibilityFlags || envOverride.hasCompatibilityFlags,
      hasCrons = base.hasCrons || envOverride.hasCrons,
    )
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

  private fun extractJsoncSection(json: JSONObject): ParsedWranglerEnv {
    val bindings = mutableListOf<CfWorkerBinding>()
    val flags = mutableListOf<String>()
    val hasFlagsKey = json.has("compatibility_flags") && json.optJSONArray("compatibility_flags") != null
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
    val triggersObj = json.optJSONObject("triggers")
    val hasCronsKey = triggersObj != null && triggersObj.has("crons") && triggersObj.optJSONArray("crons") != null
    triggersObj?.optJSONArray("crons")?.let { arr ->
      for (i in 0 until arr.length()) {
        val expr = arr.optString(i).trim()
        if (expr.isNotEmpty()) crons.add(expr)
      }
    }

    return ParsedWranglerEnv(
      scriptName = json.optString("name").takeIf { it.isNotBlank() },
      main = json.optString("main").takeIf { it.isNotBlank() }?.removePrefix("./"),
      compatibilityDate = json.optString("compatibility_date").takeIf { it.isNotBlank() },
      compatibilityFlags = flags,
      workersDev = if (json.has("workers_dev")) json.optBoolean("workers_dev") else null,
      bindings = bindings,
      crons = crons,
      hasCompatibilityFlags = hasFlagsKey,
      hasCrons = hasCronsKey,
    )
  }

  private fun parseJsonc(content: String): ParsedWranglerConfig {
    val clean = stripJsoncCommentsAndTrailingCommas(content)
    val json = JSONObject(clean)
    val root = extractJsoncSection(json)
    val environments = linkedMapOf<String, ParsedWranglerEnv>()
    json.optJSONObject("env")?.let { envObj ->
      val keys = envObj.keys()
      while (keys.hasNext()) {
        val envName = keys.next().trim()
        val sub = envObj.optJSONObject(envName) ?: continue
        if (envName.isNotEmpty()) {
          environments[envName] = extractJsoncSection(sub)
        }
      }
    }
    return ParsedWranglerConfig(
      scriptName = root.scriptName,
      main = root.main,
      compatibilityDate = root.compatibilityDate,
      compatibilityFlags = root.compatibilityFlags,
      workersDev = root.workersDev,
      bindings = root.bindings,
      crons = root.crons,
      environments = environments,
      hasCompatibilityFlags = root.hasCompatibilityFlags,
      hasCrons = root.hasCrons,
    )
  }

  private class MutableTomlScope {
    var scriptName: String? = null
    var main: String? = null
    var compatDate: String? = null
    val compatFlags = mutableListOf<String>()
    var hasCompatFlags: Boolean = false
    var workersDev: Boolean? = null
    val bindings = mutableListOf<CfWorkerBinding>()
    val crons = mutableListOf<String>()
    var hasCrons: Boolean = false

    fun toEnv() =
      ParsedWranglerEnv(
        scriptName = scriptName,
        main = main,
        compatibilityDate = compatDate,
        compatibilityFlags = compatFlags.toList(),
        workersDev = workersDev,
        bindings = bindings.toList(),
        crons = crons.toList(),
        hasCompatibilityFlags = hasCompatFlags,
        hasCrons = hasCrons,
      )
  }

  private fun splitEnvSection(section: String): Pair<String?, String> {
    if (!section.startsWith("env.")) return null to section
    val rest = section.removePrefix("env.")
    val dot = rest.indexOf('.')
    return if (dot < 0) {
      rest.trim().takeIf { it.isNotEmpty() } to ""
    } else {
      rest.substring(0, dot).trim().takeIf { it.isNotEmpty() } to rest.substring(dot + 1).trim()
    }
  }

  private fun stripTomlCommentOutsideQuotes(rawLine: String): String {
    var inDouble = false
    var inSingle = false
    var i = 0
    while (i < rawLine.length) {
      val ch = rawLine[i]
      if (inDouble) {
        if (ch == '\\' && i + 1 < rawLine.length) {
          i += 2
          continue
        }
        if (ch == '"') inDouble = false
      } else if (inSingle) {
        if (ch == '\'') inSingle = false
      } else {
        when (ch) {
          '"' -> inDouble = true
          '\'' -> inSingle = true
          '#' -> return rawLine.substring(0, i).trim()
        }
      }
      i++
    }
    return rawLine.trim()
  }

  private fun bracketBalanceDelta(text: String): Int {
    var delta = 0
    var inDouble = false
    var inSingle = false
    var i = 0
    while (i < text.length) {
      val ch = text[i]
      if (inDouble) {
        if (ch == '\\' && i + 1 < text.length) {
          i += 2
          continue
        }
        if (ch == '"') inDouble = false
      } else if (inSingle) {
        if (ch == '\'') inSingle = false
      } else {
        when (ch) {
          '"' -> inDouble = true
          '\'' -> inSingle = true
          '[' -> delta++
          ']' -> delta--
        }
      }
      i++
    }
    return delta
  }

  private fun extractTomlArrayStrings(rawVal: String): List<String> {
    val result = mutableListOf<String>()
    var i = 0
    while (i < rawVal.length) {
      val ch = rawVal[i]
      if (ch == '"' || ch == '\'') {
        val quote = ch
        val sb = StringBuilder()
        i++
        while (i < rawVal.length) {
          val c = rawVal[i]
          if (quote == '"' && c == '\\' && i + 1 < rawVal.length) {
            sb.append(rawVal[i + 1])
            i += 2
            continue
          }
          if (c == quote) {
            i++
            break
          }
          sb.append(c)
          i++
        }
        val value = sb.toString()
        if (value.isNotEmpty()) result.add(value)
      } else {
        i++
      }
    }
    return result
  }

  private fun unquoteTomlScalar(rawVal: String): String {
    val trimmed = rawVal.trim()
    if (trimmed.length >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
      val inner = trimmed.substring(1, trimmed.length - 1)
      return inner
        .replace("\\\"", "\"")
        .replace("\\n", "\n")
        .replace("\\t", "\t")
        .replace("\\\\", "\\")
    }
    if (trimmed.length >= 2 && trimmed.startsWith("'") && trimmed.endsWith("'")) {
      return trimmed.substring(1, trimmed.length - 1)
    }
    return trimmed
  }

  private fun parseToml(content: String): ParsedWranglerConfig {
    var currentSection = ""
    val root = MutableTomlScope()
    val envScopes = linkedMapOf<String, MutableTomlScope>()
    fun scopeFor(envName: String?): MutableTomlScope =
      if (envName == null) root else envScopes.getOrPut(envName) { MutableTomlScope() }

    var pendingEnv: String? = null
    var pendingType = ""
    val pendingProps = mutableMapOf<String, String>()

    fun flushPendingTable() {
      if (pendingProps.isEmpty()) return
      val target = scopeFor(pendingEnv)
      when (pendingType) {
        "kv_namespaces" -> {
          val b = pendingProps["binding"].orEmpty()
          val id = pendingProps["id"].orEmpty()
          if (b.isNotBlank()) target.bindings.add(CfWorkerBinding(name = b, type = "kv_namespace", namespaceId = id))
        }
        "d1_databases" -> {
          val b = pendingProps["binding"].orEmpty()
          val id = pendingProps["database_id"].orEmpty()
          if (b.isNotBlank()) target.bindings.add(CfWorkerBinding(name = b, type = "d1", databaseId = id))
        }
        "r2_buckets" -> {
          val b = pendingProps["binding"].orEmpty()
          val bucket = pendingProps["bucket_name"].orEmpty()
          if (b.isNotBlank()) target.bindings.add(CfWorkerBinding(name = b, type = "r2_bucket", bucketName = bucket))
        }
        "services" -> {
          val b = pendingProps["binding"].orEmpty()
          val svc = pendingProps["service"].orEmpty()
          val env = pendingProps["environment"]
          if (b.isNotBlank() && svc.isNotBlank()) {
            target.bindings.add(CfWorkerBinding(name = b, type = "service", service = svc, environment = env))
          }
        }
        "durable_objects.bindings" -> {
          val b = pendingProps["name"].orEmpty()
          val cls = pendingProps["class_name"]
          val scr = pendingProps["script_name"]
          if (b.isNotBlank()) {
            target.bindings.add(CfWorkerBinding(name = b, type = "durable_object_namespace", className = cls, scriptName = scr))
          }
        }
        "queues.producers" -> {
          val b = pendingProps["binding"].orEmpty()
          val q = pendingProps["queue"]
          if (b.isNotBlank()) target.bindings.add(CfWorkerBinding(name = b, type = "queue", queueName = q))
        }
      }
      pendingProps.clear()
      pendingType = ""
      pendingEnv = null
    }

    var accumKey: String? = null
    val accumVal = StringBuilder()
    var accumDepth = 0

    fun applyKeyValue(key: String, rawVal: String) {
      val unquoted = unquoteTomlScalar(rawVal)
      if (pendingType.isNotEmpty()) {
        pendingProps[key] = unquoted
        return
      }
      val (envName, subSection) = splitEnvSection(currentSection)
      val target = scopeFor(envName)
      when (subSection) {
        "" ->
          when (key) {
            "name" -> target.scriptName = unquoted
            "main" -> target.main = unquoted.removePrefix("./")
            "compatibility_date" -> target.compatDate = unquoted
            "workers_dev" -> target.workersDev = unquoted.toBooleanStrictOrNull()
            "compatibility_flags" -> {
              target.hasCompatFlags = true
              target.compatFlags.clear()
              target.compatFlags.addAll(extractTomlArrayStrings(rawVal))
            }
          }
        "vars" -> target.bindings.add(CfWorkerBinding(name = key, type = "plain_text", text = unquoted))
        "ai" -> if (key == "binding" && unquoted.isNotBlank()) {
          target.bindings.add(CfWorkerBinding(name = unquoted, type = "ai"))
        }
        "triggers" -> if (key == "crons") {
          target.hasCrons = true
          target.crons.clear()
          target.crons.addAll(extractTomlArrayStrings(rawVal))
        }
      }
    }

    for (rawLine in content.lines()) {
      val line = stripTomlCommentOutsideQuotes(rawLine)
      if (line.isEmpty()) continue
      if (accumKey != null) {
        accumVal.append(' ').append(line)
        accumDepth += bracketBalanceDelta(line)
        if (accumDepth <= 0) {
          val k = accumKey
          val v = accumVal.toString().trim()
          accumKey = null
          accumVal.clear()
          accumDepth = 0
          applyKeyValue(k, v)
        }
        continue
      }
      if (line.startsWith("[[") && line.endsWith("]]")) {
        flushPendingTable()
        val rawSec = line.removePrefix("[[").removeSuffix("]]").trim()
        val (envName, subSec) = splitEnvSection(rawSec)
        pendingEnv = envName
        pendingType = subSec
        currentSection = rawSec
        continue
      }
      if (line.startsWith("[") && line.endsWith("]")) {
        flushPendingTable()
        currentSection = line.removePrefix("[").removeSuffix("]").trim()
        continue
      }
      val eq = line.indexOf('=')
      if (eq <= 0) continue
      val key = unquoteTomlScalar(line.substring(0, eq).trim())
      val rawVal = line.substring(eq + 1).trim()
      val depth = bracketBalanceDelta(rawVal)
      if (depth > 0) {
        accumKey = key
        accumVal.clear()
        accumVal.append(rawVal)
        accumDepth = depth
        continue
      }
      applyKeyValue(key, rawVal)
    }
    if (accumKey != null) {
      applyKeyValue(accumKey, accumVal.toString().trim())
    }
    flushPendingTable()

    return ParsedWranglerConfig(
      scriptName = root.scriptName,
      main = root.main,
      compatibilityDate = root.compatDate,
      compatibilityFlags = root.compatFlags,
      workersDev = root.workersDev,
      bindings = root.bindings,
      crons = root.crons,
      environments = envScopes.mapValues { it.value.toEnv() },
      hasCompatibilityFlags = root.hasCompatFlags,
      hasCrons = root.hasCrons,
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
      WorkerLanguage.MARKDOWN -> {
        val headingRx = Regex("""^\s*(#{1,4})\s+(.+?)\s*$""")
        lines.forEachIndexed { idx, line ->
          headingRx.find(line)?.let { m ->
            symbols.add(CodeSymbol("${m.groupValues[1]} ${m.groupValues[2]}", "h${m.groupValues[1].length}", idx + 1))
          }
        }
      }
      WorkerLanguage.HTML -> {
        val idRx = Regex("""<([a-zA-Z][a-zA-Z0-9-]*)\b[^>]*\bid=["']([^"']+)["']""")
        val sectionRx = Regex("""^\s*<(head|body|header|nav|main|section|article|footer|script|style|h[1-3])\b""", RegexOption.IGNORE_CASE)
        lines.forEachIndexed { idx, line ->
          val idMatch = idRx.find(line)
          if (idMatch != null) {
            symbols.add(CodeSymbol("<${idMatch.groupValues[1]}#${idMatch.groupValues[2]}>", "id", idx + 1))
          } else {
            sectionRx.find(line)?.let { m ->
              symbols.add(CodeSymbol("<${m.groupValues[1].lowercase()}>", "tag", idx + 1))
            }
          }
        }
      }
      WorkerLanguage.CSS -> {
        val ruleRx = Regex("""^\s*([.@#:*a-zA-Z\[][^{/;]{1,48})\s*\{""")
        lines.forEachIndexed { idx, line ->
          ruleRx.find(line)?.let { m ->
            symbols.add(CodeSymbol(m.groupValues[1].trim(), "rule", idx + 1))
          }
        }
      }
      WorkerLanguage.CONFIG -> {
        val secRx = Regex("""^\s*\[([^\]]+)]""")
        val kvRx = Regex("""^\s*([A-Za-z_][A-Za-z0-9_.-]*)\s*[:=]""")
        lines.forEachIndexed { idx, line ->
          val sec = secRx.find(line)
          if (sec != null) {
            symbols.add(CodeSymbol("[${sec.groupValues[1]}]", "section", idx + 1))
          } else {
            kvRx.find(line)?.let { kv ->
              symbols.add(CodeSymbol(kv.groupValues[1], "key", idx + 1))
            }
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
