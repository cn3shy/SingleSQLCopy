package io.github.cn3shy.ssc.core

/**
 * 压缩结果。
 *
 * @property output 压缩后的 SQL。
 * @property warnings 安全警告，例如 Oracle Hint 被删除、字符串内含换行。
 * @property containsStringNewline 字符串字面量或 q-quote 内部是否包含换行。
 */
data class MinifyResult(
    val output: String,
    val warnings: List<String>,
    val containsStringNewline: Boolean
)
