package dev.beefers.vendetta.manager.domain.manager

import android.app.DownloadManager as SystemDownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import androidx.core.content.getSystemService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.io.File

class DownloadManager(
    private val context: Context,
    private val prefs: PreferenceManager
) {

    suspend fun downloadDiscordApk(version: String, out: File, onProgressUpdate: (Float?) -> Unit): DownloadResult =
        download("${prefs.mirror.baseUrl}/tracker/download/$version/base", out, onProgressUpdate)

    suspend fun downloadSplit(version: String, split: String, out: File, onProgressUpdate: (Float?) -> Unit): DownloadResult =
        download("${prefs.mirror.baseUrl}/tracker/download/$version/$split", out, onProgressUpdate)

    suspend fun downloadVendetta(out: File, onProgressUpdate: (Float?) -> Unit) =
        download(
            "https://github.com/C0C0B01/KettuXposed/releases/latest/download/app-release.apk",
            out,
            onProgressUpdate
        )

    suspend fun downloadUpdate(out: File) =
        download(
            "https://github.com/C0C0B01/KettuManager/releases/latest/download/Manager.apk",
            out
        ) {}

    suspend fun download(
        url: String,
        out: File,
        onProgressUpdate: (Float?) -> Unit
    ): DownloadResult {
        val downloadManager = context.getSystemService<SystemDownloadManager>()
            ?: throw IllegalStateException("DownloadManager service is not available")

        val downloadId = SystemDownloadManager.Request(Uri.parse(url))
            .setTitle("Kettu Manager")
            .setDescription("Downloading ${out.name}...")
            .setDestinationUri(Uri.fromFile(out))
            .setNotificationVisibility(SystemDownloadManager.Request.VISIBILITY_VISIBLE)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .let(downloadManager::enqueue)

        while (true) {
            try {
                delay(100)
            } catch (_: CancellationException) {
                downloadManager.remove(downloadId)
                return DownloadResult.Cancelled(systemTriggered = false)
            }

            val cursor = SystemDownloadManager.Query()
                .setFilterById(downloadId)
                .let(downloadManager::query)

            if (!cursor.moveToFirst()) {
                cursor.close()
                return DownloadResult.Cancelled(systemTriggered = true)
            }

            val statusColumn = cursor.getColumnIndex(SystemDownloadManager.COLUMN_STATUS)
            val status = cursor.getInt(statusColumn)

            cursor.use {
                when (status) {
                    SystemDownloadManager.STATUS_PENDING, SystemDownloadManager.STATUS_PAUSED ->
                        onProgressUpdate(null)

                    SystemDownloadManager.STATUS_RUNNING ->
                        onProgressUpdate(getDownloadProgress(cursor))

                    SystemDownloadManager.STATUS_SUCCESSFUL ->
                        return DownloadResult.Success

                    SystemDownloadManager.STATUS_FAILED -> {
                        val reasonColumn = cursor.getColumnIndex(SystemDownloadManager.COLUMN_REASON)
                        val reason = cursor.getInt(reasonColumn)

                        return DownloadResult.Error(debugReason = convertErrorCode(reason))
                    }
                }
            }
        }
    }

    private fun getDownloadProgress(queryCursor: Cursor): Float? {
        val bytesColumn = queryCursor.getColumnIndex(SystemDownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
        val bytes = queryCursor.getLong(bytesColumn)

        val totalBytesColumn = queryCursor.getColumnIndex(SystemDownloadManager.COLUMN_TOTAL_SIZE_BYTES)
        val totalBytes = queryCursor.getLong(totalBytesColumn)

        if (totalBytes <= 0) return null
        return bytes.toFloat() / totalBytes
    }

    private fun convertErrorCode(code: Int) = when (code) {
        SystemDownloadManager.ERROR_UNKNOWN -> "UNKNOWN"
        SystemDownloadManager.ERROR_FILE_ERROR -> "FILE_ERROR"
        SystemDownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "UNHANDLED_HTTP_CODE"
        SystemDownloadManager.ERROR_HTTP_DATA_ERROR -> "HTTP_DATA_ERROR"
        SystemDownloadManager.ERROR_TOO_MANY_REDIRECTS -> "TOO_MANY_REDIRECTS"
        SystemDownloadManager.ERROR_INSUFFICIENT_SPACE -> "INSUFFICIENT_SPACE"
        SystemDownloadManager.ERROR_DEVICE_NOT_FOUND -> "DEVICE_NOT_FOUND"
        SystemDownloadManager.ERROR_CANNOT_RESUME -> "CANNOT_RESUME"
        SystemDownloadManager.ERROR_FILE_ALREADY_EXISTS -> "FILE_ALREADY_EXISTS"
        1010 -> "NETWORK_BLOCKED"
        else -> "UNKNOWN_CODE"
    }

}

sealed interface DownloadResult {
    data object Success : DownloadResult
    data class Cancelled(val systemTriggered: Boolean) : DownloadResult
    data class Error(val debugReason: String) : DownloadResult
}
