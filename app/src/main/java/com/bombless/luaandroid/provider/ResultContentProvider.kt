package com.bombless.luaandroid.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.bombless.luaandroid.runtime.JobManager
import java.io.File
import java.io.FileNotFoundException

class ResultContentProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        return true
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        if ("r" != mode) {
            throw SecurityException("Only read-only mode ('r') is supported by ResultContentProvider")
        }

        // URI format: content://com.bombless.luaandroid.provider.results/<jobId>/<artifactId>
        val pathSegments = uri.pathSegments
        if (pathSegments.size < 2) {
            throw FileNotFoundException("Invalid result URI: $uri")
        }

        val jobId = pathSegments[0]
        val artifactId = pathSegments[1]

        JobManager.validateFileName(jobId)
        JobManager.validateFileName(artifactId)

        val context = context ?: throw FileNotFoundException("Provider Context is null")
        val baseDir = File(context.filesDir, "lua")
        val jobDir = File(baseDir, "jobs/$jobId")

        val file = when (artifactId) {
            "stdout" -> File(jobDir, "stdout.txt").takeIf { it.exists() }
            "stderr" -> File(jobDir, "stderr.txt").takeIf { it.exists() }
            else -> File(File(jobDir, "input"), artifactId).takeIf { it.exists() }
        } ?: throw FileNotFoundException("Artifact file not found for URI $uri")

        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? {
        val path = uri.path ?: return null
        return when {
            path.endsWith(".txt") || path.endsWith("stdout") || path.endsWith("stderr") -> "text/plain"
            path.endsWith(".json") -> "application/json"
            path.endsWith(".png") -> "image/png"
            path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
            else -> "application/octet-stream"
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}
