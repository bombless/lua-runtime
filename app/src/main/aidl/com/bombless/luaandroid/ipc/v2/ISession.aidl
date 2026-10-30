package com.bombless.luaandroid.ipc.v2;
import com.bombless.luaandroid.ipc.v2.SessionInfo;
import com.bombless.luaandroid.ipc.v2.CapabilityInfo;
interface ISession {
    SessionInfo getSessionInfo();
    List<CapabilityInfo> getCapabilities();
}