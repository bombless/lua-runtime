package com.bombless.luaandroid.ipc.v2;
import com.bombless.luaandroid.ipc.v2.ProtocolInfo;
import com.bombless.luaandroid.ipc.v2.ClientHello;
import com.bombless.luaandroid.ipc.v2.ISession;
interface IProtocolBootstrap {
    ProtocolInfo getProtocolInfo();
    ISession openSession(in ClientHello hello);
}