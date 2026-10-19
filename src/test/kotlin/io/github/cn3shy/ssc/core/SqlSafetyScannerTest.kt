package io.github.cn3shy.ssc.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SqlSafetyScannerTest {

    @Test
    fun `update without where is reported`() {
        val warnings = SqlSafetyScanner.scan("UPDATE users SET name = 'x'")

        assertEquals(listOf(UPDATE_WARNING), warnings)
    }

    @Test
    fun `update with where is not reported`() {
        val warnings = SqlSafetyScanner.scan("UPDATE users SET name = 'x' WHERE id = 1")

        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `delete without where is reported`() {
        val warnings = SqlSafetyScanner.scan("DELETE FROM users")

        assertEquals(listOf(DELETE_WARNING), warnings)
    }

    @Test
    fun `delete with where is not reported`() {
        val warnings = SqlSafetyScanner.scan("DELETE FROM users WHERE id = 1")

        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `semicolons in strings comments and q quotes do not split statements`() {
        val sql = """
            UPDATE t SET a = ';' /* ; in comment */
            WHERE id = 1;
            UPDATE t SET b = q'[; still string]' WHERE id = 2;
            DELETE FROM u /* ; */ WHERE id = 3;
            DELETE FROM v;
        """.trimIndent()

        val warnings = SqlSafetyScanner.scan(sql)

        assertEquals(listOf(DELETE_WARNING), warnings)
    }

    @Test
    fun `semicolon inside double quoted identifier is ignored`() {
        val sql = "UPDATE \"t;name\" SET a = 1 WHERE id = 2; DELETE FROM x WHERE id = 3;"

        val warnings = SqlSafetyScanner.scan(sql)

        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `multiple statements are scanned independently`() {
        val sql = "UPDATE t SET a = 1; DELETE FROM u WHERE id = 1; DELETE FROM v;"

        val warnings = SqlSafetyScanner.scan(sql)

        assertEquals(listOf(UPDATE_WARNING, DELETE_WARNING), warnings)
    }

    @Test
    fun `comments before the first keyword are ignored`() {
        val sql = "/* comment */ -- line comment\nDELETE FROM t;"

        val warnings = SqlSafetyScanner.scan(sql)

        assertEquals(listOf(DELETE_WARNING), warnings)
    }

    @Test
    fun `where in a subquery does not protect an update`() {
        val sql = "UPDATE t SET a = (SELECT x FROM u WHERE y = 1)"

        val warnings = SqlSafetyScanner.scan(sql)

        assertEquals(listOf(UPDATE_WARNING), warnings)
    }

    @Test
    fun `with update uses the main top level update clause`() {
        val missingWhere = SqlSafetyScanner.scan(
            "WITH cte AS (SELECT id FROM u WHERE flag = 1) UPDATE t SET a = 1;"
        )
        val withWhere = SqlSafetyScanner.scan(
            "WITH cte AS (SELECT id FROM u) UPDATE t SET a = 1 WHERE id = 2;"
        )

        assertEquals(listOf(UPDATE_WARNING), missingWhere)
        assertTrue(withWhere.isEmpty())
    }

    @Test
    fun `with delete is reported when the main delete has no where`() {
        val warnings = SqlSafetyScanner.scan(
            "WITH cte AS (SELECT id FROM u) DELETE FROM t;"
        )

        assertEquals(listOf(DELETE_WARNING), warnings)
    }

    @Test
    fun `lowercase dml keywords are detected`() {
        val warnings = SqlSafetyScanner.scan("update t set a = 1; delete from u where id = 1;")

        assertEquals(listOf(UPDATE_WARNING), warnings)
    }

    private companion object {
        const val UPDATE_WARNING = "检测到 UPDATE 语句缺少 WHERE，可能更新全表"
        const val DELETE_WARNING = "检测到 DELETE 语句缺少 WHERE，可能删除全表数据"
    }
}
