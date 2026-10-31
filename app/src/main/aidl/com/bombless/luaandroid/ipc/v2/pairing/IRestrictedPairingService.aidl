package com.bombless.luaandroid.ipc.v2.pairing;
import com.bombless.luaandroid.ipc.v2.pairing.PairingRequest;
import com.bombless.luaandroid.ipc.v2.pairing.PairingRequestResult;
interface IRestrictedPairingService {
    PairingRequestResult submitPairingRequest(in PairingRequest request);
    PairingRequestResult getPairingRequestStatus(String requestId);
    boolean cancelPairingRequest(String requestId);
}