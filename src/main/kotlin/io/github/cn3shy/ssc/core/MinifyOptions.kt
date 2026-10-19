package io.github.cn3shy.ssc.core

/**
 * 压缩选项。
 *
 * [preserveStringNewlines] 保留用于 API 兼容。出于“不得改变 SQL 语义”的安全约束，
 * 字符串、q-quote 和双引号标识符内部的内容始终原样保留，即使该选项为 false 也不会
 * 改写其中的换行。
 */
data class MinifyOptions(
    val dialect: SqlDialect = SqlDialect.ORACLE,
    val removeComments: Boolean = true,
    val keepOracleHints: Boolean = true,
    val collapseWhitespace: Boolean = true,
    val preserveStringNewlines: Boolean = true
)
