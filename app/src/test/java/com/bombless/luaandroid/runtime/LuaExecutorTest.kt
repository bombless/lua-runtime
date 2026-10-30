package com.bombless.luaandroid.runtime

import com.bombless.luaandroid.ipc.ErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.luaj.vm2.LuaValue

class LuaExecutorTest {

    private val executor = LuaExecutor()

    @Test
    fun testExecuteSimpleScriptAndReturnNumber() {
        val script = """
            local sum = 0
            for i = 1, 10 do
                sum = sum + i
            end
            print("sum:", sum)
            return sum
        """.trimIndent()

        val result = executor.execute(script)
        assertEquals(0, result.exitCode)
        assertEquals("55", result.returnValueJson)
        assertTrue(result.stdout.contains("sum:\t55"))
        assertNull(result.errorCode)
    }

    @Test
    fun testExecuteScriptWithArguments() {
        val script = """
            local name = arg[1]
            local age = arg[2]
            print("Hello " .. name .. ", age " .. age)
            return { name = name, age = tonumber(age) }
        """.trimIndent()

        val result = executor.execute(script, arrayOf("Alice", "30"))
        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("Hello Alice, age 30"))
        assertNotNull(result.returnValueJson)
        assertTrue(result.returnValueJson!!.contains("\"name\":\"Alice\""))
        assertTrue(result.returnValueJson!!.contains("\"age\":30"))
    }

    @Test
    fun testJsonConversionTypes() {
        val nilJson = executor.convertLuaToJson(LuaValue.NIL)
        assertNull(nilJson)

        val boolJson = executor.convertLuaToJson(LuaValue.TRUE)
        assertEquals("true", boolJson)

        val numJson = executor.convertLuaToJson(LuaValue.valueOf(42))
        assertEquals("42", numJson)

        val strJson = executor.convertLuaToJson(LuaValue.valueOf("hello\nworld"))
        assertEquals("\"hello\\nworld\"", strJson)
    }

    @Test
    fun testTimeoutHandling() {
        val script = "while true do end"
        val result = executor.execute(script, timeoutMillis = 500)
        assertEquals(124, result.exitCode)
        assertEquals(ErrorCode.EXECUTION_TIMEOUT, result.errorCode)
        assertNotNull(result.errorMessage)
    }

    @Test
    fun testRuntimeErrorHandling() {
        val script = "error('custom lua error')"
        val result = executor.execute(script)
        assertEquals(1, result.exitCode)
        assertEquals(ErrorCode.SCRIPT_RUNTIME_ERROR, result.errorCode)
        assertTrue(result.errorMessage!!.contains("custom lua error"))
    }
}
