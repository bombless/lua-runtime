package com.bombless.luaandroid.runtime

import android.os.RemoteCallbackList
import android.os.RemoteException
import com.bombless.luaandroid.ipc.ArtifactInfo
import com.bombless.luaandroid.ipc.ErrorCode
import com.bombless.luaandroid.ipc.IJobCallback
import com.bombless.luaandroid.ipc.InputFile
import com.bombless.luaandroid.ipc.JobInfo
import com.bombless.luaandroid.ipc.JobRequest
import com.bombless.luaandroid.ipc.JobState
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors

class JobManager(
    private val baseDir: File,
    private val luaExecutor: LuaExecutor = LuaExecutor()
) {
    private val jobMap = ConcurrentHashMap<String, JobInfo>()
    private val jobRequests = ConcurrentHashMap<String, JobRequest>()
    private val callbackSets = ConcurrentHashMap<String, CopyOnWriteArraySet<IJobCallback>>()
    private val runningFutures = ConcurrentHashMap<String, java.util.concurrent.Future<*>>()
    private val workerExecutor = Executors.newFixedThreadPool(4)

    init {
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
    }

    fun submit(request: JobRequest): String {
        val jobId = UUID.randomUUID().toString()
        val jobDir = File(baseDir, "jobs/$jobId")
        jobDir.mkdirs()

        val inputDir = File(jobDir, "input")
        inputDir.mkdirs()

        // Stage script if passed as FD or inline source
        var scriptContent = request.source ?: ""
        if (request.source.isNullOrEmpty() && request.scriptFd != null) {
            val scriptFile = File(jobDir, request.scriptName ?: "script.lua")
            FileInputStream(request.scriptFd!!.fileDescriptor).use { input ->
                FileOutputStream(scriptFile).use { output ->
                    input.copyTo(output)
                }
            }
            scriptContent = scriptFile.readText(Charsets.UTF_8)
            request.scriptFd?.close()
        }

        // Stage input files
        for (inputFile in request.inputFiles) {
            val filePfd = inputFile.pfd
            if (filePfd != null) {
                validateFileName(inputFile.name)
                val targetFile = File(inputDir, inputFile.name)
                FileInputStream(filePfd.fileDescriptor).use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                filePfd.close()
            }
        }

        val jobInfo = JobInfo(jobId = jobId, state = JobState.QUEUED)
        jobMap[jobId] = jobInfo
        jobRequests[jobId] = request

        // Schedule execution
        val future = workerExecutor.submit {
            runJob(jobId, scriptContent, request, jobDir)
        }
        runningFutures[jobId] = future

        return jobId
    }

    private fun runJob(jobId: String, scriptContent: String, request: JobRequest, jobDir: File) {
        val job = jobMap[jobId] ?: return

        synchronized(job) {
            if (job.state == JobState.CANCELLED) {
                return
            }
            job.state = JobState.RUNNING
            job.startedAtMillis = System.currentTimeMillis()
        }
        notifyJobChanged(job)

        val result = luaExecutor.execute(
            scriptContent = scriptContent,
            arguments = request.arguments,
            workingDir = File(jobDir, "input"),
            timeoutMillis = request.timeoutMillis
        )

        synchronized(job) {
            if (job.state == JobState.CANCELLED) {
                return
            }
            job.finishedAtMillis = System.currentTimeMillis()
            job.exitCode = result.exitCode
            job.returnValueJson = result.returnValueJson
            job.stdoutBytes = result.stdout.toByteArray(Charsets.UTF_8).size.toLong()
            job.stderrBytes = result.stderr.toByteArray(Charsets.UTF_8).size.toLong()
            job.truncated = result.truncated
            job.errorCode = result.errorCode
            job.errorMessage = result.errorMessage

            if (result.stdout.isNotEmpty()) {
                val stdoutFile = File(jobDir, "stdout.txt")
                stdoutFile.writeText(result.stdout, Charsets.UTF_8)
            }
            if (result.stderr.isNotEmpty()) {
                val stderrFile = File(jobDir, "stderr.txt")
                stderrFile.writeText(result.stderr, Charsets.UTF_8)
            }

            job.state = when (result.errorCode) {
                ErrorCode.EXECUTION_TIMEOUT -> JobState.TIMED_OUT
                null -> JobState.SUCCEEDED
                else -> JobState.FAILED
            }

            // Artifacts
            val artifacts = mutableListOf<ArtifactInfo>()
            val stdoutFile = File(jobDir, "stdout.txt")
            if (stdoutFile.exists()) {
                artifacts.add(
                    ArtifactInfo(
                        artifactId = "stdout",
                        displayName = "stdout.txt",
                        mimeType = "text/plain",
                        sizeBytes = stdoutFile.length()
                    )
                )
            }
            val stderrFile = File(jobDir, "stderr.txt")
            if (stderrFile.exists()) {
                artifacts.add(
                    ArtifactInfo(
                        artifactId = "stderr",
                        displayName = "stderr.txt",
                        mimeType = "text/plain",
                        sizeBytes = stderrFile.length()
                    )
                )
            }
            job.artifacts = artifacts.toTypedArray()
        }

        runningFutures.remove(jobId)
        notifyJobChanged(job)
    }

    fun getJob(jobId: String): JobInfo? {
        return jobMap[jobId]
    }

    fun listJobs(limit: Int, beforeCreatedAtMillis: Long): List<JobInfo> {
        var list = jobMap.values.toList()
        if (beforeCreatedAtMillis > 0) {
            list = list.filter { it.createdAtMillis < beforeCreatedAtMillis }
        }
        list = list.sortedByDescending { it.createdAtMillis }
        if (limit in 1..<list.size) {
            list = list.take(limit)
        }
        return list
    }

    fun cancel(jobId: String): Boolean {
        val job = jobMap[jobId] ?: return false

        synchronized(job) {
            if (job.state == JobState.SUCCEEDED || job.state == JobState.FAILED ||
                job.state == JobState.CANCELLED || job.state == JobState.TIMED_OUT
            ) {
                return false
            }
            job.state = JobState.CANCELLED
            job.finishedAtMillis = System.currentTimeMillis()
            job.errorCode = ErrorCode.EXECUTION_CANCELLED
            job.errorMessage = "Job was cancelled by client"
        }

        val future = runningFutures.remove(jobId)
        future?.cancel(true)

        notifyJobChanged(job)
        return true
    }

    fun registerCallback(jobId: String, callback: IJobCallback) {
        val set = callbackSets.computeIfAbsent(jobId) { CopyOnWriteArraySet() }
        set.add(callback)

        val job = jobMap[jobId]
        if (job != null) {
            try {
                callback.onJobChanged(job)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun unregisterCallback(jobId: String, callback: IJobCallback) {
        callbackSets[jobId]?.remove(callback)
    }

    private fun notifyJobChanged(job: JobInfo) {
        val set = callbackSets[job.jobId] ?: return
        for (cb in set) {
            try {
                cb.onJobChanged(job)
            } catch (e: Exception) {
                set.remove(cb)
            }
        }
    }

    fun getArtifactFile(jobId: String, artifactId: String): File? {
        val jobDir = File(baseDir, "jobs/$jobId")
        if (!jobDir.exists()) return null

        return when (artifactId) {
            "stdout" -> File(jobDir, "stdout.txt").takeIf { it.exists() }
            "stderr" -> File(jobDir, "stderr.txt").takeIf { it.exists() }
            else -> {
                validateFileName(artifactId)
                File(File(jobDir, "input"), artifactId).takeIf { it.exists() }
            }
        }
    }

    companion object {
        fun validateFileName(fileName: String) {
            if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\") || fileName.contains("\u0000")) {
                throw IllegalArgumentException("Invalid file name / path traversal detected: $fileName")
            }
        }
    }
}
