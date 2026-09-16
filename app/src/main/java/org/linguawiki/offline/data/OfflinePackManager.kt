package org.linguawiki.offline.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.StatFs
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

class OfflinePackManager(private val context: Context) {
    private val packDirectory = File(context.filesDir, "dictionary-packs").apply { mkdirs() }
    private val downloadDirectory = File(context.filesDir, "dictionary-downloads").apply { mkdirs() }

    val catalog: List<OfflinePackDescriptor> by lazy { loadCatalog() }

    fun initialStates(): List<OfflinePackState> = catalog.map { descriptor ->
        OfflinePackState(
            descriptor = descriptor,
            installed = installedFile(descriptor).isFile,
            freeBytes = availableBytes(),
        )
    }

    fun openDatabase(language: String): OfflinePackDatabase? {
        val descriptor = catalog.firstOrNull {
            it.headwordLanguage == language && installedFile(it).isFile
        } ?: return null
        return runCatching { OfflinePackDatabase(installedFile(descriptor), descriptor) }.getOrNull()
    }

    fun descriptorForLanguage(language: String): OfflinePackDescriptor? = catalog.firstOrNull {
        it.headwordLanguage == language
    }

    fun isInstalled(descriptor: OfflinePackDescriptor): Boolean = installedFile(descriptor).isFile

    fun availableBytes(): Long = StatFs(context.filesDir.absolutePath).availableBytes

    fun requiredPeakBytes(descriptor: OfflinePackDescriptor): Long =
        descriptor.downloadBytes + descriptor.installedBytes + SAFETY_MARGIN_BYTES

    fun install(descriptor: OfflinePackDescriptor, onProgress: (Float) -> Unit) {
        ensure(descriptor.url.startsWith("https://"), OfflinePackError.INVALID_PACKAGE)
        ensure(availableBytes() >= requiredPeakBytes(descriptor), OfflinePackError.NOT_ENOUGH_SPACE)

        val partial = File(downloadDirectory, "${descriptor.packId}-${descriptor.version}.sqlite.gz.part")
        val temporary = File(packDirectory, ".${descriptor.packId}-${descriptor.version}.sqlite.tmp")
        try {
            download(descriptor, partial, onProgress)
            ensure(partial.length() == descriptor.downloadBytes, OfflinePackError.INVALID_PACKAGE)
            ensure(
                sha256(partial).equals(descriptor.sha256, ignoreCase = true),
                OfflinePackError.INVALID_PACKAGE,
            )
            if (temporary.exists()) temporary.delete()
            try {
                GZIPInputStream(partial.inputStream().buffered()).use { input ->
                    FileOutputStream(temporary).buffered().use { output ->
                        input.copyTo(output, 1024 * 1024)
                    }
                }
            } catch (error: IOException) {
                throw OfflinePackException(OfflinePackError.INVALID_PACKAGE, error)
            }
            ensure(temporary.length() == descriptor.installedBytes, OfflinePackError.INVALID_PACKAGE)
            validateDatabase(temporary, descriptor)
            activate(temporary, installedFile(descriptor))
            partial.delete()
            onProgress(1f)
        } catch (error: Throwable) {
            temporary.delete()
            throw error
        }
    }

    fun remove(descriptor: OfflinePackDescriptor): Boolean {
        val installed = installedFile(descriptor)
        val partial = File(downloadDirectory, "${descriptor.packId}-${descriptor.version}.sqlite.gz.part")
        partial.delete()
        return !installed.exists() || installed.delete()
    }

    private fun download(
        descriptor: OfflinePackDescriptor,
        destination: File,
        onProgress: (Float) -> Unit,
    ) {
        var existing = destination.length().coerceAtMost(descriptor.downloadBytes)
        if (destination.length() > descriptor.downloadBytes) {
            destination.delete()
            existing = 0
        }
        if (existing == descriptor.downloadBytes) {
            onProgress(1f)
            return
        }

        val connection = try {
            URL(descriptor.url).openConnection() as HttpURLConnection
        } catch (error: IOException) {
            throw OfflinePackException(OfflinePackError.NETWORK, error)
        }
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", USER_AGENT)
            if (existing > 0) connection.setRequestProperty("Range", "bytes=$existing-")

            val response = connection.responseCode
            val append = existing > 0 && response == HttpURLConnection.HTTP_PARTIAL
            if (response !in 200..299) throw OfflinePackException(OfflinePackError.NETWORK)
            ensure(connection.url.protocol == "https", OfflinePackError.INVALID_PACKAGE)
            if (!append) existing = 0

            connection.inputStream.buffered().use { input ->
                FileOutputStream(destination, append).buffered().use { output ->
                    val buffer = ByteArray(128 * 1024)
                    var total = existing
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        total += read
                        ensure(total <= descriptor.downloadBytes, OfflinePackError.INVALID_PACKAGE)
                        onProgress(total.toFloat() / descriptor.downloadBytes.toFloat())
                    }
                }
            }
        } catch (error: OfflinePackException) {
            throw error
        } catch (error: IOException) {
            throw OfflinePackException(OfflinePackError.NETWORK, error)
        } finally {
            connection.disconnect()
        }
    }

    private fun validateDatabase(file: File, descriptor: OfflinePackDescriptor) {
        val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        try {
            val metadata = mutableMapOf<String, String>()
            db.rawQuery(
                "SELECT key, value FROM meta WHERE key IN ('schema_version', 'pack_id', 'version', 'edition', 'language')",
                null,
            ).use { cursor ->
                while (cursor.moveToNext()) metadata[cursor.getString(0)] = cursor.getString(1)
            }
            ensure(metadata["schema_version"] == SUPPORTED_SCHEMA.toString(), OfflinePackError.INVALID_PACKAGE)
            ensure(metadata["pack_id"] == descriptor.packId, OfflinePackError.INVALID_PACKAGE)
            ensure(metadata["version"] == descriptor.version, OfflinePackError.INVALID_PACKAGE)
            ensure(metadata["edition"] == descriptor.primaryEdition, OfflinePackError.INVALID_PACKAGE)
            ensure(metadata["language"] == descriptor.headwordLanguage, OfflinePackError.INVALID_PACKAGE)
            val integrity = db.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else "failed"
            }
            ensure(integrity == "ok", OfflinePackError.INVALID_PACKAGE)
        } finally {
            db.close()
        }
    }

    private fun activate(temporary: File, installed: File) {
        val backup = File(installed.parentFile, ".${installed.name}.backup")
        backup.delete()
        if (installed.exists()) ensure(installed.renameTo(backup), OfflinePackError.INSTALLATION)
        try {
            ensure(temporary.renameTo(installed), OfflinePackError.INSTALLATION)
            backup.delete()
        } catch (error: Throwable) {
            if (!installed.exists() && backup.exists()) backup.renameTo(installed)
            throw error
        }
    }

    private fun installedFile(descriptor: OfflinePackDescriptor) =
        File(packDirectory, "${descriptor.packId}.sqlite")

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun ensure(condition: Boolean, reason: OfflinePackError) {
        if (!condition) throw OfflinePackException(reason)
    }

    private fun loadCatalog(): List<OfflinePackDescriptor> {
        val root = JSONObject(
            context.assets.open("offline_packs.json").bufferedReader().use { it.readText() },
        )
        val packages = root.getJSONArray("packages")
        return buildList {
            for (index in 0 until packages.length()) {
                val item = packages.getJSONObject(index)
                val licensesArray = item.optJSONArray("licenses")
                val licenses = buildList {
                    if (licensesArray != null) {
                        for (licenseIndex in 0 until licensesArray.length()) {
                            licensesArray.optString(licenseIndex).takeIf { it.isNotBlank() }?.let(::add)
                        }
                    }
                }
                add(
                    OfflinePackDescriptor(
                        packId = item.getString("packId"),
                        version = item.getString("version"),
                        headwordLanguage = item.getString("headwordLanguage"),
                        primaryEdition = item.getString("primaryEdition"),
                        displayName = item.getString("displayName"),
                        downloadBytes = item.getLong("downloadBytes"),
                        installedBytes = item.getLong("installedBytes"),
                        entryCount = item.getLong("entryCount"),
                        senseCount = item.getLong("senseCount"),
                        translationCount = item.getLong("translationCount"),
                        formCount = item.getLong("formCount"),
                        sha256 = item.getString("sha256"),
                        url = item.getString("url"),
                        licenses = licenses,
                    ),
                )
            }
        }
    }

    companion object {
        private const val SUPPORTED_SCHEMA = 1
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val SAFETY_MARGIN_BYTES = 16L * 1024L * 1024L
        private const val USER_AGENT = "LinguaWiki/0.4 (Android offline dictionary)"
    }
}
