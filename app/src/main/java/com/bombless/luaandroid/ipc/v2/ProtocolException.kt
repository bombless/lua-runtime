package com.bombless.luaandroid.ipc.v2

class ProtocolException(val domain: String, val code: String, message: String) : SecurityException("[$domain/$code] $message") {
    companion object {
        const val PROTOCOL_MAJOR_MISMATCH = "PROTOCOL_MAJOR_MISMATCH"
        const val UNSUPPORTED_CAPABILITY = "UNSUPPORTED_CAPABILITY"
        const val INVALID_MESSAGE = "INVALID_MESSAGE"
        const val PAIRING_REQUIRED = "PAIRING_REQUIRED"
        const val PAIRING_REVOKED = "PAIRING_REVOKED"
        const val SCOPE_DENIED = "SCOPE_DENIED"
        const val CALLER_IDENTITY_INVALID = "CALLER_IDENTITY_INVALID"
    }
}
