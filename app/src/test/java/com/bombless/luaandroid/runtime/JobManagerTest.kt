package com.bombless.luaandroid.runtime

import com.bombless.luaandroid.ipc.IJobCallback
import com.bombless.luaandroid.ipc.JobInfo
import com.bombless.luaandroid.ipc.JobRequest
import com.bombless.luaandroid.ipc.JobState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class JobManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var baseDir: File
    private lateinit var jobManager: JobManager

    @Before
    fun setUp() {
        baseDir = tempFolder.newFolder("lua_test")
        jobManager = JobManager(baseDir)
    }

    @Test
    fun testSubmitAndExecuteJobSuccess() {
        val request = JobRequest().apply {
            source = "print('Hello JobManager'); return 42"
            timeoutMillis = 5_000
        }

        val jobId = jobManager.submit(request)
        assertNotNull(jobId)

        // Wait for completion
        var job: JobInfo? = null
        for (i in 0..50) {
            Thread.sleep(100)
            job = jobManager.getJob(jobId)
            if (job?.state == JobState.SUCCEEDED || job?.state == JobState.FAILED) {
                break
            }
        }

        assertNotNull(job)
        assertEquals(JobState.SUCCEEDED, job!!.state)
        assertEquals("42", job.returnValueJson)
        val stdoutFile = jobManager.getArtifactFile(jobId, "stdout")
        assertNotNull(stdoutFile)
        assertTrue(stdoutFile!!.readText().contains("Hello JobManager"))
    }

    @Test
    fun testCancelQueuedOrRunningJob() {
        val request = JobRequest().apply {
            source = "while true do end"
            timeoutMillis = 30_000
        }

        val jobId = jobManager.submit(request)
        Thread.sleep(50) // let it start running

        val cancelled = jobManager.cancel(jobId)
        assertTrue(cancelled)

        val job = jobManager.getJob(jobId)
        assertNotNull(job)
        assertEquals(JobState.CANCELLED, job!!.state)
    }

    @Test
    fun testListJobsWithLimit() {
        for (i in 1..5) {
            val req = JobRequest().apply {
                source = "return $i"
            }
            jobManager.submit(req)
        }
        Thread.sleep(500)

        val jobs = jobManager.listJobs(3, 0)
        assertEquals(3, jobs.size)
    }

    @Test
    fun testRegisterCallbackStateNotification() {
        val request = JobRequest().apply {
            source = "print('callback test')"
        }

        val latch = CountDownLatch(1)
        var receivedState: String? = null

        val callback = object : IJobCallback {
            override fun onJobChanged(info: JobInfo) {
                receivedState = info.state
                if (info.state == JobState.SUCCEEDED) {
                    latch.countDown()
                }
            }

            override fun asBinder(): android.os.IBinder {
                throw UnsupportedOperationException("Not required for unit test")
            }
        }

        val jobId = jobManager.submit(request)
        jobManager.registerCallback(jobId, callback)

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertNotNull(receivedState)
    }
}
