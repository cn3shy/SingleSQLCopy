package io.github.cn3shy.ssc.core

/**
 * 手写词法状态机实现的 SQL 单行压缩器。
 *
 * 该类型不依赖任何 IntelliJ Platform API，可直接用于单元测试。
 * 压缩过程只折叠普通空白与注释，不会重写字符串、Oracle q-quote、
 * 双引号标识符、数字、标识符或运算符。
 */
class SqlOneLineMinifier {

    fun minify(sql: String, options: MinifyOptions = MinifyOptions()): MinifyResult {
        if (sql.isEmpty()) {
            return MinifyResult(output = "", warnings = emptyList(), containsStringNewline = false)
        }

        val tokens = SqlLexer(sql, options.dialect).tokenize()
        val output = StringBuilder()
        val warnings = mutableListOf<String>()

        var containsStringNewline = false
        var hasOracleHint = false
        var pendingWhitespace = ""
        var afterKeptLineComment = false

        fun queueWhitespace(text: String) {
            if (afterKeptLineComment || output.isEmpty()) return
            pendingWhitespace = if (options.collapseWhitespace) " " else pendingWhitespace + text
        }

        fun queueRemovedCommentBoundary() {
            if (afterKeptLineComment || output.isEmpty()) return
            if (pendingWhitespace.isEmpty() || options.collapseWhitespace) {
                pendingWhitespace = " "
            }
        }

        fun flushPendingWhitespace() {
            if (pendingWhitespace.isEmpty()) return
            if (output.isEmpty()) {
                pendingWhitespace = ""
                return
            }
            if (options.collapseWhitespace) {
                val last = output.last()
                if (!last.isWhitespace()) {
                    output.append(' ')
                }
            } else {
                output.append(pendingWhitespace)
            }
            pendingWhitespace = ""
        }

        fun appendTokenText(text: String) {
            if (afterKeptLineComment) {
                pendingWhitespace = ""
                output.append(text)
                afterKeptLineComment = false
            } else {
                flushPendingWhitespace()
                output.append(text)
            }
        }

        for (token in tokens) {
            when (token.type) {
                SqlTokenType.WHITESPACE -> queueWhitespace(token.text)

                SqlTokenType.LINE_COMMENT -> {
                    if (options.removeComments) {
                        queueRemovedCommentBoundary()
                    } else {
                        appendTokenText(token.text)
                        output.append('\n')
                        afterKeptLineComment = true
                        pendingWhitespace = ""
                    }
                }

                SqlTokenType.BLOCK_COMMENT, SqlTokenType.ORACLE_HINT -> {
                    if (token.type == SqlTokenType.ORACLE_HINT) {
                        hasOracleHint = true
                    }
                    val shouldRemove = options.removeComments &&
                        (token.type != SqlTokenType.ORACLE_HINT || !options.keepOracleHints)
                    if (shouldRemove) {
                        queueRemovedCommentBoundary()
                    } else {
                        appendTokenText(token.text)
                    }
                }

                SqlTokenType.STRING,
                SqlTokenType.Q_QUOTE,
                SqlTokenType.QUOTED_IDENTIFIER,
                SqlTokenType.WORD,
                SqlTokenType.SYMBOL,
                SqlTokenType.OTHER -> {
                    if ((token.type == SqlTokenType.STRING || token.type == SqlTokenType.Q_QUOTE) &&
                        (token.text.indexOf('\n') >= 0 || token.text.indexOf('\r') >= 0)
                    ) {
                        containsStringNewline = true
                    }
                    appendTokenText(token.text)
                    if (token.type == SqlTokenType.SYMBOL && token.text == ";") {
                        pendingWhitespace = " "
                    }
                }
            }
        }

        if (options.removeComments && !options.keepOracleHints && hasOracleHint) {
            warnings += ORACLE_HINT_REMOVED_WARNING
        }
        if (containsStringNewline) {
            warnings += STRING_NEWLINE_WARNING
        }

        return MinifyResult(
            output = output.toString().trimEnd(),
            warnings = warnings,
            containsStringNewline = containsStringNewline
        )
    }

    private companion object {
        const val ORACLE_HINT_REMOVED_WARNING = "检测到 Oracle Hint，删除可能影响执行计划"
        const val STRING_NEWLINE_WARNING =
            "字符串字面量内含换行，未删除以保持语义，输出可能不是物理单行"
    }
}

internal enum class SqlTokenType {
    WORD,
    SYMBOL,
    WHITESPACE,
    LINE_COMMENT,
    BLOCK_COMMENT,
    ORACLE_HINT,
    STRING,
    Q_QUOTE,
    QUOTED_IDENTIFIER,
    OTHER
}

internal data class SqlToken(
    val type: SqlTokenType,
    val text: String
)

/**
 * 供压缩器与安全扫描器共用的最小 SQL 词法分析器。
 *
 * 它只负责把输入切分为受保护片段和普通片段，不做 SQL 语法解析。
 */
internal class SqlLexer(
    private val source: String,
    private val dialect: SqlDialect
) {

    fun tokenize(): List<SqlToken> {
        val tokens = ArrayList<SqlToken>()
        var index = 0

        while (index < source.length) {
            val current = source[index]

            if (current.isWhitespace()) {
                val start = index
                index++
                while (index < source.length && source[index].isWhitespace()) {
                    index++
                }
                tokens += SqlToken(SqlTokenType.WHITESPACE, source.substring(start, index))
                continue
            }

            if (current == '\'') {
                val end = readQuoted(index, '\'')
                tokens += SqlToken(SqlTokenType.STRING, source.substring(index, end))
                index = end
                continue
            }

            if (current == '"') {
                val end = readQuoted(index, '"')
                tokens += SqlToken(SqlTokenType.QUOTED_IDENTIFIER, source.substring(index, end))
                index = end
                continue
            }

            if ((current == 'q' || current == 'Q') && isQQuoteStart(index)) {
                val end = readQQuote(index)
                tokens += SqlToken(SqlTokenType.Q_QUOTE, source.substring(index, end))
                index = end
                continue
            }

            if (current == '-' && index + 1 < source.length && source[index + 1] == '-') {
                val end = readLineComment(index)
                tokens += SqlToken(SqlTokenType.LINE_COMMENT, source.substring(index, end))
                index = end
                continue
            }

            if (current == '#' && isGenericHashCommentStart(index)) {
                val end = readLineComment(index)
                tokens += SqlToken(SqlTokenType.LINE_COMMENT, source.substring(index, end))
                index = end
                continue
            }

            if (current == '/' && index + 1 < source.length && source[index + 1] == '*') {
                val isHint = index + 2 < source.length && source[index + 2] == '+'
                val end = readBlockComment(index)
                val type = if (isHint) SqlTokenType.ORACLE_HINT else SqlTokenType.BLOCK_COMMENT
                tokens += SqlToken(type, source.substring(index, end))
                index = end
                continue
            }

            if (isWordCharacter(current)) {
                val start = index
                index++
                while (index < source.length && isWordCharacter(source[index])) {
                    index++
                }
                tokens += SqlToken(SqlTokenType.WORD, source.substring(start, index))
                continue
            }

            val type = if (isSymbolCharacter(current)) SqlTokenType.SYMBOL else SqlTokenType.OTHER
            tokens += SqlToken(type, current.toString())
            index++
        }

        return tokens
    }

    private fun readQuoted(start: Int, quote: Char): Int {
        var index = start + 1
        while (index < source.length) {
            if (source[index] == quote) {
                if (index + 1 < source.length && source[index + 1] == quote) {
                    index += 2
                } else {
                    return index + 1
                }
            } else {
                index++
            }
        }
        return source.length
    }

    private fun isQQuoteStart(start: Int): Boolean {
        if (start + 2 >= source.length) return false
        if (source[start + 1] != '\'') return false
        val delimiter = source[start + 2]
        return !delimiter.isWhitespace() && delimiter != '\''
    }

    private fun readQQuote(start: Int): Int {
        val delimiter = source[start + 2]
        val closingDelimiter = when (delimiter) {
            '[' -> ']'
            '{' -> '}'
            '(' -> ')'
            '<' -> '>'
            else -> delimiter
        }

        var index = start + 3
        while (index + 1 < source.length) {
            if (source[index] == closingDelimiter && source[index + 1] == '\'') {
                return index + 2
            }
            index++
        }
        return source.length
    }

    private fun readLineComment(start: Int): Int {
        var index = start
        while (index < source.length && source[index] != '\n' && source[index] != '\r') {
            index++
        }
        return index
    }

    private fun readBlockComment(start: Int): Int {
        val closing = source.indexOf("*/", start + 2)
        return if (closing >= 0) closing + 2 else source.length
    }

    private fun isGenericHashCommentStart(index: Int): Boolean {
        if (dialect != SqlDialect.GENERIC) return false
        if (index == 0) return true
        return source[index - 1].isWhitespace()
    }

    private fun isWordCharacter(character: Char): Boolean {
        return character.isLetterOrDigit() ||
            character == '_' ||
            character == '$' ||
            character == '#'
    }

    private fun isSymbolCharacter(character: Char): Boolean {
        return character == ';' ||
            character == ',' ||
            character == '(' ||
            character == ')' ||
            character == '[' ||
            character == ']' ||
            character == '{' ||
            character == '}' ||
            character == '+' ||
            character == '-' ||
            character == '*' ||
            character == '/' ||
            character == '=' ||
            character == '<' ||
            character == '>' ||
            character == '!' ||
            character == '|' ||
            character == ':' ||
            character == '.' ||
            character == '%' ||
            character == '^' ||
            character == '~' ||
            character == '&' ||
            character == '?' ||
            character == '@'
    }
}
