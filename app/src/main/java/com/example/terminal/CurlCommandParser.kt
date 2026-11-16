package com.example.terminal

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

data class CurlSpec(
  val url: String,
  val method: String,
  val headers: Map<String, String>,
  val body: String?,
  val includeHeaders: Boolean,
  val headOnly: Boolean,
  val verbose: Boolean,
  val followRedirects: Boolean,
)

object ShellTokenizer {
  /**
   * Normalizes multi-line backslash continuations (common when pasting cURL commands)
   * and splits into segments separated by unquoted pipes `|` or command separators `&&` / `;`.
   */
  fun normalizeMultilineCommand(raw: String): String {
    return raw
      .replace("\r\n", "\n")
      .replace(Regex("\\\\\\s*\n\\s*"), " ")
      .trim()
  }

  fun tokenize(command: String): List<String> {
    val tokens = mutableListOf<String>()
    val current = StringBuilder()
    var inSingleQuote = false
    var inDoubleQuote = false
    var escaped = false
    var hasToken = false

    for (ch in command) {
      when {
        escaped -> {
          current.append(ch)
          escaped = false
          hasToken = true
        }
        ch == '\\' && !inSingleQuote -> {
          escaped = true
          hasToken = true
        }
        ch == '\'' && !inDoubleQuote -> {
          inSingleQuote = !inSingleQuote
          hasToken = true
        }
        ch == '"' && !inSingleQuote -> {
          inDoubleQuote = !inDoubleQuote
          hasToken = true
        }
        ch.isWhitespace() && !inSingleQuote && !inDoubleQuote -> {
          if (hasToken) {
            tokens.add(current.toString())
            current.clear()
            hasToken = false
          }
        }
        else -> {
          current.append(ch)
          hasToken = true
        }
      }
    }
    if (hasToken) {
      tokens.add(current.toString())
    }
    return tokens
  }

  fun splitUnquotedPipe(command: String): List<String> {
    val parts = mutableListOf<String>()
    val current = StringBuilder()
    var inSingle = false
    var inDouble = false
    var escaped = false

    for (i in command.indices) {
      val ch = command[i]
      if (escaped) {
        current.append(ch)
        escaped = false
        continue
      }
      if (ch == '\\' && !inSingle) {
        escaped = true
        current.append(ch)
        continue
      }
      if (ch == '\'' && !inDouble) {
        inSingle = !inSingle
        current.append(ch)
        continue
      }
      if (ch == '"' && !inSingle) {
        inDouble = !inDouble
        current.append(ch)
        continue
      }
      if (ch == '|' && !inSingle && !inDouble) {
        val next = command.getOrNull(i + 1)
        val prev = command.getOrNull(i - 1)
        if (next != '|' && prev != '|') {
          parts.add(current.toString().trim())
          current.clear()
          continue
        }
      }
      current.append(ch)
    }
    val tail = current.toString().trim()
    if (tail.isNotEmpty()) parts.add(tail)
    return parts
  }

  fun expandEnvVars(input: String, env: Map<String, String>): String {
    val braced = Regex("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}")
    val simple = Regex("\\$([A-Za-z_][A-Za-z0-9_]*)")
    var result = braced.replace(input) { m -> env[m.groupValues[1]] ?: "" }
    result = simple.replace(result) { m -> env[m.groupValues[1]] ?: "" }
    return result
  }
}

object CurlCommandParser {
  private val httpVerbs = setOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")

  fun isHttpTestCommand(tokens: List<String>): Boolean {
    if (tokens.isEmpty()) return false
    val cmd = tokens[0]
    if (cmd.equals("curl", ignoreCase = true) || cmd.equals("http", ignoreCase = true) || cmd.equals("fetch", ignoreCase = true)) {
      return true
    }
    return cmd.uppercase() in httpVerbs && tokens.size >= 2
  }

  fun parse(tokens: List<String>, defaultBaseUrl: String?): Result<CurlSpec> {
    if (tokens.isEmpty()) return Result.failure(IllegalArgumentException("Empty HTTP command"))

    val cmd = tokens[0]
    var method: String? = null
    val headers = linkedMapOf<String, String>()
    val dataParts = mutableListOf<String>()
    var urlToken: String? = null
    var includeHeaders = false
    var headOnly = false
    var verbose = false
    var followRedirects = true

    var startIndex = 1
    if (cmd.uppercase() in httpVerbs) {
      method = cmd.uppercase()
      if (method == "HEAD") headOnly = true
    } else if ((cmd.equals("http", ignoreCase = true) || cmd.equals("fetch", ignoreCase = true)) && tokens.size >= 2) {
      val second = tokens[1].uppercase()
      if (second in httpVerbs) {
        method = second
        if (method == "HEAD") headOnly = true
        startIndex = 2
      }
    }

    var i = startIndex
    while (i < tokens.size) {
      val tok = tokens[i]
      when {
        tok == "-X" || tok == "--request" -> {
          val next = tokens.getOrNull(i + 1) ?: return Result.failure(IllegalArgumentException("Missing method after $tok"))
          method = next.uppercase()
          i += 2
        }
        tok.startsWith("-X") && tok.length > 2 -> {
          method = tok.substring(2).uppercase()
          i++
        }
        tok == "-H" || tok == "--header" -> {
          val next = tokens.getOrNull(i + 1) ?: return Result.failure(IllegalArgumentException("Missing header after $tok"))
          val colon = next.indexOf(':')
          if (colon > 0) {
            headers[next.substring(0, colon).trim()] = next.substring(colon + 1).trim()
          }
          i += 2
        }
        tok == "-d" || tok == "--data" || tok == "--data-raw" || tok == "--data-binary" || tok == "--data-urlencode" -> {
          val next = tokens.getOrNull(i + 1) ?: return Result.failure(IllegalArgumentException("Missing payload after $tok"))
          dataParts.add(next)
          i += 2
        }
        tok == "--json" -> {
          val next = tokens.getOrNull(i + 1) ?: return Result.failure(IllegalArgumentException("Missing JSON body after --json"))
          dataParts.add(next)
          headers.putIfAbsent("Content-Type", "application/json")
          headers.putIfAbsent("Accept", "application/json")
          i += 2
        }
        tok == "-u" || tok == "--user" -> {
          val creds = tokens.getOrNull(i + 1) ?: return Result.failure(IllegalArgumentException("Missing user:pass after $tok"))
          val encoded = Base64.encodeToString(creds.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
          headers["Authorization"] = "Basic $encoded"
          i += 2
        }
        tok == "-A" || tok == "--user-agent" -> {
          val ua = tokens.getOrNull(i + 1) ?: return Result.failure(IllegalArgumentException("Missing User-Agent after $tok"))
          headers["User-Agent"] = ua
          i += 2
        }
        tok == "-b" || tok == "--cookie" -> {
          val c = tokens.getOrNull(i + 1) ?: return Result.failure(IllegalArgumentException("Missing Cookie after $tok"))
          headers["Cookie"] = c
          i += 2
        }
        tok == "--url" -> {
          urlToken = tokens.getOrNull(i + 1)
          i += 2
        }
        tok.startsWith("-") && !tok.startsWith("--") -> {
          // Handle combined short flags like -sSLiv or -XPOST
          for (flag in tok.drop(1)) {
            when (flag) {
              'i' -> includeHeaders = true
              'I' -> {
                headOnly = true
                includeHeaders = true
                method = "HEAD"
              }
              'v' -> {
                verbose = true
                includeHeaders = true
              }
              'L' -> followRedirects = true
              's', 'S', 'f', 'k', 'g' -> Unit
            }
          }
          i++
        }
        tok == "--include" -> {
          includeHeaders = true
          i++
        }
        tok == "--head" -> {
          headOnly = true
          includeHeaders = true
          method = "HEAD"
          i++
        }
        tok == "--verbose" -> {
          verbose = true
          includeHeaders = true
          i++
        }
        tok == "--location" -> {
          followRedirects = true
          i++
        }
        tok == "--silent" || tok == "--compressed" || tok == "--insecure" || tok == "--show-error" -> {
          i++
        }
        !tok.startsWith("-") && urlToken == null -> {
          urlToken = tok
          i++
        }
        else -> i++
      }
    }

    val rawUrl = urlToken ?: "/"
    val resolvedUrl =
      when {
        rawUrl.startsWith("http://", ignoreCase = true) || rawUrl.startsWith("https://", ignoreCase = true) -> rawUrl
        !defaultBaseUrl.isNullOrBlank() -> {
          val base = defaultBaseUrl.trimEnd('/')
          val suffix = if (rawUrl.startsWith("/")) rawUrl else "/$rawUrl"
          "$base$suffix"
        }
        else -> {
          return Result.failure(
            IllegalArgumentException(
              "Relative path '$rawUrl' requires an active Worker workers.dev URL, or pass a full https:// URL.",
            ),
          )
        }
      }

    val body = if (dataParts.isEmpty()) null else dataParts.joinToString("&")
    val resolvedMethod = method ?: if (body != null) "POST" else "GET"

    return Result.success(
      CurlSpec(
        url = resolvedUrl,
        method = resolvedMethod,
        headers = headers,
        body = body,
        includeHeaders = includeHeaders,
        headOnly = headOnly,
        verbose = verbose,
        followRedirects = followRedirects,
      ),
    )
  }

  fun toReproducibleCurl(spec: CurlSpec): String {
    val parts = mutableListOf("curl", "-i", "-X ${spec.method}")
    spec.headers.forEach { (k, v) ->
      parts.add("-H \"${k.replace("\"", "\\\"")}: ${v.replace("\"", "\\\"")}\"")
    }
    if (spec.body != null) {
      parts.add("-d '${spec.body.replace("'", "\\'")}'")
    }
    parts.add("\"${spec.url}\"")
    return parts.joinToString(" ")
  }
}

object JqEvaluator {
  fun prettyPrintIfJson(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return raw
    return runCatching {
      val parsed = JSONTokener(trimmed).nextValue()
      when (parsed) {
        is JSONObject -> parsed.toString(2)
        is JSONArray -> parsed.toString(2)
        else -> raw
      }
    }.getOrDefault(raw)
  }

  fun evaluate(rawInput: String, expressionRaw: String): Result<String> {
    val expr = expressionRaw.trim().ifEmpty { "." }
    val trimmed = rawInput.trim()
    if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("jq: empty input"))

    return runCatching {
      var current: Any? = JSONTokener(trimmed).nextValue()

      if (expr == "keys") {
        return@runCatching when (current) {
          is JSONObject -> JSONArray(current.keys().asSequence().toList().sorted()).toString(2)
          is JSONArray -> JSONArray((0 until current.length()).toList()).toString(2)
          else -> "[]"
        }
      }
      if (expr == "length") {
        return@runCatching when (current) {
          is JSONObject -> current.length().toString()
          is JSONArray -> current.length().toString()
          is String -> current.length.toString()
          else -> "1"
        }
      }

      if (expr != ".") {
        val cleanPath = expr.removePrefix(".")
        val segments =
          cleanPath
            .replace("[", ".[")
            .split('.')
            .filter { it.isNotBlank() }

        for (seg in segments) {
          current =
            if (seg.startsWith("[") && seg.endsWith("]")) {
              val idx = seg.substring(1, seg.length - 1).toIntOrNull()
              if (current is JSONArray && idx != null && idx in 0 until current.length()) {
                current.get(idx)
              } else {
                JSONObject.NULL
              }
            } else if (current is JSONObject && current.has(seg)) {
              current.get(seg)
            } else {
              JSONObject.NULL
            }
        }
      }

      when (current) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> current.toString(2)
        is JSONArray -> current.toString(2)
        else -> current.toString()
      }
    }
  }

  /**
   * Extracts flat key -> value pairs for easy 1-tap mobile copying from JSON responses.
   */
  fun extractCopyableFields(raw: String, maxFields: Int = 24): List<Pair<String, String>> {
    val trimmed = raw.trim()
    if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return emptyList()
    val fields = mutableListOf<Pair<String, String>>()
    runCatching {
      val root = JSONTokener(trimmed).nextValue()
      fun walk(prefix: String, node: Any?) {
        if (fields.size >= maxFields) return
        when (node) {
          is JSONObject -> {
            val keys = node.keys()
            while (keys.hasNext() && fields.size < maxFields) {
              val k = keys.next()
              val nextPrefix = if (prefix.isEmpty()) k else "$prefix.$k"
              walk(nextPrefix, node.opt(k))
            }
          }
          is JSONArray -> {
            for (i in 0 until minOf(node.length(), 8)) {
              if (fields.size >= maxFields) break
              walk("$prefix[$i]", node.opt(i))
            }
          }
          null, JSONObject.NULL -> Unit
          else -> {
            val str = node.toString()
            if (str.isNotEmpty()) {
              fields.add(prefix to str)
            }
          }
        }
      }
      walk("", root)
    }
    return fields
  }
}
