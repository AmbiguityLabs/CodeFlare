package com.example.data.remote

data class D1ColumnInfo(
  val cid: Int = 0,
  val name: String,
  val type: String = "TEXT",
  val notNull: Boolean = false,
  val defaultValue: String? = null,
  val isPrimaryKey: Boolean = false,
  val primaryKeyOrder: Int = 0,
  val isForeignKey: Boolean = false,
)

data class D1ForeignKeyInfo(
  val id: Int = 0,
  val fromTable: String,
  val fromColumn: String,
  val toTable: String,
  val toColumn: String,
  val onUpdate: String = "NO ACTION",
  val onDelete: String = "NO ACTION",
)

data class D1IndexInfo(
  val name: String,
  val unique: Boolean = false,
  val columns: List<String> = emptyList(),
)

data class D1TableInfo(
  val name: String,
  val createSql: String = "",
  val rowCountEstimate: Long = 0L,
  val columns: List<D1ColumnInfo> = emptyList(),
  val foreignKeys: List<D1ForeignKeyInfo> = emptyList(),
  val indexes: List<D1IndexInfo> = emptyList(),
) {
  val primaryKeyColumns: List<D1ColumnInfo>
    get() = columns.filter { it.isPrimaryKey }.sortedBy { it.primaryKeyOrder }
}

data class D1SchemaGraph(
  val databaseId: String = "",
  val databaseName: String = "",
  val tables: List<D1TableInfo> = emptyList(),
  val introspectedAtEpochMs: Long = 0L,
  val errorMessage: String? = null,
) {
  val totalForeignKeys: Int
    get() = tables.sumOf { it.foreignKeys.size }
}

data class D1NodeOffset(
  val x: Float = 0f,
  val y: Float = 0f,
)

data class D1CellEditDraft(
  val tableName: String,
  val rowIndex: Int,
  val row: Map<String, Any?>,
  val column: D1ColumnInfo,
  val currentValueText: String,
  val isCurrentlyNull: Boolean,
)

data class D1FkBreadcrumb(
  val tableName: String,
  val filterColumn: String? = null,
  val filterValue: String? = null,
)

data class D1TableBrowserState(
  val selectedTableName: String? = null,
  val rows: List<Map<String, Any?>> = emptyList(),
  val columns: List<D1ColumnInfo> = emptyList(),
  val totalRowsEstimate: Long = 0L,
  val pageSize: Int = 25,
  val pageOffset: Int = 0,
  val sortColumn: String? = null,
  val sortDescending: Boolean = false,
  val searchQuery: String = "",
  val exactFilterColumn: String? = null,
  val exactFilterValue: String? = null,
  val breadcrumbs: List<D1FkBreadcrumb> = emptyList(),
  val isLoading: Boolean = false,
  val isMutating: Boolean = false,
  val lastExecutedSql: String = "",
  val errorMessage: String? = null,
)

enum class D1StudioMode(val label: String) {
  ER_GRAPH("ER Graph"),
  ROWS("Tables & Rows"),
  MIGRATIONS("Migrations"),
  SQL_CONSOLE("SQL Console"),
}

enum class D1MigrationStatus(val label: String) {
  APPLIED("APPLIED"),
  PENDING("PENDING"),
  CHECKSUM_MISMATCH("DRIFT"),
}

data class D1WorkspaceMigration(
  val filePath: String,
  val fileName: String,
  val sql: String,
  val statementCount: Int,
  val checksum: String,
  val status: D1MigrationStatus = D1MigrationStatus.PENDING,
  val appliedAt: String? = null,
)

data class D1AppliedMigrationRecord(
  val name: String,
  val appliedAt: String = "",
  val checksum: String? = null,
)

data class D1MigrationState(
  val ledgerTableName: String = "_edgecode_migrations",
  val migrations: List<D1WorkspaceMigration> = emptyList(),
  val isLoading: Boolean = false,
  val isApplying: Boolean = false,
  val lastExecutionOutput: String? = null,
  val lastError: String? = null,
) {
  val pendingCount: Int
    get() = migrations.count { it.status != D1MigrationStatus.APPLIED }
}

data class XRayCorrelatedLog(
  val timestamp: String,
  val level: String,
  val message: String,
  val matchedByTraceId: Boolean,
)

data class RequestXRayTrace(
  val traceId: String,
  val method: String,
  val url: String,
  val statusCode: Int,
  val statusText: String,
  val durationMs: Long,
  val cfRay: String? = null,
  val edgeColo: String? = null,
  val startedAtEpochMs: Long = 0L,
  val finishedAtEpochMs: Long = 0L,
  val correlatedLogs: List<XRayCorrelatedLog> = emptyList(),
  val hasException: Boolean = false,
  val boundResources: List<CfWorkerBinding> = emptyList(),
)
