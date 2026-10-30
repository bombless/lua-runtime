package com.bombless.luaandroid.runtime

import org.junit.Assert.assertThrows
import org.junit.Test

class SecurityValidationTest {

    @Test
    fun testFileNameValidationRejectsTraversalPaths() {
        assertThrows(IllegalArgumentException::class.java) {
            JobManager.validateFileName("../etc/passwd")
        }

        assertThrows(IllegalArgumentException::class.java) {
            JobManager.validateFileName("foo/bar")
        }

        assertThrows(IllegalArgumentException::class.java) {
            JobManager.validateFileName("foo\\bar")
        }

        assertThrows(IllegalArgumentException::class.java) {
            JobManager.validateFileName("foo\u0000bar")
        }
    }

    @Test
    fun testValidFileNameAccepted() {
        JobManager.validateFileName("script.lua")
        JobManager.validateFileName("input_data.json")
        JobManager.validateFileName("stdout")
    }
}
