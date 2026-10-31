package com.bombless.luaandroid.ipc.v2

import android.os.Parcel
import android.os.Parcelable

/** Stable, safe-to-send protocol error. Empty optional strings mean absent. */
class ErrorInfo() : Parcelable {
    var domain = ""
    var code = ""
    var safeMessage = ""
    var retryable = false
    var fieldPath = ""
    var detailsJson = "{}"
    var retryAfterMillis = 0L
    var correlationId = ""

    private constructor(p: Parcel) : this() {
        domain = p.readString() ?: ""
        code = p.readString() ?: ""
        safeMessage = p.readString() ?: ""
        retryable = p.readInt() != 0
        fieldPath = p.readString() ?: ""
        detailsJson = p.readString() ?: "{}"
        retryAfterMillis = p.readLong()
        correlationId = p.readString() ?: ""
    }

    override fun writeToParcel(p: Parcel, flags: Int) {
        p.writeString(domain); p.writeString(code); p.writeString(safeMessage); p.writeInt(if (retryable) 1 else 0)
        p.writeString(fieldPath); p.writeString(detailsJson); p.writeLong(retryAfterMillis); p.writeString(correlationId)
    }
    override fun describeContents() = 0

    companion object {
        @JvmField val CREATOR = object : Parcelable.Creator<ErrorInfo> {
            override fun createFromParcel(p: Parcel) = ErrorInfo(p)
            override fun newArray(size: Int) = arrayOfNulls<ErrorInfo>(size)
        }
    }
}