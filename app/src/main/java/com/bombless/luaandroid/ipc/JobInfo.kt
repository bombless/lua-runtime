package com.bombless.luaandroid.ipc

import android.os.Parcel
import android.os.Parcelable

class JobInfo : Parcelable {
    var jobId: String = ""
    var state: String = JobState.QUEUED
    var createdAtMillis: Long = 0L
    var startedAtMillis: Long = 0L
    var finishedAtMillis: Long = 0L
    var exitCode: Int? = null
    var returnValueJson: String? = null
    var stdoutBytes: Long = 0L
    var stderrBytes: Long = 0L
    var truncated: Boolean = false
    var errorCode: String? = null
    var errorMessage: String? = null
    var artifacts: Array<ArtifactInfo> = emptyArray()

    constructor()

    constructor(
        jobId: String,
        state: String,
        createdAtMillis: Long = System.currentTimeMillis()
    ) {
        this.jobId = jobId
        this.state = state
        this.createdAtMillis = createdAtMillis
    }

    private constructor(parcel: Parcel) {
        jobId = parcel.readString() ?: ""
        state = parcel.readString() ?: JobState.QUEUED
        createdAtMillis = parcel.readLong()
        startedAtMillis = parcel.readLong()
        finishedAtMillis = parcel.readLong()
        exitCode = if (parcel.readByte().toInt() != 0) parcel.readInt() else null
        returnValueJson = parcel.readString()
        stdoutBytes = parcel.readLong()
        stderrBytes = parcel.readLong()
        truncated = parcel.readByte().toInt() != 0
        errorCode = parcel.readString()
        errorMessage = parcel.readString()
        artifacts = parcel.createTypedArray(ArtifactInfo.CREATOR) ?: emptyArray()
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(jobId)
        dest.writeString(state)
        dest.writeLong(createdAtMillis)
        dest.writeLong(startedAtMillis)
        dest.writeLong(finishedAtMillis)
        if (exitCode != null) {
            dest.writeByte(1)
            dest.writeInt(exitCode!!)
        } else {
            dest.writeByte(0)
        }
        dest.writeString(returnValueJson)
        dest.writeLong(stdoutBytes)
        dest.writeLong(stderrBytes)
        dest.writeByte(if (truncated) 1 else 0)
        dest.writeString(errorCode)
        dest.writeString(errorMessage)
        dest.writeTypedArray(artifacts, flags)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<JobInfo> {
        override fun createFromParcel(parcel: Parcel): JobInfo {
            return JobInfo(parcel)
        }

        override fun newArray(size: Int): Array<JobInfo?> {
            return arrayOfNulls(size)
        }
    }
}
