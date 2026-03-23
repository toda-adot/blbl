package blbl.cat3399.core.log

import android.content.Context
import android.net.Uri
import blbl.cat3399.core.io.DocumentExporter
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object LogExporter {
    const val ZIP_MIME = "application/zip"

    data class ZipExtra(
        /**
         * Path inside the zip, e.g. `meta.json` or `logs/extra.txt`.
         */
        val path: String,
        val bytes: ByteArray,
    )

    data class ExportResult(
        val fileName: String,
        val uri: Uri,
        val includedFiles: Int,
    )

    data class LocalExportResult(
        val fileName: String,
        val file: File,
        val includedFiles: Int,
    )

    fun exportToUri(
        context: Context,
        uri: Uri,
        nowMs: Long = System.currentTimeMillis(),
        fileNameOverride: String? = null,
        extras: List<ZipExtra> = emptyList(),
    ): ExportResult {
        val appContext = context.applicationContext
        val logDir = AppLog.logDir(appContext)
        val logFiles =
            logDir.listFiles()?.asSequence()
                ?.filter { it.isFile && it.name.endsWith(".log") }
                ?.sortedBy { it.lastModified() }
                ?.toList()
                ?: emptyList()

        val crashFile = CrashTracker.crashFile(appContext).takeIf { it.exists() && it.isFile }

        if (logFiles.isEmpty() && crashFile == null) {
            throw IOException("没有可导出的日志文件")
        }

        val fileName = resolveExportFileName(nowMs = nowMs, fileNameOverride = fileNameOverride)
        val result =
            DocumentExporter.exportToUri(
                context = appContext,
                uri = uri,
                fileName = fileName,
            ) { out ->
                ZipOutputStream(out).use { zip ->
                    writeZip(zip = zip, extras = extras, logFiles = logFiles, crashFile = crashFile)
                }
            }

        return ExportResult(fileName = result.fileName, uri = result.uri, includedFiles = result.value)
    }

    fun exportToLocalFile(
        context: Context,
        nowMs: Long = System.currentTimeMillis(),
        fileNameOverride: String? = null,
        extras: List<ZipExtra> = emptyList(),
    ): LocalExportResult {
        val appContext = context.applicationContext
        val logDir = AppLog.logDir(appContext)
        val logFiles =
            logDir.listFiles()?.asSequence()
                ?.filter { it.isFile && it.name.endsWith(".log") }
                ?.sortedBy { it.lastModified() }
                ?.toList()
                ?: emptyList()

        val crashFile = CrashTracker.crashFile(appContext).takeIf { it.exists() && it.isFile }

        if (logFiles.isEmpty() && crashFile == null) {
            throw IOException("没有可导出的日志文件")
        }

        val fileName = resolveExportFileName(nowMs = nowMs, fileNameOverride = fileNameOverride)
        val result =
            DocumentExporter.exportToLocalFile(
                context = appContext,
                fileName = fileName,
                subDir = "exports",
            ) { out ->
                ZipOutputStream(out).use { zip ->
                    writeZip(zip = zip, extras = extras, logFiles = logFiles, crashFile = crashFile)
                }
            }

        return LocalExportResult(fileName = result.fileName, file = result.file, includedFiles = result.value)
    }

    private fun buildExportFileName(nowMs: Long): String {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val ts = runCatching { sdf.format(Date(nowMs)) }.getOrNull() ?: nowMs.toString()
        return "blbl_logs_${ts}.zip"
    }

    private fun writeZip(
        zip: ZipOutputStream,
        extras: List<ZipExtra>,
        logFiles: List<File>,
        crashFile: File?,
    ): Int {
        var included = 0
        for (extra in extras) {
            val path = sanitizeZipPath(extra.path) ?: continue
            val bytes = extra.bytes
            zip.putNextEntry(ZipEntry(path))
            zip.write(bytes)
            zip.closeEntry()
            included++
        }
        for (f in logFiles) {
            if (!f.exists() || !f.isFile) continue
            zip.putNextEntry(ZipEntry("logs/${f.name}"))
            FileInputStream(f).use { input ->
                input.copyTo(zip, bufferSize = 32 * 1024)
            }
            zip.closeEntry()
            included++
        }
        if (crashFile != null) {
            zip.putNextEntry(ZipEntry("logs/${crashFile.name}"))
            FileInputStream(crashFile).use { input ->
                input.copyTo(zip, bufferSize = 32 * 1024)
            }
            zip.closeEntry()
            included++
        }
        return included
    }

    private fun resolveExportFileName(
        nowMs: Long,
        fileNameOverride: String?,
    ): String {
        val fallback = buildExportFileName(nowMs)
        val raw = fileNameOverride?.trim().takeIf { !it.isNullOrBlank() } ?: fallback
        val withExt = if (raw.endsWith(".zip", ignoreCase = true)) raw else "$raw.zip"
        val safe = sanitizeFileName(withExt)
        return safe.ifBlank { fallback }
    }

    fun suggestExportFileName(
        nowMs: Long = System.currentTimeMillis(),
        fileNameOverride: String? = null,
    ): String = resolveExportFileName(nowMs = nowMs, fileNameOverride = fileNameOverride)

    private fun sanitizeFileName(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return ""
        val noSeparators = trimmed.replace(Regex("[\\\\/\\r\\n\\t]"), "_")
        // Keep it reasonably short; some providers reject very long file names.
        return noSeparators.take(96)
    }

    private fun sanitizeZipPath(path: String): String? {
        val p = path.trim().replace('\\', '/')
        if (p.isBlank()) return null
        if (p.startsWith('/')) return null
        if (p.startsWith("../")) return null
        if (p.contains("/../")) return null
        if (p.contains('\u0000')) return null
        return p.take(256)
    }
}
