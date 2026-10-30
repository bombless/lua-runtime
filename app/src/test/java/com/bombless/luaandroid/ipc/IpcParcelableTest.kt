package com.bombless.luaandroid.ipc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class IpcParcelableTest {

    @Test
    fun testJobRequestProperties() {
        val request = JobRequest().apply {
            apiVersion = 1
            source = "print('hello')"
            scriptName = "test.lua"
            arguments = arrayOf("arg1", "arg2")
            timeoutMillis = 10000
        }

        assertEquals(1, request.apiVersion)
        assertEquals("print('hello')", request.source)
        assertEquals("test.lua", request.scriptName)
        assertEquals(2, request.arguments.size)
        assertEquals("arg1", request.arguments[0])
        assertEquals(10000L, request.timeoutMillis)
    }

    @Test
    fun testJobInfoProperties() {
        val artifact = ArtifactInfo("art1", "stdout.txt", "text/plain", 100L, "content://uri")
        val jobInfo = JobInfo("job-123", JobState.SUCCEEDED).apply {
            exitCode = 0
            returnValueJson = "\"success\""
            stdoutBytes = 100L
            artifacts = arrayOf(artifact)
        }

        assertEquals("job-123", jobInfo.jobId)
        assertEquals(JobState.SUCCEEDED, jobInfo.state)
        assertEquals(0, jobInfo.exitCode)
        assertEquals("\"success\"", jobInfo.returnValueJson)
        assertEquals(1, jobInfo.artifacts.size)
        assertEquals("art1", jobInfo.artifacts[0].artifactId)
    }

    @Test
    fun testInputFileProperties() {
        val inputFile = InputFile("script.lua", null)
        assertEquals("script.lua", inputFile.name)
    }
}
