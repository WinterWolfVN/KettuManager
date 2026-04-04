package dev.beefers.vendetta.manager.installer.step.patching

import android.os.Build
import dev.beefers.vendetta.manager.R
import dev.beefers.vendetta.manager.installer.step.Step
import dev.beefers.vendetta.manager.installer.step.StepGroup
import dev.beefers.vendetta.manager.installer.step.StepRunner
import dev.beefers.vendetta.manager.installer.step.download.DownloadVendettaStep
import dev.beefers.vendetta.manager.installer.util.Patcher
import java.io.File

class AddVendettaStep(
    private val signedDir: File,
    private val lspatchedDir: File
) : Step() {

    override val group = StepGroup.PATCHING
    override val nameRes = R.string.step_add_vd

    override suspend fun run(runner: StepRunner) {
        val vendetta = runner.getCompletedStep<DownloadVendettaStep>().workingCopy
        val files = signedDir.listFiles() ?: return

        lspatchedDir.mkdirs()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runner.logger.i("Adding KettuXposed module with LSPatch (Android 8+)")
            Patcher.patch(
                runner.logger,
                outputDir = lspatchedDir,
                apkPaths = files.map { it.absolutePath },
                embeddedModules = listOf(vendetta.absolutePath)
            )
        } else {
            runner.logger.i("Adding KettuXposed module with Xpatch (Android 7)")
            
            val baseApk = files.first { it.name.startsWith("base") || it.name.endsWith(".apk") }
            val outApk = File(lspatchedDir, baseApk.name)
            val modulesParam = listOf(vendetta.absolutePath).joinToString(File.pathSeparator)

            val args = arrayOf(
                baseApk.absolutePath,
                "-o", outApk.absolutePath,
                "-xm", modulesParam
            )

            runner.logger.i("Executing Xpatch: ${args.joinToString(" ")}")
            
            val clazz = Class.forName("com.storm.wind.xpatch.MainCommand")
            val mainMethod = clazz.getMethod("main", Array<String>::class.java)
            mainMethod.invoke(null, args as Any)

            files.forEach { file ->
                if (file.absolutePath != baseApk.absolutePath) {
                    file.copyTo(File(lspatchedDir, file.name), overwrite = true)
                }
            }
        }
    }
}
