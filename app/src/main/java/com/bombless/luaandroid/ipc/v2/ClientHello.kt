package com.bombless.luaandroid.ipc.v2
import android.os.Parcel
import android.os.Parcelable
class ClientHello() : Parcelable {
    var clientVersion=""; var buildId=""; var minProtocolMajor=2; var maxProtocolMajor=2
    var minProtocolMinor=0; var maxProtocolMinor=0; var requestedCapabilities= emptyArray<String>(); var nonce=java.util.UUID.randomUUID().toString()
    private constructor(p:Parcel):this(){ clientVersion=p.readString()?:""; buildId=p.readString()?:""; minProtocolMajor=p.readInt(); maxProtocolMajor=p.readInt(); minProtocolMinor=p.readInt(); maxProtocolMinor=p.readInt(); requestedCapabilities=p.createStringArray()?: emptyArray(); nonce=p.readString()?:"" }
    override fun writeToParcel(p:Parcel,f:Int){p.writeString(clientVersion);p.writeString(buildId);p.writeInt(minProtocolMajor);p.writeInt(maxProtocolMajor);p.writeInt(minProtocolMinor);p.writeInt(maxProtocolMinor);p.writeStringArray(requestedCapabilities);p.writeString(nonce)}
    override fun describeContents()=0
    companion object{@JvmField val CREATOR=object:Parcelable.Creator<ClientHello>{override fun createFromParcel(p:Parcel)=ClientHello(p);override fun newArray(n:Int)=arrayOfNulls<ClientHello>(n)}}
}