package com.bombless.luaandroid.ipc.v2
import android.os.Parcel
import android.os.Parcelable
class ProtocolInfo() : Parcelable {
    var namespace = NAMESPACE; var protocolMajor = 2; var protocolMinor = 0
    var runtimeId = ""; var runtimeVersion = ""; var serviceVersion = "2.0.0"; var buildId = ""
    var engineId = "luaj"; var engineVersion = ""; var luaLanguageVersion = "5.1"
    var maxMetadataBytes = 65536; var maxTransferBytes = 16L * 1024 * 1024; var maxInputCount = 16
    var maxConcurrency = 4; var maxQueueDepth = 64; var maxExecutionMillis = 300000L
    var maxOutputBytes = 4L * 1024 * 1024; var maxEventBatchSize = 128
    private constructor(p: Parcel) : this() {
        namespace=p.readString()?:NAMESPACE; protocolMajor=p.readInt(); protocolMinor=p.readInt()
        runtimeId=p.readString()?:""; runtimeVersion=p.readString()?:""; serviceVersion=p.readString()?:""; buildId=p.readString()?:""
        engineId=p.readString()?:"luaj"; engineVersion=p.readString()?:""; luaLanguageVersion=p.readString()?:"5.1"
        maxMetadataBytes=p.readInt(); maxTransferBytes=p.readLong(); maxInputCount=p.readInt(); maxConcurrency=p.readInt()
        maxQueueDepth=p.readInt(); maxExecutionMillis=p.readLong(); maxOutputBytes=p.readLong(); maxEventBatchSize=p.readInt()
    }
    override fun writeToParcel(p: Parcel, f: Int) { p.writeString(namespace); p.writeInt(protocolMajor); p.writeInt(protocolMinor); p.writeString(runtimeId); p.writeString(runtimeVersion); p.writeString(serviceVersion); p.writeString(buildId); p.writeString(engineId); p.writeString(engineVersion); p.writeString(luaLanguageVersion); p.writeInt(maxMetadataBytes); p.writeLong(maxTransferBytes); p.writeInt(maxInputCount); p.writeInt(maxConcurrency); p.writeInt(maxQueueDepth); p.writeLong(maxExecutionMillis); p.writeLong(maxOutputBytes); p.writeInt(maxEventBatchSize) }
    override fun describeContents()=0
    companion object { const val NAMESPACE="com.bombless.luaandroid.ipc.v2"; @JvmField val CREATOR=object:Parcelable.Creator<ProtocolInfo>{ override fun createFromParcel(p:Parcel)=ProtocolInfo(p); override fun newArray(n:Int)=arrayOfNulls<ProtocolInfo>(n) } }
}