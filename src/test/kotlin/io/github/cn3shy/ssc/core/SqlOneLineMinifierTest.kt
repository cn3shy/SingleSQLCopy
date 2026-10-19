package io.github.cn3shy.ssc.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SqlOneLineMinifierTest {

    private val minifier = SqlOneLineMinifier()

    @Test
    fun `multiline select is collapsed into one line`() {
        val sql = """
            SELECT id,
                   name
            FROM users
            WHERE id = 1;
        """.trimIndent()

        val result = minifier.minify(sql)

        assertEquals("SELECT id, name FROM users WHERE id = 1;", result.output)
        assertFalse(result.containsStringNewline)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `consecutive spaces tabs and newlines are collapsed`() {
        val sql = "SELECT\t\t*\r\n  FROM   dual\nWHERE 1=1"

        val result = minifier.minify(sql)

        assertEquals("SELECT * FROM dual WHERE 1=1", result.output)
    }

    @Test
    fun `line comments are removed`() {
        val sql = """
            SELECT 1 -- remove me
            FROM dual;
        """.trimIndent()

        val result = minifier.minify(sql)

        assertEquals("SELECT 1 FROM dual;", result.output)
    }

    @Test
    fun `block comments are removed`() {
        val sql = "SELECT 1 /* remove me */ FROM dual;"

        val result = minifier.minify(sql)

        assertEquals("SELECT 1 FROM dual;", result.output)
    }

    @Test
    fun `comment markers inside strings are retained`() {
        val sql = "SELECT '-- not comment' AS a, '/* not comment */' AS b FROM dual;"

        val result = minifier.minify(sql)

        assertEquals(sql, result.output)
    }

    @Test
    fun `two single quotes represent an escaped quote`() {
        val literal = "'It''s a --comment /*comment*/; still string'"
        val sql = "SELECT $literal AS text FROM dual;"

        val result = minifier.minify(sql)

        assertEquals(sql, result.output)
        assertTrue(result.output.contains(literal))
    }

    @Test
    fun `oracle q quote content is retained exactly`() {
        val body = "line1\n-- not a comment\n/* not a comment */\n; still string\n"
        val sql = "SELECT q'[$body]' AS c FROM dual;"

        val result = minifier.minify(sql)

        assertTrue(result.output.startsWith("SELECT q'[line1\n"))
        assertTrue(result.output.contains("-- not a comment"))
        assertTrue(result.output.contains("/* not a comment */"))
        assertTrue(result.output.contains("; still string"))
        assertTrue(result.containsStringNewline)
        assertTrue(result.warnings.contains(STRING_NEWLINE_WARNING))
    }

    @Test
    fun `oracle q quote supports all common delimiters`() {
        val sql = "SELECT q'{a;b}', Q'(c;d)', q'<e;f>', q'!g;h!' FROM dual;"

        val result = minifier.minify(sql)

        assertEquals(sql, result.output)
        assertFalse(result.containsStringNewline)
    }

    @Test
    fun `whitespace inside double quoted identifiers is retained`() {
        val sql = "SELECT \"col  name\", \"a\"\"b  c\" FROM \"my  table\";"

        val result = minifier.minify(sql)

        assertEquals(sql, result.output)
    }

    @Test
    fun `oracle hints are kept by default`() {
        val sql = "SELECT /*+ INDEX(t idx_t) */ * FROM t WHERE id = 1;"

        val result = minifier.minify(sql)

        assertEquals(sql, result.output)
        assertTrue(result.output.contains("/*+ INDEX(t idx_t) */"))
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `oracle hints are removed with warning when retention is disabled`() {
        val sql = "SELECT /*+ INDEX(t idx_t) */ * FROM t WHERE id = 1;"
        val options = MinifyOptions(
            dialect = SqlDialect.ORACLE,
            removeComments = true,
            keepOracleHints = false,
            collapseWhitespace = true
        )

        val result = minifier.minify(sql, options)

        assertEquals("SELECT * FROM t WHERE id = 1;", result.output)
        assertTrue(result.warnings.contains(ORACLE_HINT_WARNING))
    }

    @Test
    fun `newline inside string is preserved and warned`() {
        val sql = "SELECT 'line 1\nline 2' AS value FROM dual"

        val result = minifier.minify(sql)

        assertEquals(sql, result.output)
        assertTrue(result.containsStringNewline)
        assertTrue(result.warnings.contains(STRING_NEWLINE_WARNING))
    }

    @Test
    fun `compression is idempotent`() {
        val sql = """
            -- leading comment
            SELECT /*+ INDEX(e idx_e) */ NVL(e.name, 'a -- b') AS n
            FROM employees e
            WHERE e.id = 1; /* trailing comment */
        """.trimIndent()

        val first = minifier.minify(sql)
        val second = minifier.minify(first.output)

        assertEquals(first.output, second.output)
    }

    @Test
    fun `generic hash comments are removed`() {
        val sql = "SELECT 1 # remove me\nFROM dual;"
        val options = MinifyOptions(dialect = SqlDialect.GENERIC)

        val result = minifier.minify(sql, options)

        assertEquals("SELECT 1 FROM dual;", result.output)
    }

    @Test
    fun `common oracle syntax is not broken`() {
        val sql = """
            SELECT NVL(col, 0),
                   DECODE(status, 'A', 1, 0) AS flag,
                   TO_DATE('2024-01-01', 'YYYY-MM-DD') AS d,
                   SYSDATE,
                   ROWNUM
            FROM dual
            START WITH manager_id IS NULL
            CONNECT BY PRIOR employee_id = manager_id;
        """.trimIndent()

        val result = minifier.minify(sql)

        assertEquals(
            "SELECT NVL(col, 0), DECODE(status, 'A', 1, 0) AS flag, " +
                "TO_DATE('2024-01-01', 'YYYY-MM-DD') AS d, SYSDATE, ROWNUM " +
                "FROM dual START WITH manager_id IS NULL " +
                "CONNECT BY PRIOR employee_id = manager_id;",
            result.output
        )
        assertFalse(result.containsStringNewline)
    }

    private companion object {
        const val ORACLE_HINT_WARNING = "检测到 Oracle Hint，删除可能影响执行计划"
        const val STRING_NEWLINE_WARNING =
            "字符串字面量内含换行，未删除以保持语义，输出可能不是物理单行"
    }
}
