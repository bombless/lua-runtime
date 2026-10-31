package com.bombless.luaandroid.ipc.v2.pairing

import android.os.Parcel
import android.os.Parcelable

/** Contains only an opaque request handle and lifecycle state, never Runtime metadata. */
class PairingRequestResult() : Parcelable {
    var schemaVersion: Int = 1
    var requestId: String = ""
    var status: String = ""
    var expiresAtElapsedRealtime: Long = 0L
    private constructor(source: Parcel) : this() {
        schemaVersion = source.readInt(); requestId = source.readString() ?: ""
        status = source.readString() ?: ""; expiresAtElapsedRealtime = source.readLong()
    }
    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(schemaVersion); dest.writeString(requestId); dest.writeString(status)
        dest.writeLong(expiresAtElapsedRealtime)
    }
    override fun describeContents() = 0
    companion object {
        @JvmField val CREATOR = object : Parcelable.Creator<PairingRequestResult> {
            override fun createFromParcel(source: Parcel) = PairingRequestResult(source)
            override fun newArray(size: Int) = arrayOfNulls<PairingRequestResult>(size)
        }
    }
}