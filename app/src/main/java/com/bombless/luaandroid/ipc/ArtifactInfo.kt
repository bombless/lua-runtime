package com.bombless.luaandroid.ipc

import android.os.Parcel
import android.os.Parcelable

class ArtifactInfo : Parcelable {
    var artifactId: String = ""
    var displayName: String = ""
    var mimeType: String = ""
    var sizeBytes: Long = 0L
    var contentUri: String? = null

    constructor()

    constructor(
        artifactId: String,
        displayName: String,
        mimeType: String,
        sizeBytes: Long,
        contentUri: String? = null
    ) {
        this.artifactId = artifactId
        this.displayName = displayName
        this.mimeType = mimeType
        this.sizeBytes = sizeBytes
        this.contentUri = contentUri
    }

    private constructor(parcel: Parcel) {
        artifactId = parcel.readString() ?: ""
        displayName = parcel.readString() ?: ""
        mimeType = parcel.readString() ?: ""
        sizeBytes = parcel.readLong()
        contentUri = parcel.readString()
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(artifactId)
        dest.writeString(displayName)
        dest.writeString(mimeType)
        dest.writeLong(sizeBytes)
        dest.writeString(contentUri)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<ArtifactInfo> {
        override fun createFromParcel(parcel: Parcel): ArtifactInfo {
            return ArtifactInfo(parcel)
        }

        override fun newArray(size: Int): Array<ArtifactInfo?> {
            return arrayOfNulls(size)
        }
    }
}
