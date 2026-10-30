package com.bombless.luaandroid.runtime

data class LuaExecutionResult(
    val exitCode: Int,
    val returnValueJson: String?,
    val stdout: String,
    val stderr: String,
    val truncated: Boolean = false,
    val errorCode: String? = null,
    val errorMessage: String? = null
)
