package com.bombless.luaandroid.ipc.v2

import android.os.Parcel
import android.os.Parcelable

class CapabilityInfo() : Parcelable {
    var id = ""
    var schemaVersion = 1
    var limitsJson = "{}"

    constructor(id: String, version: Int = 1, limitsJson: String = "{}") : this() {
        this.id = id
        this.schemaVersion = version
        this.limitsJson = limitsJson
    }

    private constructor(p: Parcel) : this() {
        id = p.readString() ?: ""
        schemaVersion = p.readInt()
        limitsJson = p.readString() ?: "{}"
    }

    override fun writeToParcel(p: Parcel, flags: Int) {
        p.writeString(id); p.writeInt(schemaVersion); p.writeString(limitsJson)
    }
    override fun describeContents() = 0

    companion object {
        @JvmField val CREATOR = object : Parcelable.Creator<CapabilityInfo> {
            override fun createFromParcel(p: Parcel) = CapabilityInfo(p)
            override fun newArray(size: Int) = arrayOfNulls<CapabilityInfo>(size)
        }
    }
}