package com.bombless.luaandroid.ipc.v2.security

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.security.MessageDigest

data class VerifiedCallerIdentity(val uid: Int, val packageName: String, val signerSha256: List<String>)

interface PackageIdentitySource {
    fun packagesForUid(uid: Int): List<String>
    fun uidForPackage(packageName: String): Int
    fun currentSignerSha256(packageName: String): List<String>
}

class CallerIdentityException(message: String) : SecurityException(message)

/** Rejects ambiguous UIDs and trusts only the exact current signer set, never signing history. */
class CallerIdentityVerifier(private val source: PackageIdentitySource) {
    fun verify(uid: Int): VerifiedCallerIdentity {
        if (uid < 0) throw CallerIdentityException("Invalid Binder UID")
        val packages = source.packagesForUid(uid).distinct().sorted()
        if (packages.size != 1 || packages.single().isBlank()) {
            throw CallerIdentityException("Binder UID does not identify exactly one package")
        }
        val packageName = packages.single()
        if (source.uidForPackage(packageName) != uid) {
            throw CallerIdentityException("Package ownership does not match Binder UID")
        }
        val signers = source.currentSignerSha256(packageName).distinct().sorted()
        if (signers.isEmpty() || signers.any { !SHA256_FINGERPRINT.matches(it) }) {
            throw CallerIdentityException("Package signing identity is unavailable or invalid")
        }
        return VerifiedCallerIdentity(uid, packageName, signers)
    }

    companion object { private val SHA256_FINGERPRINT = Regex("[0-9a-f]{64}") }
}

/** API 26–27 use GET_SIGNATURES; API 28+ use SigningInfo.apkContentsSigners. */
class AndroidPackageIdentitySource(context: Context) : PackageIdentitySource {
    private val packageManager = context.applicationContext.packageManager
    override fun packagesForUid(uid: Int): List<String> = packageManager.getPackagesForUid(uid)?.toList().orEmpty()
    override fun uidForPackage(packageName: String): Int = packageManager.getPackageUid(packageName, 0)

    @Suppress("DEPRECATION")
    override fun currentSignerSha256(packageName: String): List<String> {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
        }
        val signatures: Array<out Signature> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Current signer set only; certificate history never silently inherits trust.
            info.signingInfo?.apkContentsSigners.orEmpty()
        } else info.signatures.orEmpty()
        return signatures.map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
                .joinToString("") { byte -> "%02x".format(byte) }
        }.distinct().sorted()
    }
}