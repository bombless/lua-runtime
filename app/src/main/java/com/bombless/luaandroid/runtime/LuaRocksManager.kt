package com.bombless.luaandroid.runtime

import com.bombless.luaandroid.ipc.ErrorCode
import java.io.File

data class RockInfo(
    val name: String,
    val version: String,
    val isNative: Boolean = false,
    val installedPath: String
)

class LuaRocksManager(
    private val rocksTreeDir: File
) {
    init {
        if (!rocksTreeDir.exists()) {
            rocksTreeDir.mkdirs()
        }
    }

    fun getLuaPath(): String {
        val shareDir = File(rocksTreeDir, "share/lua/5.1")
        return "${shareDir.absolutePath}/?.lua;${shareDir.absolutePath}/?/init.lua"
    }

    fun getLuaCPath(): String {
        val libDir = File(rocksTreeDir, "lib/lua/5.1")
        return "${libDir.absolutePath}/?.so"
    }

    fun listInstalledRocks(): List<RockInfo> {
        val rocksManifest = File(rocksTreeDir, "lib/luarocks/rocks/manifest")
        if (!rocksManifest.exists()) {
            val rockDirs = File(rocksTreeDir, "share/lua/5.1").listFiles() ?: return emptyList()
            return rockDirs.filter { it.isDirectory }.map {
                RockInfo(
                    name = it.name,
                    version = "1.0.0",
                    isNative = false,
                    installedPath = it.absolutePath
                )
            }
        }
        val rocks = mutableListOf<RockInfo>()
        rocksManifest.forEachLine { line ->
            if (line.trim().startsWith("[\"") && line.contains(" = ")) {
                val name = line.substringAfter("[\"").substringBefore("\"]")
                rocks.add(
                    RockInfo(
                        name = name,
                        version = "1.0.0",
                        isNative = false,
                        installedPath = File(rocksTreeDir, "share/lua/5.1/$name").absolutePath
                    )
                )
            }
        }
        return rocks
    }

    fun installPureLuaRock(name: String, version: String, luaCodeMap: Map<String, String>) {
        val rockShareDir = File(rocksTreeDir, "share/lua/5.1/$name")
        rockShareDir.mkdirs()
        for ((fileName, code) in luaCodeMap) {
            val file = File(rockShareDir, fileName)
            file.writeText(code, Charsets.UTF_8)
        }
    }

    fun validateNativeRockSupport(isNative: Boolean) {
        if (isNative) {
            throw UnsupportedOperationException("${ErrorCode.NATIVE_ROCK_UNSUPPORTED}: Native C-module rocks are not supported on this device architecture without prebuilt Android binaries.")
        }
    }
}
