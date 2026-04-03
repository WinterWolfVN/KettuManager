package dev.beefers.vendetta.manager.domain.manager

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class DownloadManager(
    private val context: Context,
    private val prefs: PreferenceManager
) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

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
    ): DownloadResult = withContext(Dispatchers.IO) {
        try {
            onProgressUpdate(null)

            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext DownloadResult.Error("HTTP_ERROR_${response.code}")
            }

            val body = response.body ?: return@withContext DownloadResult.Error("EMPTY_BODY")
            val totalBytes = body.contentLength()
            var downloadedBytes = 0L
            var lastUpdateTime = System.currentTimeMillis()

            body.byteStream().use { input ->
                out.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytes = input.read(buffer)
                    while (bytes >= 0) {
                        if (!isActive) {
                            out.delete()
                            return@withContext DownloadResult.Cancelled(false)
                        }

                        output.write(buffer, 0, bytes)
                        downloadedBytes += bytes

                        if (totalBytes > 0) {
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastUpdateTime > 60 || downloadedBytes == totalBytes) {
                                onProgressUpdate(downloadedBytes.toFloat() / totalBytes.toFloat())
                                lastUpdateTime = currentTime
                            }
                        }

                        bytes = input.read(buffer)
                    }
                }
            }
            DownloadResult.Success
        } catch (e: CancellationException) {
            out.delete()
            DownloadResult.Cancelled(systemTriggered = false)
        } catch (e: Exception) {
            out.delete()
            DownloadResult.Error(e.message ?: "UNKNOWN_ERROR")
        }
    }
}

sealed interface DownloadResult {
    data object Success : DownloadResult
    data class Cancelled(val systemTriggered: Boolean) : DownloadResult
    data class Error(val debugReason: String) : DownloadResult
}
