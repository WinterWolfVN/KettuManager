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
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun downloadDiscordApk(version: String, out: File, onProgressUpdate: (Float?) -> Unit): DownloadResult =
        download("${prefs.mirror.baseUrl}/tracker/download/$version/base", out, onProgressUpdate)

    suspend fun downloadSplit(version: String, split: String, out: File, onProgressUpdate: (Float?) -> Unit): DownloadResult =
        download("${prefs.mirror.baseUrl}/tracker/download/$version/$split", out, onProgressUpdate)

    suspend fun downloadVendetta(out: File, onProgressUpdate: (Float?) -> Unit): DownloadResult =
        download(
            "https://github.com/C0C0B01/KettuXposed/releases/latest/download/app-release.apk",
            out,
            onProgressUpdate
        )

    suspend fun downloadUpdate(out: File): DownloadResult =
        download(
            "https://github.com/C0C0B01/KettuManager/releases/latest/download/Manager.apk",
            out
        ) {}

suspend fun download(url: String, out: File, onProgressUpdate: (Float?) -> Unit): DownloadResult = withContext(Dispatchers.IO) {
    if (out.exists()) out.delete()
    var retryCount = 0
    var finalResult: DownloadResult = DownloadResult.Error("TIMEOUT")

        while (retryCount < 3 && isActive) {
        try {
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                response.close()
                retryCount++
                delay(1000)
            } else {
                val body = response.body ?: throw Exception("Empty")
                val totalSize = body.contentLength()
                var downloaded = 0L
                var lastUpdateProgress = 0f

                out.sink().buffer().use { sink ->
                    val source = body.source()
                    val chunkSize = 2048 * 1024L
                    
                    while (isActive) {
                        val read = source.read(sink.buffer, chunkSize)
                        if (read == -1L) break
                        
                        sink.emitCompleteSegments()
                        downloaded += read
                        
                        if (totalSize > 0) {
                            val currentProgress = downloaded.toFloat() / totalSize.toFloat()
                            if (currentProgress - lastUpdateProgress >= 0.2f || downloaded == totalSize) {
                                onProgressUpdate(currentProgress)
                                lastUpdateProgress = currentProgress
                            }
                        }
                    }
                }

                if (totalSize > 0 && downloaded < totalSize) {
                    out.delete()
                    retryCount++
                } else {
                    return@withContext DownloadResult.Success
                }
            }
        } catch (e: CancellationException) {
            if (out.exists()) out.delete()
            return@withContext DownloadResult.Cancelled(false)
        } catch (e: Exception) {
            retryCount++
            if (out.exists()) out.delete()
            delay(1000)
            finalResult = DownloadResult.Error(e.message ?: "FAIL")
        }
    }

sealed interface DownloadResult {
    data object Success : DownloadResult
    data class Cancelled(val systemTriggered: Boolean) : DownloadResult
    data class Error(val debugReason: String) : DownloadResult
}
