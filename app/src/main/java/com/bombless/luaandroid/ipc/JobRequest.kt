package com.bombless.luaandroid.ipc

import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.os.ParcelFileDescriptor

class JobRequest : Parcelable {
    var apiVersion: Int = 1
    var source: String? = null
    var scriptFd: ParcelFileDescriptor? = null
    var scriptName: String? = null
    var arguments: Array<String> = emptyArray()
    var environment: Bundle = Bundle()
    var workingDirectory: String? = null
    var timeoutMillis: Long = 30_000L
    var memoryLimitBytes: Long = 0L
    var inputFiles: Array<InputFile> = emptyArray()
    var flags: Int = 0

    constructor()

    private constructor(parcel: Parcel) {
        apiVersion = parcel.readInt()
        source = parcel.readString()
        scriptFd = parcel.readTypedObject(ParcelFileDescriptor.CREATOR)
        scriptName = parcel.readString()
        arguments = parcel.createStringArray() ?: emptyArray()
        environment = parcel.readBundle(JobRequest::class.java.classLoader) ?: Bundle()
        workingDirectory = parcel.readString()
        timeoutMillis = parcel.readLong()
        memoryLimitBytes = parcel.readLong()
        inputFiles = parcel.createTypedArray(InputFile.CREATOR) ?: emptyArray()
        flags = parcel.readInt()
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(apiVersion)
        dest.writeString(source)
        dest.writeTypedObject(scriptFd, flags)
        dest.writeString(scriptName)
        dest.writeStringArray(arguments)
        dest.writeBundle(environment)
        dest.writeString(workingDirectory)
        dest.writeLong(timeoutMillis)
        dest.writeLong(memoryLimitBytes)
        dest.writeTypedArray(inputFiles, flags)
        dest.writeInt(this.flags)
    }

    override fun describeContents(): Int {
        var contents = 0
        if (scriptFd != null) contents = contents or scriptFd!!.describeContents()
        for (input in inputFiles) {
            contents = contents or input.describeContents()
        }
        return contents
    }

    companion object CREATOR : Parcelable.Creator<JobRequest> {
        override fun createFromParcel(parcel: Parcel): JobRequest {
            return JobRequest(parcel)
        }

        override fun newArray(size: Int): Array<JobRequest?> {
            return arrayOfNulls(size)
        }
    }
}
