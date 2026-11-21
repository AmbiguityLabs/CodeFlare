package com.example.idecore.snippets

import com.example.idecore.completion.EdgeMacroResolver

data class CodeSnippetItem(
  val id: String,
  val abbrev: String,
  val title: String,
  val tag: String,
  val code: String,
  val description: String = "",
  val isCustom: Boolean = false,
)

data class ExpandedSnippet(
  val text: String,
  val cursorBackOffset: Int,
)

object CodeSnippetRegistry {
  private const val RECORD_SEP = '\u001E'
  private const val FIELD_SEP = '\u001F'

  val BUILTIN_SNIPPETS: List<CodeSnippetItem> =
    listOf(
      CodeSnippetItem(
        id = "builtin_fetch",
        abbrev = "fetch",
        title = "Worker Fetch Handler",
        tag = "WORKER",
        description = "Standard Cloudflare Worker fetch entrypoint",
        code =
          """export default {
  async fetch(request, env, ctx) {
    ${"$"}END${"$"}
    return Response.json({ ok: true });
  },
};""",
      ),
      CodeSnippetItem(
        id = "builtin_rjson",
        abbrev = "rjson",
        title = "JSON Response",
        tag = "HTTP",
        description = "Return formatted Response.json with status 200",
        code = """return Response.json({ ${"$"}END${"$"} }, { status: 200 });""",
      ),
      CodeSnippetItem(
        id = "builtin_cors",
        abbrev = "cors",
        title = "CORS Preflight & Headers",
        tag = "CORS",
        description = "Handle OPTIONS preflight and permissive CORS headers",
        code =
          """const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, Authorization",
};
if (request.method === "OPTIONS") {
  return new Response(null, { status: 204, headers: corsHeaders });
}${"$"}END${"$"}""",
      ),
      CodeSnippetItem(
        id = "builtin_kvget",
        abbrev = "kvget",
        title = "KV Read & Cache",
        tag = "KV",
        description = "Read JSON key from Workers KV binding with fallback put",
        code =
          """const cached = await env.MY_KV.get("${"$"}END${"$"}", { type: "json" });
if (!cached) {
  await env.MY_KV.put("session:key", JSON.stringify({ ok: true }), { expirationTtl: 300 });
}""",
      ),
      CodeSnippetItem(
        id = "builtin_kvput",
        abbrev = "kvput",
        title = "KV Write JSON",
        tag = "KV",
        description = "Write JSON payload with TTL into Workers KV",
        code = """await env.MY_KV.put("${"$"}END${"$"}", JSON.stringify(payload), { expirationTtl: 3600 });""",
      ),
      CodeSnippetItem(
        id = "builtin_d1q",
        abbrev = "d1q",
        title = "D1 Parameterized Query",
        tag = "D1",
        description = "Prepare and execute bound D1 SQL statement",
        code =
          """const { results } = await env.DB.prepare("SELECT * FROM items WHERE id = ?")
  .bind(${"$"}END${"$"})
  .all();
return Response.json({ results });""",
      ),
      CodeSnippetItem(
        id = "builtin_r2get",
        abbrev = "r2get",
        title = "R2 Bucket Read",
        tag = "R2",
        description = "Stream an object from R2 bucket with 404 guard",
        code =
          """const object = await env.BUCKET.get("${"$"}END${"$"}");
if (!object) return new Response("Not Found", { status: 404 });
return new Response(object.body, { headers: { "etag": object.httpEtag } });""",
      ),
      CodeSnippetItem(
        id = "builtin_cron",
        abbrev = "cron",
        title = "Scheduled Cron Handler",
        tag = "CRON",
        description = "Workers scheduled trigger handler",
        code =
          """async scheduled(controller, env, ctx) {
  ctx.waitUntil((async () => {
    ${"$"}END${"$"}
    console.log("Cron fired at", new Date(controller.scheduledTime).toISOString());
  })());
},""",
      ),
      CodeSnippetItem(
        id = "builtin_ws",
        abbrev = "cfws",
        title = "WebSocket Upgrade Pair",
        tag = "WS",
        description = "Edge WebSocketPair upgrade response",
        code =
          """if (request.headers.get("Upgrade") !== "websocket") {
  return new Response("Expected WebSocket", { status: 426 });
}
const pair = new WebSocketPair();
const [client, server] = Object.values(pair);
server.accept();
server.addEventListener("message", (event) => {
  ${"$"}END${"$"}
  server.send(event.data);
});
return new Response(null, { status: 101, webSocket: client });""",
      ),
      CodeSnippetItem(
        id = "builtin_tryc",
        abbrev = "tryc",
        title = "Try / Catch JSON Error",
        tag = "TS",
        description = "Wrap async handler block with JSON 500 error boundary",
        code =
          """try {
  ${"$"}END${"$"}
} catch (err) {
  return Response.json({ error: String(err) }, { status: 500 });
}""",
      ),
      CodeSnippetItem(
        id = "builtin_ts_env",
        abbrev = "tsenv",
        title = "TypeScript Env Interface",
        tag = "TS",
        description = "Typed Cloudflare Worker Env bindings interface",
        code =
          """export interface Env {
  DB: D1Database;
  KV: KVNamespace;
  BUCKET: R2Bucket;
  ${"$"}END${"$"}
}""",
      ),
      CodeSnippetItem(
        id = "builtin_html5",
        abbrev = "html5",
        title = "HTML5 Responsive Starter",
        tag = "HTML",
        description = "Minimal mobile-ready HTML5 shell",
        code =
          """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>CodeFlare App</title>
</head>
<body>
  <main>${"$"}END${"$"}</main>
</body>
</html>""",
      ),
      CodeSnippetItem(
        id = "builtin_css_grid",
        abbrev = "cssgrid",
        title = "Responsive CSS Grid",
        tag = "CSS",
        description = "Auto-fit card grid with gap",
        code =
          """.grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 1rem;
  ${"$"}END${"$"}
}""",
      ),
      CodeSnippetItem(
        id = "builtin_uuid",
        abbrev = "uuid",
        title = "UUID / TODO Stamp",
        tag = "UTIL",
        description = "Timestamped console log with file & function name",
        code = """console.log("[${"$"}FILE${"$"}:${"$"}FUNCTION_NAME${"$"}]", ${"$"}END${"$"});""",
      ),
    )

  fun expandSnippet(
    rawCode: String,
    fileName: String = "worker.ts",
    documentText: CharSequence = "",
    offset: Int = 0,
  ): ExpandedSnippet {
    val normalizedTemplate = rawCode.replace("\$0", "\$END\$")
    val (expanded, cursorBack) =
      EdgeMacroResolver.expand(
        template = normalizedTemplate,
        fileName = fileName,
        documentText = documentText,
        offset = offset,
      )
    return ExpandedSnippet(text = expanded, cursorBackOffset = cursorBack)
  }

  fun allSnippets(customSnippets: List<CodeSnippetItem>): List<CodeSnippetItem> {
    if (customSnippets.isEmpty()) return BUILTIN_SNIPPETS
    return customSnippets + BUILTIN_SNIPPETS
  }

  fun filterSnippets(
    items: List<CodeSnippetItem>,
    query: String,
    tagFilter: String? = null,
  ): List<CodeSnippetItem> {
    val cleanQuery = query.trim().lowercase()
    val cleanTag = tagFilter?.trim()?.takeIf { it.isNotEmpty() && !it.equals("ALL", ignoreCase = true) }
    return items.filter { item ->
      val tagMatches = cleanTag == null || item.tag.equals(cleanTag, ignoreCase = true)
      val queryMatches =
        cleanQuery.isEmpty() ||
          item.abbrev.lowercase().contains(cleanQuery) ||
          item.title.lowercase().contains(cleanQuery) ||
          item.tag.lowercase().contains(cleanQuery) ||
          item.description.lowercase().contains(cleanQuery)
      tagMatches && queryMatches
    }
  }

  fun encodeCustomSnippets(items: List<CodeSnippetItem>): String {
    if (items.isEmpty()) return ""
    return items.joinToString(RECORD_SEP.toString()) { item ->
      listOf(
        escapeField(item.id),
        escapeField(item.abbrev),
        escapeField(item.title),
        escapeField(item.tag),
        escapeField(item.code),
        escapeField(item.description),
      ).joinToString(FIELD_SEP.toString())
    }
  }

  fun decodeCustomSnippets(raw: String?): List<CodeSnippetItem> {
    val input = raw?.trim().orEmpty()
    if (input.isEmpty()) return emptyList()
    return input.split(RECORD_SEP).mapNotNull { record ->
      if (record.isBlank()) return@mapNotNull null
      val parts = record.split(FIELD_SEP)
      if (parts.size < 5) return@mapNotNull null
      val id = unescapeField(parts[0]).ifBlank { "custom_${System.nanoTime()}" }
      val abbrev = unescapeField(parts[1]).trim()
      val title = unescapeField(parts[2]).trim()
      val tag = unescapeField(parts[3]).trim().ifBlank { "CUSTOM" }
      val code = unescapeField(parts[4])
      val desc = if (parts.size >= 6) unescapeField(parts[5]) else ""
      if (abbrev.isBlank() || code.isBlank()) return@mapNotNull null
      CodeSnippetItem(
        id = id,
        abbrev = abbrev,
        title = title.ifBlank { abbrev },
        tag = tag,
        code = code,
        description = desc,
        isCustom = true,
      )
    }
  }

  private fun escapeField(value: String): String =
    buildString(value.length) {
      value.forEach { ch ->
        when (ch) {
          '\\' -> append("\\\\")
          '\n' -> append("\\n")
          '\r' -> append("\\r")
          RECORD_SEP -> append("\\E")
          FIELD_SEP -> append("\\F")
          else -> append(ch)
        }
      }
    }

  private fun unescapeField(value: String): String =
    buildString(value.length) {
      var i = 0
      while (i < value.length) {
        val ch = value[i]
        if (ch == '\\' && i + 1 < value.length) {
          when (value[i + 1]) {
            '\\' -> append('\\')
            'n' -> append('\n')
            'r' -> append('\r')
            'E' -> append(RECORD_SEP)
            'F' -> append(FIELD_SEP)
            else -> append(value[i + 1])
          }
          i += 2
        } else {
          append(ch)
          i++
        }
      }
    }
}
