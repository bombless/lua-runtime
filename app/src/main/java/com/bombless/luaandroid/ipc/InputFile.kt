package com.bombless.luaandroid.ipc

import android.os.Parcel
import android.os.Parcelable
import android.os.ParcelFileDescriptor

class InputFile : Parcelable {
    var name: String = ""
    var pfd: ParcelFileDescriptor? = null

    constructor()

    constructor(name: String, pfd: ParcelFileDescriptor?) {
        this.name = name
        this.pfd = pfd
    }

    private constructor(parcel: Parcel) {
        name = parcel.readString() ?: ""
        pfd = parcel.readTypedObject(ParcelFileDescriptor.CREATOR)
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(name)
        dest.writeTypedObject(pfd, flags)
    }

    override fun describeContents(): Int {
        return pfd?.describeContents() ?: 0
    }

    companion object CREATOR : Parcelable.Creator<InputFile> {
        override fun createFromParcel(parcel: Parcel): InputFile {
            return InputFile(parcel)
        }

        override fun newArray(size: Int): Array<InputFile?> {
            return arrayOfNulls(size)
        }
    }
}
