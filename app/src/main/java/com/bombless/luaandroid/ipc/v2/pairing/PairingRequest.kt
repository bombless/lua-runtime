package com.bombless.luaandroid.ipc.v2.pairing

import android.os.Parcel
import android.os.Parcelable

/** Separately versioned pairing input; never extends the frozen v2.0 ClientHello. */
class PairingRequest() : Parcelable {
    var schemaVersion: Int = 1
    var requestedScopes: Array<String> = emptyArray()
    private constructor(source: Parcel) : this() {
        schemaVersion = source.readInt()
        requestedScopes = source.createStringArray() ?: emptyArray()
    }
    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(schemaVersion); dest.writeStringArray(requestedScopes)
    }
    override fun describeContents() = 0
    companion object {
        @JvmField val CREATOR = object : Parcelable.Creator<PairingRequest> {
            override fun createFromParcel(source: Parcel) = PairingRequest(source)
            override fun newArray(size: Int) = arrayOfNulls<PairingRequest>(size)
        }
    }
}