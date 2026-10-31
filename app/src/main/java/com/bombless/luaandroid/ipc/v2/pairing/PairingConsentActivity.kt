package com.bombless.luaandroid.ipc.v2.pairing

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.content.pm.PackageManager
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView

/** Runtime-owned consent UI. Displayed identity and requested Scopes come from server-held state. */
class PairingConsentActivity : Activity() {
    companion object { const val EXTRA_REQUEST_ID = "pairing_request_id" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PairingRuntimeState.initialize(this)
        val id = intent.getStringExtra(EXTRA_REQUEST_ID)
        val pending = id?.let { PairingRuntimeState.challengeManager.pendingForConsent(it) }
        if (id == null || pending == null) {
            AlertDialog.Builder(this).setTitle("Pairing request expired")
                .setMessage("This request is no longer valid. No access was granted.")
                .setPositiveButton("OK") { _, _ -> finish() }
                .setOnCancelListener { finish() }.show()
            return
        }
        val packageName = pending.identity.packageName
        val appLabel = try {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (_: PackageManager.NameNotFoundException) { packageName }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 8)
        }
        content.addView(TextView(this).apply {
            text = "Application: $appLabel\nPackage: $packageName\nSigner SHA-256:\n" +
                pending.identity.signerSha256.joinToString("\n") + "\n\nRequested Scopes:"
            textSize = 14f
        })
        val checks = pending.requestedScopes.sorted().map { scope ->
            CheckBox(this).apply { text = scope; isChecked = false; content.addView(this) }
        }
        AlertDialog.Builder(this)
            .setTitle("Allow Runtime access?")
            .setView(content)
            .setPositiveButton("Approve selected") { _, _ ->
                val selected = checks.filter { it.isChecked }.map { it.text.toString() }.toSet()
                if (selected.isEmpty()) PairingRuntimeState.deny(id)
                else PairingRuntimeState.approve(id, selected)
                finish()
            }
            .setNegativeButton("Deny") { _, _ -> PairingRuntimeState.deny(id); finish() }
            .setOnCancelListener { PairingRuntimeState.deny(id); finish() }
            .show()
    }
}