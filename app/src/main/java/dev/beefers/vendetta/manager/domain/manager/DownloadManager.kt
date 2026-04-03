package dev.beefers.vendetta.manager.domain.manager

import android.content.Context
import kotlinx.coroutines.*
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okio.buffer
import okio.sink
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class DownloadManager(
    private val context: Context,
    private val prefs: PreferenceManager
) {

    private val httpClient = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
        .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
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
            val headRequest = Request.Builder().url(url).head().build()
            val headResponse = httpClient.newCall(headRequest).execute()
            val totalBytes = headResponse.body?.contentLength() ?: -1L
            
            if (totalBytes <= 0 || headResponse.header("Accept-Ranges") == null) {
                return@withContext downloadModernSingle(url, out, onProgressUpdate)
            }

            val numThreads = 4
            val chunkSize = totalBytes / numThreads
            val downloadedBytes = AtomicLong(0)
            val deferreds = mutableListOf<Deferred<Unit>>()

            RandomAccessFile(out, "rw").use { it.setLength(totalBytes) }

            for (i in 0 until numThreads) {
                val start = i * chunkSize
                val end = if (i == numThreads - 1) totalBytes - 1 else (i + 1) * chunkSize - 1
                deferreds.add(async {
                    downloadModernChunk(url, out, start, end, downloadedBytes, totalBytes, onProgressUpdate)
                })
            }

            deferreds.awaitAll()
            DownloadResult.Success
        } catch (e: Exception) {
            downloadModernSingle(url, out, onProgressUpdate)
        }
    }

    private suspend fun downloadModernChunk(
        url: String,
        out: File,
        start: Long,
        end: Long,
        downloadedBytes: AtomicLong,
        totalBytes: Long,
        onProgressUpdate: (Float?) -> Unit
    ) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .addHeader("Range", "bytes=$start-$end")
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body ?: return@withContext
            RandomAccessFile(out, "rw").use { raf ->
                raf.channel.map(java.nio.channels.FileChannel.MapMode.READ_WRITE, start, end - start + 1).let { buffer ->
                    val source = body.source()
                    val sinkBuffer = ByteArray(64 * 1024)
                    while (isActive) {
                        val read = source.read(sinkBuffer)
                        if (read == -1) break
                        buffer.put(sinkBuffer, 0, read)
                        val current = downloadedBytes.addAndGet(read.toLong())
                        onProgressUpdate(current.toFloat() / totalBytes.toFloat())
                    }
                }
            }
        }
    }

    private suspend fun downloadModernSingle(
        url: String,
        out: File,
        onProgressUpdate: (Float?) -> Unit
    ): DownloadResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                val body = response.body ?: return@withContext DownloadResult.Error("EMPTY")
                val totalBytes = body.contentLength()
                var downloaded = 0L
                
                out.sink().buffer().use { sink ->
                    val source = body.source()
                    while (isActive) {
                        val read = source.read(sink.buffer, 64 * 1024)
                        if (read == -1L) break
                        sink.emitCompleteSegments()
                        downloaded += read
                        onProgressUpdate(downloaded.toFloat() / totalBytes.toFloat())
                    }
                }
            }
            DownloadResult.Success
        } catch (e: Exception) {
            DownloadResult.Error(e.message ?: "ERR")
        }
    }
}

sealed interface DownloadResult {
    data object Success : DownloadResult
    data class Cancelled(val systemTriggered: Boolean) : DownloadResult
    data class Error(val debugReason: String) : DownloadResult
}
