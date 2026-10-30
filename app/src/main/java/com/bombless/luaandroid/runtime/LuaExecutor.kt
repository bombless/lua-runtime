package com.bombless.luaandroid.runtime

import com.bombless.luaandroid.ipc.ErrorCode
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.jse.JsePlatform
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class LuaExecutor(
    private val maxOutputBytes: Int = 100 * 1024 // 100 KB default cap
) {

    fun execute(
        scriptContent: String,
        arguments: Array<String> = emptyArray(),
        workingDir: File? = null,
        timeoutMillis: Long = 30_000L
    ): LuaExecutionResult {
        val executorService = Executors.newSingleThreadExecutor()
        val stdoutStream = ByteArrayOutputStream()
        val stderrStream = ByteArrayOutputStream()

        val future: Future<LuaExecutionResult> = executorService.submit(Callable {
            val globals: Globals = JsePlatform.standardGlobals()

            // Setup custom stdout / stderr
            val printStdout = PrintStream(stdoutStream, true, "UTF-8")
            val printStderr = PrintStream(stderrStream, true, "UTF-8")
            globals.STDOUT = printStdout
            globals.STDERR = printStderr

            // Setup arg array
            val argTable = LuaTable()
            argTable.set(0, LuaValue.valueOf("script.lua"))
            for (i in arguments.indices) {
                argTable.set(i + 1, LuaValue.valueOf(arguments[i]))
            }
            globals.set("arg", argTable)

            // Execute script
            var truncated = false
            try {
                val chunk = globals.load(scriptContent, "script.lua")
                val result: LuaValue = chunk.call()

                val stdoutStr = stdoutStream.toString("UTF-8")
                val stderrStr = stderrStream.toString("UTF-8")

                val cappedStdout = capOutput(stdoutStr)
                val cappedStderr = capOutput(stderrStr)
                if (stdoutStr.length > cappedStdout.length || stderrStr.length > cappedStderr.length) {
                    truncated = true
                }

                val jsonResult = convertLuaToJson(result)

                LuaExecutionResult(
                    exitCode = 0,
                    returnValueJson = jsonResult,
                    stdout = cappedStdout,
                    stderr = cappedStderr,
                    truncated = truncated
                )
            } catch (e: Exception) {
                val stdoutStr = capOutput(stdoutStream.toString("UTF-8"))
                val stderrStr = capOutput(stderrStream.toString("UTF-8"))
                LuaExecutionResult(
                    exitCode = 1,
                    returnValueJson = null,
                    stdout = stdoutStr,
                    stderr = stderrStr,
                    truncated = truncated,
                    errorCode = ErrorCode.SCRIPT_RUNTIME_ERROR,
                    errorMessage = e.message ?: e.toString()
                )
            }
        })

        return try {
            if (timeoutMillis > 0) {
                future.get(timeoutMillis, TimeUnit.MILLISECONDS)
            } else {
                future.get()
            }
        } catch (e: TimeoutException) {
            future.cancel(true)
            LuaExecutionResult(
                exitCode = 124,
                returnValueJson = null,
                stdout = capOutput(stdoutStream.toString("UTF-8")),
                stderr = capOutput(stderrStream.toString("UTF-8")),
                errorCode = ErrorCode.EXECUTION_TIMEOUT,
                errorMessage = "Execution timed out after $timeoutMillis ms"
            )
        } catch (e: Exception) {
            future.cancel(true)
            LuaExecutionResult(
                exitCode = 1,
                returnValueJson = null,
                stdout = capOutput(stdoutStream.toString("UTF-8")),
                stderr = capOutput(stderrStream.toString("UTF-8")),
                errorCode = ErrorCode.INTERNAL_ERROR,
                errorMessage = e.message ?: e.toString()
            )
        } finally {
            executorService.shutdownNow()
        }
    }

    private fun capOutput(output: String): String {
        return if (output.toByteArray(Charsets.UTF_8).size > maxOutputBytes) {
            output.substring(0, Math.min(output.length, maxOutputBytes))
        } else {
            output
        }
    }

    fun convertLuaToJson(value: LuaValue, visited: MutableSet<LuaValue> = mutableSetOf()): String? {
        if (value.isnil()) return null
        if (value.isboolean()) return value.toboolean().toString()
        if (value.isnumber()) return value.todouble().let {
            if (it % 1.0 == 0.0) it.toLong().toString() else it.toString()
        }
        if (value.isstring()) return escapeJsonString(value.tojstring())
        if (value.istable()) {
            val table = value.checktable()
            if (visited.contains(table)) {
                return "\"<cyclic reference>\""
            }
            visited.add(table)

            val isArray = isLuaArray(table)
            val sb = StringBuilder()
            if (isArray) {
                sb.append("[")
                val len = table.length()
                for (i in 1..len) {
                    val element = table.get(i)
                    sb.append(convertLuaToJson(element, visited) ?: "null")
                    if (i < len) sb.append(",")
                }
                sb.append("]")
            } else {
                sb.append("{")
                var first = true
                var k = LuaValue.NIL
                while (true) {
                    val keyVal = table.next(k)
                    k = keyVal.arg1()
                    if (k.isnil()) break
                    val v = keyVal.arg(2)

                    if (!first) sb.append(",")
                    first = false

                    val keyStr = escapeJsonString(k.tojstring())
                    val valStr = convertLuaToJson(v, visited) ?: "null"
                    sb.append(keyStr).append(":").append(valStr)
                }
                sb.append("}")
            }
            visited.remove(table)
            return sb.toString()
        }

        return escapeJsonString(value.tojstring())
    }

    private fun isLuaArray(table: LuaTable): Boolean {
        val len = table.length()
        if (len == 0) {
            var k = LuaValue.NIL
            val keyVal = table.next(k)
            return keyVal.arg1().isnil()
        }
        return true
    }

    private fun escapeJsonString(str: String): String {
        val sb = StringBuilder("\"")
        for (ch in str) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (ch.code < 0x20) {
                        sb.append(String.format("\\u%04x", ch.code))
                    } else {
                        sb.append(ch)
                    }
                }
            }
        }
        sb.append("\"")
        return sb.toString()
    }
}
