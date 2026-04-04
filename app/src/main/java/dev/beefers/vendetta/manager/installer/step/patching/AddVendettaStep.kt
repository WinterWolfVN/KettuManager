package dev.beefers.vendetta.manager.installer.step.patching

import android.content.Context
import android.os.Build
import dalvik.system.DexClassLoader
import dev.beefers.vendetta.manager.R
import dev.beefers.vendetta.manager.installer.step.Step
import dev.beefers.vendetta.manager.installer.step.StepGroup
import dev.beefers.vendetta.manager.installer.step.StepRunner
import dev.beefers.vendetta.manager.installer.step.download.DownloadVendettaStep
import dev.beefers.vendetta.manager.installer.util.Patcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.TlsVersion
import okio.buffer
import okio.sink
import org.koin.core.component.inject
import java.io.File
import java.util.concurrent.TimeUnit

class AddVendettaStep(
    private val signedDir: File,
    private val lspatchedDir: File
) : Step() {

    private val context: Context by inject()

    override val group = StepGroup.PATCHING
    override val nameRes = R.string.step_add_vd

    private val npatchUrl = "https://github.com/WinterWolfVN/KettuManager/blob/main/app/libs/jar-v0.8.0-538-release.jar"

    private val modernTlsSpec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
        .tlsVersions(TlsVersion.TLS_1_2)
        .build()

    private val httpClient = OkHttpClient.Builder()
        .connectionSpecs(listOf(modernTlsSpec, ConnectionSpec.COMPATIBLE_TLS, ConnectionSpec.CLEARTEXT))
        .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    override suspend fun run(runner: StepRunner) {
        val vendetta = runner.getCompletedStep<DownloadVendettaStep>().workingCopy
        val files = signedDir.listFiles()?.takeIf { it.isNotEmpty() } ?: return

        lspatchedDir.mkdirs()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Patcher.patch(
                runner.logger,
                lspatchedDir,
                files.map { it.absolutePath },
                listOf(vendetta.absolutePath)
            )
        } else {
            val npatchJar = File(context.cacheDir, "npatch-0.8.0.jar")

            if (!npatchJar.exists()) {
                runner.logger.i("Downloading NPatch engine...")
                downloadNPatch(npatchJar)
            }

            val baseApk = files.first { it.name.startsWith("base") || it.name.endsWith(".apk") }
            val modulesParam = listOf(vendetta.absolutePath).joinToString(File.pathSeparator)

            val args = arrayOf(
                baseApk.absolutePath,
                "-o", lspatchedDir.absolutePath,
                "-m", modulesParam,
                "-f"
            )

            runner.logger.i("Loading NPatch engine...")
            val dexOutputDir = context.getDir("npatch_dex", Context.MODE_PRIVATE)

            val classLoader = DexClassLoader(
                npatchJar.absolutePath,
                dexOutputDir.absolutePath,
                null,
                context.classLoader
            )

            val clazz = classLoader.loadClass("org.lsposed.patch.NPatch")
            val mainMethod = clazz.getMethod("main", Array<String>::class.java)

            runner.logger.i("Executing NPatch: ${args.joinToString(" ")}")
            mainMethod.invoke(null, args as Any)

            val patchedApk = lspatchedDir.listFiles()?.firstOrNull { it.name.contains("-patched") || it.name.contains("base") }
            if (patchedApk != null && patchedApk.name != baseApk.name) {
                patchedApk.renameTo(File(lspatchedDir, baseApk.name))
            }

            files.forEach { file ->
                if (file.absolutePath != baseApk.absolutePath) {
                    file.copyTo(File(lspatchedDir, file.name), overwrite = true)
                }
            }
        }
    }

    private suspend fun downloadNPatch(out: File) {
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(npatchUrl).build()
            val response = httpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                out.delete()
                throw Exception("HTTP ${response.code}")
            }
            
            val body = response.body ?: throw Exception("Empty body")
            out.sink().buffer().use { sink ->
                val source = body.source()
                val chunkSize = 2048 * 1024L
                while (true) {
                    val read = source.read(sink.buffer, chunkSize)
                    if (read == -1L) break
                    sink.emitCompleteSegments()
                }
            }
        }
    }
}
