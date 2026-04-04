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
import okhttp3.OkHttpClient
import okhttp3.Request
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

    private val xpatchUrl = "https://github.com/WindySha/Xpatch/releases/download/v6.0/xpatch-6.0.jar"

    private val httpClient = OkHttpClient.Builder()
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
            val xpatchJar = File(context.cacheDir, "xpatch-6.0.jar")

            if (!xpatchJar.exists()) {
                runner.logger.i("Downloading Xpatch engine...")
                downloadXpatch(xpatchJar)
            }

            val baseApk = files.first { it.name.startsWith("base") || it.name.endsWith(".apk") }
            val outApk = File(lspatchedDir, baseApk.name)
            val modulesParam = listOf(vendetta.absolutePath).joinToString(File.pathSeparator)

            val args = arrayOf(
                baseApk.absolutePath,
                "-o", outApk.absolutePath,
                "-xm", modulesParam
            )

            runner.logger.i("Loading Xpatch engine...")
            val dexOutputDir = File(context.cacheDir, "xpatch_dex").apply { mkdirs() }

            val classLoader = DexClassLoader(
                xpatchJar.absolutePath,
                dexOutputDir.absolutePath,
                null,
                this::class.java.classLoader
            )

            val clazz = classLoader.loadClass("com.storm.wind.xpatch.MainCommand")
            val mainMethod = clazz.getMethod("main", Array<String>::class.java)

            runner.logger.i("Executing Xpatch: ${args.joinToString(" ")}")
            mainMethod.invoke(null, args as Any)

            files.forEach { file ->
                if (file.absolutePath != baseApk.absolutePath) {
                    file.copyTo(File(lspatchedDir, file.name), overwrite = true)
                }
            }
        }
    }

    private suspend fun downloadXpatch(out: File) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(xpatchUrl).build()
        httpClient.newCall(request).execute().use { response ->
            val body = response.body ?: return@withContext
            out.sink().buffer().use { sink ->
                val source = body.source()
                while (true) {
                    val read = source.read(sink.buffer, 64 * 1024L)
                    if (read == -1L) break
                    sink.emitCompleteSegments()
                }
            }
        }
    }
}
