package com.example.editor

import com.example.data.remote.CfD1QueryResult
import com.example.data.remote.D1ColumnInfo
import com.example.data.remote.D1ForeignKeyInfo
import com.example.data.remote.D1IndexInfo
import com.example.data.remote.D1MigrationStatus
import com.example.data.remote.D1NodeOffset
import com.example.data.remote.D1SchemaGraph
import com.example.data.remote.D1TableInfo
import com.example.data.remote.D1WorkspaceMigration
import com.example.data.remote.WorkerFile
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

object D1RelationalEngine {
  fun quoteIdent(identifier: String): String {
    val clean = identifier.replace("\"", "\"\"")
    return "\"$clean\""
  }

  fun formatSqlLiteral(
    rawValue: String?,
    setNull: Boolean,
    columnType: String,
  ): String {
    if (setNull || rawValue == null) return "NULL"
    val upperType = columnType.uppercase()
    val trimmed = rawValue.trim()
    val looksNumeric =
      (upperType.contains("INT") || upperType.contains("REAL") || upperType.contains("NUM") ||
        upperType.contains("FLOAT") || upperType.contains("DOUBLE") || upperType.contains("DEC")) &&
        trimmed.matches(Regex("""-?\d+(\.\d+)?"""))
    if (looksNumeric) {
      return trimmed
    }
    return "'" + rawValue.replace("'", "''") + "'"
  }

  fun formatKeyValueLiteral(value: Any?): String {
    return when (value) {
      null -> "IS NULL"
      is Number -> "= $value"
      is Boolean -> "= ${if (value) 1 else 0}"
      else -> {
        val str = value.toString()
        val num = str.toLongOrNull()
        if (num != null && num.toString() == str) {
          "= $num"
        } else {
          "= '${str.replace("'", "''")}'"
        }
      }
    }
  }

  fun buildIntrospectionMasterSql(): String {
    return "SELECT name, sql FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE '_cf_%' ORDER BY name;"
  }

  fun buildTableInspectionSqlChunk(tableNames: List<String>): String {
    val sb = StringBuilder()
    tableNames.forEach { t ->
      val q = quoteIdent(t)
      val esc = t.replace("'", "''")
      sb.append("PRAGMA table_info('$esc');\n")
      sb.append("PRAGMA foreign_key_list('$esc');\n")
      sb.append("PRAGMA index_list('$esc');\n")
      sb.append("SELECT COUNT(*) AS row_count FROM $q;\n")
    }
    return sb.toString()
  }

  fun parseMasterTables(masterResult: CfD1QueryResult?): List<Pair<String, String>> {
    if (masterResult == null) return emptyList()
    return masterResult.results.mapNotNull { row ->
      val name = row["name"]?.toString()?.trim().orEmpty()
      if (name.isEmpty()) return@mapNotNull null
      val sql = row["sql"]?.toString().orEmpty()
      name to sql
    }
  }

  fun parseTableDetailsChunk(
    databaseId: String,
    databaseName: String,
    tablesWithDdl: List<Pair<String, String>>,
    chunkResults: List<CfD1QueryResult>,
  ): List<D1TableInfo> {
    val parsed = ArrayList<D1TableInfo>(tablesWithDdl.size)
    tablesWithDdl.forEachIndexed { idx, (tableName, createSql) ->
      val base = idx * 4
      val infoRows = chunkResults.getOrNull(base)?.results.orEmpty()
      val fkRows = chunkResults.getOrNull(base + 1)?.results.orEmpty()
      val indexRows = chunkResults.getOrNull(base + 2)?.results.orEmpty()
      val countRow = chunkResults.getOrNull(base + 3)?.results?.firstOrNull()

      val foreignKeys =
        fkRows.mapNotNull { row ->
          val fromCol = row["from"]?.toString()?.trim().orEmpty()
          val toTable = row["table"]?.toString()?.trim().orEmpty()
          val toCol = row["to"]?.toString()?.trim().orEmpty().ifEmpty { "id" }
          if (fromCol.isEmpty() || toTable.isEmpty()) return@mapNotNull null
          D1ForeignKeyInfo(
            id = (row["id"] as? Number)?.toInt() ?: 0,
            fromTable = tableName,
            fromColumn = fromCol,
            toTable = toTable,
            toColumn = toCol,
            onUpdate = row["on_update"]?.toString() ?: "NO ACTION",
            onDelete = row["on_delete"]?.toString() ?: "NO ACTION",
          )
        }
      val fkColNames = foreignKeys.map { it.fromColumn }.toSet()

      val columns =
        infoRows.mapNotNull { row ->
          val colName = row["name"]?.toString()?.trim().orEmpty()
          if (colName.isEmpty()) return@mapNotNull null
          val pkVal = (row["pk"] as? Number)?.toInt() ?: row["pk"]?.toString()?.toIntOrNull() ?: 0
          val nnVal = (row["notnull"] as? Number)?.toInt() ?: row["notnull"]?.toString()?.toIntOrNull() ?: 0
          D1ColumnInfo(
            cid = (row["cid"] as? Number)?.toInt() ?: 0,
            name = colName,
            type = row["type"]?.toString()?.ifBlank { "ANY" } ?: "ANY",
            notNull = nnVal > 0 || pkVal > 0,
            defaultValue = row["dflt_value"]?.toString(),
            isPrimaryKey = pkVal > 0,
            primaryKeyOrder = pkVal,
            isForeignKey = colName in fkColNames,
          )
        }

      val indexes =
        indexRows.mapNotNull { row ->
          val ixName = row["name"]?.toString()?.trim().orEmpty()
          if (ixName.isEmpty()) return@mapNotNull null
          val uniq = ((row["unique"] as? Number)?.toInt() ?: 0) > 0
          D1IndexInfo(name = ixName, unique = uniq)
        }

      val rowCount =
        (countRow?.get("row_count") as? Number)?.toLong()
          ?: countRow?.values?.firstOrNull()?.toString()?.toLongOrNull()
          ?: 0L

      parsed.add(
        D1TableInfo(
          name = tableName,
          createSql = createSql,
          rowCountEstimate = rowCount,
          columns = columns,
          foreignKeys = foreignKeys,
          indexes = indexes,
        ),
      )
    }
    return parsed
  }

  fun computeLayeredErLayout(tables: List<D1TableInfo>): Map<String, D1NodeOffset> {
    if (tables.isEmpty()) return emptyMap()
    val byName = tables.associateBy { it.name }
    val depths = mutableMapOf<String, Int>()
    val visiting = mutableSetOf<String>()

    fun computeDepth(tableName: String): Int {
      depths[tableName]?.let { return it }
      if (!visiting.add(tableName)) return 0
      val tbl = byName[tableName]
      val maxParentDepth =
        tbl?.foreignKeys
          ?.filter { it.toTable != tableName && byName.containsKey(it.toTable) }
          ?.maxOfOrNull { computeDepth(it.toTable) + 1 }
          ?: 0
      visiting.remove(tableName)
      depths[tableName] = maxParentDepth
      return maxParentDepth
    }

    tables.forEach { computeDepth(it.name) }

    val grouped =
      tables
        .sortedBy { it.name.lowercase() }
        .groupBy { depths[it.name] ?: 0 }
        .toSortedMap()

    val cardW = 240f
    val colGap = 56f
    val rowGap = 44f
    val positions = linkedMapOf<String, D1NodeOffset>()

    var currentY = 36f
    grouped.forEach { (_, layerTables) ->
      val colsPerRow = 3
      var maxRowHeight = 0f
      layerTables.chunked(colsPerRow).forEach { chunk ->
        var x = 36f
        maxRowHeight = 0f
        chunk.forEach { tbl ->
          val estimatedHeight = 64f + (tbl.columns.size.coerceIn(1, 8) * 24f)
          positions[tbl.name] = D1NodeOffset(x = x, y = currentY)
          x += cardW + colGap
          if (estimatedHeight > maxRowHeight) maxRowHeight = estimatedHeight
        }
        currentY += maxRowHeight + rowGap
      }
    }
    return positions
  }

  fun buildBrowseRowsSql(
    tableName: String,
    columns: List<D1ColumnInfo>,
    searchQuery: String,
    sortColumn: String?,
    sortDescending: Boolean,
    limit: Int,
    offset: Int,
    exactFilterColumn: String? = null,
    exactFilterValue: String? = null,
  ): String {
    val qTable = quoteIdent(tableName)
    val clauses = mutableListOf<String>()
    if (!exactFilterColumn.isNullOrBlank() && exactFilterValue != null) {
      val qCol = quoteIdent(exactFilterColumn)
      if (exactFilterValue == "NULL") {
        clauses.add("$qCol IS NULL")
      } else {
        clauses.add("CAST($qCol AS TEXT) = '${exactFilterValue.replace("'", "''")}'")
      }
    }
    val qSearch = searchQuery.trim()
    if (qSearch.isNotEmpty() && columns.isNotEmpty()) {
      val escaped = qSearch.replace("'", "''")
      val likeParts = columns.take(12).joinToString(" OR ") { col ->
        "CAST(${quoteIdent(col.name)} AS TEXT) LIKE '%$escaped%'"
      }
      clauses.add("($likeParts)")
    }
    val whereSql = if (clauses.isEmpty()) "" else " WHERE " + clauses.joinToString(" AND ")
    val orderSql =
      if (!sortColumn.isNullOrBlank() && columns.any { it.name == sortColumn }) {
        " ORDER BY ${quoteIdent(sortColumn)} ${if (sortDescending) "DESC" else "ASC"}"
      } else {
        val pk = columns.firstOrNull { it.isPrimaryKey }
        if (pk != null) " ORDER BY ${quoteIdent(pk.name)} ASC" else ""
      }
    return "SELECT * FROM $qTable$whereSql$orderSql LIMIT ${limit.coerceIn(1, 200)} OFFSET ${offset.coerceAtLeast(0)};"
  }

  fun buildRowWhereClause(
    pkColumns: List<D1ColumnInfo>,
    allColumns: List<D1ColumnInfo>,
    row: Map<String, Any?>,
  ): String? {
    val keyCols =
      if (pkColumns.isNotEmpty()) {
        pkColumns.map { it.name }
      } else {
        allColumns.filter { row.containsKey(it.name) }.take(4).map { it.name }
      }
    if (keyCols.isEmpty()) return null
    return keyCols.joinToString(" AND ") { colName ->
      val valExpr = formatKeyValueLiteral(row[colName])
      "${quoteIdent(colName)} $valExpr"
    }
  }

  fun buildUpdateCellSql(
    tableName: String,
    pkColumns: List<D1ColumnInfo>,
    allColumns: List<D1ColumnInfo>,
    row: Map<String, Any?>,
    targetColumn: D1ColumnInfo,
    newValue: String?,
    setNull: Boolean,
  ): String? {
    val whereClause = buildRowWhereClause(pkColumns, allColumns, row) ?: return null
    val literal = formatSqlLiteral(newValue, setNull, targetColumn.type)
    return "UPDATE ${quoteIdent(tableName)} SET ${quoteIdent(targetColumn.name)} = $literal WHERE $whereClause;"
  }

  fun buildInsertRowSql(
    tableName: String,
    entries: List<Triple<D1ColumnInfo, String, Boolean>>,
  ): String {
    val active = entries.filter { (col, text, isNull) ->
      if (col.isPrimaryKey && text.isBlank() && !isNull && col.type.uppercase().contains("INT")) {
        false
      } else {
        true
      }
    }
    if (active.isEmpty()) {
      return "INSERT INTO ${quoteIdent(tableName)} DEFAULT VALUES;"
    }
    val colsSql = active.joinToString(", ") { quoteIdent(it.first.name) }
    val valsSql = active.joinToString(", ") { (col, text, isNull) ->
      formatSqlLiteral(text, isNull, col.type)
    }
    return "INSERT INTO ${quoteIdent(tableName)} ($colsSql) VALUES ($valsSql);"
  }

  fun buildDeleteRowSql(
    tableName: String,
    pkColumns: List<D1ColumnInfo>,
    allColumns: List<D1ColumnInfo>,
    row: Map<String, Any?>,
  ): String? {
    val whereClause = buildRowWhereClause(pkColumns, allColumns, row) ?: return null
    return "DELETE FROM ${quoteIdent(tableName)} WHERE $whereClause;"
  }

  fun splitSqlStatements(sql: String): List<String> {
    val result = mutableListOf<String>()
    val current = StringBuilder()
    var inSingle = false
    var inDouble = false
    var inLineComment = false
    var inBlockComment = false
    var i = 0
    val len = sql.length

    while (i < len) {
      val c = sql[i]
      val next = if (i + 1 < len) sql[i + 1] else '\u0000'

      if (inLineComment) {
        current.append(c)
        if (c == '\n') inLineComment = false
        i++
        continue
      }
      if (inBlockComment) {
        current.append(c)
        if (c == '*' && next == '/') {
          current.append('/')
          inBlockComment = false
          i += 2
        } else {
          i++
        }
        continue
      }
      if (inSingle) {
        current.append(c)
        if (c == '\'' && next == '\'') {
          current.append('\'')
          i += 2
        } else {
          if (c == '\'') inSingle = false
          i++
        }
        continue
      }
      if (inDouble) {
        current.append(c)
        if (c == '"' && next == '"') {
          current.append('"')
          i += 2
        } else {
          if (c == '"') inDouble = false
          i++
        }
        continue
      }

      if (c == '-' && next == '-') {
        inLineComment = true
        current.append("--")
        i += 2
        continue
      }
      if (c == '/' && next == '*') {
        inBlockComment = true
        current.append("/*")
        i += 2
        continue
      }
      if (c == '\'') {
        inSingle = true
        current.append(c)
        i++
        continue
      }
      if (c == '"') {
        inDouble = true
        current.append(c)
        i++
        continue
      }
      if (c == ';') {
        val stmt = current.toString().trim()
        if (stmt.isNotBlank() && !isCommentOnlySql(stmt)) {
          result.add("$stmt;")
        }
        current.setLength(0)
        i++
        continue
      }
      current.append(c)
      i++
    }
    val tail = current.toString().trim()
    if (tail.isNotBlank() && !isCommentOnlySql(tail)) {
      result.add("$tail;")
    }
    return result
  }

  private fun isCommentOnlySql(stmt: String): Boolean {
    return stmt.lines().all { line ->
      val t = line.trim()
      t.isEmpty() || t.startsWith("--")
    }
  }

  fun sha256Hex(text: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }.take(16)
  }

  fun discoverWorkspaceMigrations(
    files: List<WorkerFile>,
    appliedByName: Map<String, Pair<String, String?>> = emptyMap(),
  ): List<D1WorkspaceMigration> {
    val sqlFiles =
      files.filter { f ->
        !f.isVirtualReadOnly &&
          f.path.endsWith(".sql", ignoreCase = true) &&
          (f.path.contains("migrations/", ignoreCase = true) ||
            f.path.equals("schema.sql", ignoreCase = true) ||
            f.path.startsWith("db/", ignoreCase = true) ||
            f.path.startsWith("sql/", ignoreCase = true))
      }.sortedBy { it.path.lowercase() }

    return sqlFiles.map { file ->
      val stmts = splitSqlStatements(file.content)
      val hash = sha256Hex(file.content.trim())
      val byFileName = appliedByName[file.fileName] ?: appliedByName[file.path]
      val status =
        when {
          byFileName == null -> D1MigrationStatus.PENDING
          byFileName.second != null && byFileName.second != hash -> D1MigrationStatus.CHECKSUM_MISMATCH
          else -> D1MigrationStatus.APPLIED
        }
      D1WorkspaceMigration(
        filePath = file.path,
        fileName = file.fileName,
        sql = file.content,
        statementCount = stmts.size,
        checksum = hash,
        status = status,
        appliedAt = byFileName?.first,
      )
    }
  }

  fun exportRowsAsCsv(
    columns: List<String>,
    rows: List<Map<String, Any?>>,
  ): String {
    if (columns.isEmpty()) return ""
    fun escapeCsv(raw: Any?): String {
      if (raw == null) return ""
      val s = raw.toString()
      return if (s.contains(',') || s.contains('"') || s.contains('\n') || s.contains('\r')) {
        "\"" + s.replace("\"", "\"\"") + "\""
      } else {
        s
      }
    }
    val sb = StringBuilder()
    sb.append(columns.joinToString(",") { escapeCsv(it) }).append('\n')
    rows.forEach { row ->
      sb.append(columns.joinToString(",") { col -> escapeCsv(row[col]) }).append('\n')
    }
    return sb.toString()
  }

  fun exportRowsAsJson(rows: List<Map<String, Any?>>): String {
    val arr = JSONArray()
    rows.forEach { row ->
      val obj = JSONObject()
      row.forEach { (k, v) -> obj.put(k, v ?: JSONObject.NULL) }
      arr.put(obj)
    }
    return arr.toString(2)
  }

  fun exportSchemaDdl(tables: List<D1TableInfo>): String {
    return tables.joinToString("\n\n") { tbl ->
      if (tbl.createSql.isNotBlank()) {
        val trimmed = tbl.createSql.trim()
        if (trimmed.endsWith(";")) trimmed else "$trimmed;"
      } else {
        val cols = tbl.columns.joinToString(",\n  ") { col ->
          buildString {
            append(quoteIdent(col.name)).append(" ").append(col.type)
            if (col.isPrimaryKey) append(" PRIMARY KEY")
            if (col.notNull && !col.isPrimaryKey) append(" NOT NULL")
            if (!col.defaultValue.isNullOrBlank()) append(" DEFAULT ").append(col.defaultValue)
          }
        }
        "CREATE TABLE ${quoteIdent(tbl.name)} (\n  $cols\n);"
      }
    }
  }
}
