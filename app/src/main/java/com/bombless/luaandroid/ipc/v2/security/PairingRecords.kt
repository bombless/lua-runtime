package com.bombless.luaandroid.ipc.v2.security

import android.util.AtomicFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.charset.CodingErrorAction

data class PairingRecord(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val packageName: String,
    val signerSha256: List<String>,
    val grantedScopes: Set<String>,
    val pairingVersion: Long,
    val createdAtEpochMillis: Long,
) {
    companion object { const val CURRENT_SCHEMA_VERSION = 1 }
}

class PairingStoreException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** Bounded deterministic binary codec; unknown versions and malformed state fail closed. */
object PairingRecordCodec {
    private const val MAGIC = 0x4c504149 // LPAI
    private const val FILE_VERSION = 1
    private const val MAX_RECORDS = 256
    private const val MAX_SIGNERS = 8
    private const val MAX_STRING_BYTES = 512
    private val digestPattern = Regex("[0-9a-f]{64}")

    fun encode(records: List<PairingRecord>): ByteArray {
        require(records.size <= MAX_RECORDS)
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(MAGIC); out.writeInt(FILE_VERSION); out.writeInt(records.size)
            records.sortedWith(compareBy<PairingRecord> { it.packageName }.thenBy { it.signerSha256.joinToString(":") })
                .forEach { record ->
                    validate(record)
                    out.writeInt(record.schemaVersion)
                    writeString(out, record.packageName)
                    val signers = record.signerSha256.sorted()
                    out.writeInt(signers.size); signers.forEach { writeString(out, it) }
                    val scopes = record.grantedScopes.sorted()
                    out.writeInt(scopes.size); scopes.forEach { writeString(out, it) }
                    out.writeLong(record.pairingVersion); out.writeLong(record.createdAtEpochMillis)
                }
        }
        return bytes.toByteArray()
    }

    fun decode(bytes: ByteArray): List<PairingRecord> {
        if (bytes.size > 1024 * 1024) throw PairingStoreException("Pairing store exceeds size limit")
        try {
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                if (input.readInt() != MAGIC) throw PairingStoreException("Invalid pairing store header")
                if (input.readInt() != FILE_VERSION) throw PairingStoreException("Unsupported pairing store schema")
                val count = input.readInt()
                if (count !in 0..MAX_RECORDS) throw PairingStoreException("Invalid pairing record count")
                val records = ArrayList<PairingRecord>(count)
                repeat(count) {
                    val version = input.readInt()
                    if (version != PairingRecord.CURRENT_SCHEMA_VERSION) throw PairingStoreException("Unsupported pairing record schema")
                    val packageName = readString(input)
                    val signerCount = input.readInt()
                    if (signerCount !in 1..MAX_SIGNERS) throw PairingStoreException("Invalid signer count")
                    val signers = List(signerCount) { readString(input) }
                    if (signers != signers.distinct().sorted()) throw PairingStoreException("Non-canonical signer set")
                    val scopeCount = input.readInt()
                    if (scopeCount !in 1..KNOWN_SCOPES.size) throw PairingStoreException("Invalid Scope count")
                    val scopeValues = List(scopeCount) { readString(input) }
                    if (scopeValues != scopeValues.distinct().sorted()) throw PairingStoreException("Non-canonical Scope set")
                    val scopes = scopeValues.toSet()
                    val record = PairingRecord(version, packageName, signers, scopes, input.readLong(), input.readLong())
                    validate(record); records += record
                }
                if (input.available() != 0) throw PairingStoreException("Unexpected trailing pairing data")
                return records
            }
        } catch (error: PairingStoreException) { throw error }
        catch (error: Exception) { throw PairingStoreException("Pairing store is corrupt", error) }
    }

    private fun validate(record: PairingRecord) {
        require(record.schemaVersion == PairingRecord.CURRENT_SCHEMA_VERSION)
        require(record.packageName.isNotBlank() && record.packageName.toByteArray(StandardCharsets.UTF_8).size <= MAX_STRING_BYTES)
        require(record.signerSha256.isNotEmpty() && record.signerSha256.size <= MAX_SIGNERS)
        require(record.signerSha256.all { digestPattern.matches(it) } && record.signerSha256.distinct().size == record.signerSha256.size)
        require(record.grantedScopes.isNotEmpty() && record.grantedScopes.size <= KNOWN_SCOPES.size)
        require(record.grantedScopes.all { it in KNOWN_SCOPES })
        require(record.pairingVersion > 0 && record.createdAtEpochMillis >= 0)
    }

    private fun writeString(out: DataOutputStream, value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size in 1..MAX_STRING_BYTES)
        out.writeInt(bytes.size); out.write(bytes)
    }
    private fun readString(input: DataInputStream): String {
        val size = input.readInt()
        if (size !in 1..MAX_STRING_BYTES) throw PairingStoreException("Invalid pairing string length")
        val bytes = ByteArray(size).also(input::readFully)
        return try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (error: Exception) {
            throw PairingStoreException("Invalid UTF-8 in pairing store", error)
        }
    }
}

/** AtomicFile-backed store. Read/write failures are surfaced and must deny authorization. */
class AtomicPairingStore(file: File) {
    private val atomicFile = AtomicFile(file)

    @Synchronized fun load(): List<PairingRecord> {
        if (!atomicFile.baseFile.exists() && !File(atomicFile.baseFile.path + ".bak").exists()) return emptyList()
        return try { atomicFile.openRead().use { PairingRecordCodec.decode(it.readBytes()) } }
        catch (error: Exception) { throw PairingStoreException("Unable to read pairing state; authorization is unavailable", error) }
    }

    @Synchronized fun replace(records: List<PairingRecord>) {
        val bytes = try { PairingRecordCodec.encode(records) }
        catch (error: Exception) { throw PairingStoreException("Invalid pairing state", error) }
        var stream: java.io.FileOutputStream? = null
        try {
            stream = atomicFile.startWrite(); stream.write(bytes); atomicFile.finishWrite(stream)
        } catch (error: Exception) {
            if (stream != null) atomicFile.failWrite(stream)
            throw PairingStoreException("Unable to persist pairing state", error)
        }
    }
}

val KNOWN_SCOPES: Set<String> = setOf(
    "runtime.read", "jobs.submit", "jobs.read", "jobs.cancel", "packages.read", "packages.install", "artifacts.read",
)