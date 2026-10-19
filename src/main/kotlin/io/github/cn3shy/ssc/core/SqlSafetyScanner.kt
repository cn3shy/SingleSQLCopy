package io.github.cn3shy.ssc.core

/**
 * 轻量级 SQL 安全扫描器。
 *
 * 只扫描 UPDATE / DELETE 是否缺少顶层 WHERE，不修改、不删除任何 SQL 内容。
 * 分号切分复用词法分析器，因此字符串、Oracle q-quote、双引号标识符和注释内的
 * 分号不会被误判为语句分隔符。
 */
object SqlSafetyScanner {

    fun scan(sql: String, dialect: SqlDialect = SqlDialect.ORACLE): List<String> {
        if (sql.isEmpty()) return emptyList()

        val statements = splitStatements(SqlLexer(sql, dialect).tokenize())
        val warnings = mutableListOf<String>()

        for (statement in statements) {
            val significant = statement.filter { token ->
                token.type != SqlTokenType.WHITESPACE && !token.isComment()
            }
            if (significant.isEmpty()) continue

            val firstWordIndex = significant.indexOfFirst { it.type == SqlTokenType.WORD }
            if (firstWordIndex < 0) continue

            val firstWord = significant[firstWordIndex].text.uppercase()
            when (firstWord) {
                "UPDATE" -> {
                    if (!hasTopLevelWhere(significant, firstWordIndex + 1)) {
                        warnings += UPDATE_WITHOUT_WHERE_WARNING
                    }
                }

                "DELETE" -> {
                    if (!hasTopLevelWhere(significant, firstWordIndex + 1)) {
                        warnings += DELETE_WITHOUT_WHERE_WARNING
                    }
                }

                "WITH" -> {
                    val dmlIndex = findTopLevelDmlIndex(significant)
                    if (dmlIndex >= 0) {
                        val dml = significant[dmlIndex].text.uppercase()
                        if (!hasTopLevelWhere(significant, dmlIndex + 1)) {
                            when (dml) {
                                "UPDATE" -> warnings += UPDATE_WITHOUT_WHERE_WARNING
                                "DELETE" -> warnings += DELETE_WITHOUT_WHERE_WARNING
                            }
                        }
                    }
                }
            }
        }

        return warnings
    }

    private fun splitStatements(tokens: List<SqlToken>): List<List<SqlToken>> {
        val statements = mutableListOf<List<SqlToken>>()
        var current = mutableListOf<SqlToken>()

        for (token in tokens) {
            if (token.type == SqlTokenType.SYMBOL && token.text == ";") {
                if (current.any { it.type != SqlTokenType.WHITESPACE }) {
                    statements += current
                }
                current = mutableListOf()
            } else {
                current += token
            }
        }

        if (current.any { it.type != SqlTokenType.WHITESPACE }) {
            statements += current
        }
        return statements
    }

    private fun findTopLevelDmlIndex(tokens: List<SqlToken>): Int {
        var depth = 0
        for (index in tokens.indices) {
            val token = tokens[index]
            if (token.type == SqlTokenType.SYMBOL) {
                when (token.text) {
                    "(" -> {
                        depth++
                        continue
                    }

                    ")" -> {
                        if (depth > 0) depth--
                        continue
                    }
                }
            }
            if (depth == 0 && token.type == SqlTokenType.WORD) {
                val upper = token.text.uppercase()
                if (upper == "UPDATE" || upper == "DELETE") {
                    return index
                }
            }
        }
        return -1
    }

    private fun hasTopLevelWhere(tokens: List<SqlToken>, startIndex: Int): Boolean {
        var depth = 0
        for (index in startIndex until tokens.size) {
            val token = tokens[index]
            if (token.type == SqlTokenType.SYMBOL) {
                when (token.text) {
                    "(" -> depth++
                    ")" -> if (depth > 0) depth--
                }
            } else if (depth == 0 &&
                token.type == SqlTokenType.WORD &&
                token.text.equals("WHERE", ignoreCase = true)
            ) {
                return true
            }
        }
        return false
    }

    private fun SqlToken.isComment(): Boolean {
        return type == SqlTokenType.LINE_COMMENT ||
            type == SqlTokenType.BLOCK_COMMENT ||
            type == SqlTokenType.ORACLE_HINT
    }

    private const val UPDATE_WITHOUT_WHERE_WARNING = "检测到 UPDATE 语句缺少 WHERE，可能更新全表"
    private const val DELETE_WITHOUT_WHERE_WARNING = "检测到 DELETE 语句缺少 WHERE，可能删除全表数据"
}
