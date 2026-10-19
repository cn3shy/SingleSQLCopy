package io.github.cn3shy.ssc.core

/**
 * 压缩与安全扫描使用的 SQL 方言。
 *
 * ORACLE 会识别 Oracle q-quote 与 Oracle Hint，并将 # 视为普通标识符字符。
 * GENERIC 额外支持以 # 开头的行注释。
 */
enum class SqlDialect {
    ORACLE,
    GENERIC
}
