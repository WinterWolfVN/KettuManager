package dev.beefers.vendetta.manager.installer.step.patching

import android.content.Context
import dev.beefers.vendetta.manager.R
import dev.beefers.vendetta.manager.installer.step.Step
import dev.beefers.vendetta.manager.installer.step.StepGroup
import dev.beefers.vendetta.manager.installer.step.StepRunner
import dev.beefers.vendetta.manager.installer.step.download.DownloadVendettaStep
import org.koin.core.component.inject
import java.io.File

class AddVendettaStep(
    private val signedDir: File,
    private val lspatchedDir: File
) : Step() {

    private val context: Context by inject()

    override val group = StepGroup.PATCHING
    override val nameRes = R.string.step_add_vd

    override suspend fun run(runner: StepRunner) {
        val vendetta = runner.getCompletedStep<DownloadVendettaStep>().workingCopy
        val files = signedDir.listFiles()?.takeIf { it.isNotEmpty() } ?: return

        lspatchedDir.mkdirs()

        val baseApk = files.first { it.name.startsWith("base") || it.name.endsWith(".apk") }
        val modulesParam = listOf(vendetta.absolutePath).joinToString(File.pathSeparator)

        val args = arrayOf(
            baseApk.absolutePath,
            "-o", lspatchedDir.absolutePath,
            "-m", modulesParam,
            "-f"
        )

        runner.logger.i("Executing built-in NPatch engine...")

        val clazz = Class.forName("org.lsposed.patch.NPatch")
        val mainMethod = clazz.getMethod("main", Array<String>::class.java)

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
