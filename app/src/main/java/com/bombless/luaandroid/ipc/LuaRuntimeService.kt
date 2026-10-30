package com.bombless.luaandroid.ipc

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Process
import com.bombless.luaandroid.runtime.JobManager
import java.io.File
import java.io.FileNotFoundException

class LuaRuntimeService : Service() {

    private lateinit var jobManager: JobManager

    override fun onCreate() {
        super.onCreate()
        val luaBaseDir = File(filesDir, "lua")
        jobManager = JobManager(luaBaseDir)
    }

    private val binder = object : ILuaRuntimeService.Stub() {
        override fun getApiVersion(): Int {
            return 1
        }

        override fun getRuntimeInfo(): String {
            return "Lua 5.1 (Luaj engine); API version 1"
        }

        override fun submit(request: JobRequest): String {
            verifyCallerPermission()
            validateJobRequest(request)
            return jobManager.submit(request)
        }

        override fun getJob(jobId: String): JobInfo? {
            verifyCallerPermission()
            JobManager.validateFileName(jobId)
            return jobManager.getJob(jobId)
        }

        override fun listJobs(limit: Int, beforeCreatedAtMillis: Long): List<JobInfo> {
            verifyCallerPermission()
            return jobManager.listJobs(limit, beforeCreatedAtMillis)
        }

        override fun cancel(jobId: String): Boolean {
            verifyCallerPermission()
            JobManager.validateFileName(jobId)
            return jobManager.cancel(jobId)
        }

        override fun registerCallback(jobId: String, callback: IJobCallback) {
            verifyCallerPermission()
            JobManager.validateFileName(jobId)
            jobManager.registerCallback(jobId, callback)
        }

        override fun unregisterCallback(jobId: String, callback: IJobCallback) {
            verifyCallerPermission()
            JobManager.validateFileName(jobId)
            jobManager.unregisterCallback(jobId, callback)
        }

        override fun openResult(jobId: String, artifactId: String): ParcelFileDescriptor {
            verifyCallerPermission()
            JobManager.validateFileName(jobId)
            JobManager.validateFileName(artifactId)

            val file = jobManager.getArtifactFile(jobId, artifactId)
                ?: throw FileNotFoundException("Artifact not found: $artifactId for job $jobId")

            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    private fun verifyCallerPermission() {
        val callingUid = Binder.getCallingUid()
        if (callingUid == Process.myUid()) {
            return
        }
        val permissionResult = checkCallingPermission("com.bombless.luaandroid.permission.USE_LUA_RUNTIME")
        if (permissionResult != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("Caller UID $callingUid missing required permission com.bombless.luaandroid.permission.USE_LUA_RUNTIME")
        }
    }

    private fun validateJobRequest(request: JobRequest) {
        if (!request.scriptName.isNullOrEmpty()) {
            JobManager.validateFileName(request.scriptName!!)
        }
        if (!request.workingDirectory.isNullOrEmpty()) {
            JobManager.validateFileName(request.workingDirectory!!)
        }
        for (input in request.inputFiles) {
            JobManager.validateFileName(input.name)
        }
        if (request.source != null && request.source!!.length > 5 * 1024 * 1024) {
            throw IllegalArgumentException("Inline script source exceeds maximum allowed limit (5MB)")
        }
    }
}
