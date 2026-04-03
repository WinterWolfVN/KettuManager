package dev.beefers.vendetta.manager.domain.manager

import android.content.Context
import kotlinx.coroutines.*
import okhttp3.*
import okio.buffer
import okio.sink
import java.io.File
import java.util.concurrent.TimeUnit

class DownloadManager(
    private val context: Context,
    private val prefs: PreferenceManager
) {

    private val httpClient = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(15, 5, TimeUnit.MINUTES))
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
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

    private suspend fun download(
        url: String,
        out: File,
        onProgressUpdate: (Float?) -> Unit
    ): DownloadResult = withContext(Dispatchers.IO) {
        var trial = 0
        while (trial < 3) {
            try {
                onProgressUpdate(null)
                val request = Request.Builder()
                    .url(url)
                    .header("Connection", "keep-alive")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        trial++
                        if (trial == 3) return@withContext DownloadResult.Error("${response.code}")
                        delay(1000)
                        return@continue
                    }

                    val body = response.body ?: return@withContext DownloadResult.Error("EMPTY")
                    val totalBytes = body.contentLength()
                    var downloaded = 0L

                    out.sink().buffer().use { sink ->
                        val source = body.source()
                        val bufferSize = 64 * 1024L
                        while (isActive) {
                            val read = source.read(sink.buffer, bufferSize)
                            if (read == -1L) break
                            sink.emitCompleteSegments()
                            downloaded += read
                            if (totalBytes > 0) {
                                onProgressUpdate(downloaded.toFloat() / totalBytes.toFloat())
                            }
                        }
                    }
                    return@withContext DownloadResult.Success
                }
            } catch (e: Exception) {
                trial++
                if (trial == 3) return@withContext DownloadResult.Error(e.message ?: "ERR")
                delay(1000)
            }
        }
        DownloadResult.Error("TIMEOUT")
    }
}

sealed interface DownloadResult {
    data object Success : DownloadResult
    data class Cancelled(val systemTriggered: Boolean) : DownloadResult
    data class Error(val debugReason: String) : DownloadResult
}
