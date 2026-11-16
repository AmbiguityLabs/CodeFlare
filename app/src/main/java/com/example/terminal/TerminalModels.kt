package com.example.terminal

enum class TerminalEntryKind {
  SHELL,
  HTTP,
  WRANGLER,
  SYSTEM,
  ERROR,
}

data class HttpTraceMeta(
  val method: String,
  val url: String,
  val protocol: String,
  val statusCode: Int,
  val statusText: String,
  val durationMs: Long,
  val timingSummary: String,
  val headersText: String,
  val bodyText: String,
  val curlEquivalent: String,
)

data class TerminalEntry(
  val id: Long,
  val timestamp: String,
  val promptPrefix: String,
  val command: String,
  val output: String,
  val isError: Boolean = false,
  val exitCode: Int = 0,
  val kind: TerminalEntryKind = TerminalEntryKind.SHELL,
  val httpMeta: HttpTraceMeta? = null,
)
