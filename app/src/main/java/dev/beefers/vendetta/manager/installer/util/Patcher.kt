package dev.beefers.vendetta.manager.installer.util

import dev.beefers.vendetta.manager.installer.step.StepLogger
import org.lsposed.patch.NPatch
import java.io.File

object Patcher {
    fun patch(logger: StepLogger, outputDir: File, apks: List<String>, modules: List<String>) {
        val argsList = mutableListOf<String>()
        argsList.addAll(apks)
        argsList.add("-o")
        argsList.add(outputDir.absolutePath)
        argsList.add("-m")
        argsList.add(modules.joinToString(File.pathSeparator))
        argsList.add("-f")
        
        argsList.add("-k")
        argsList.add(Signer.keyStore.absolutePath)
        argsList.add("password")
        argsList.add("alias")
        argsList.add("password")

        logger.i("Executing built-in NPatch engine with Keystore...")
        
        NPatch.main(argsList.toTypedArray())

        val baseApkPath = apks.firstOrNull { it.contains("base") || it.endsWith(".apk") } ?: return
        val baseApkFile = File(baseApkPath)

        val patchedApk = outputDir.listFiles()?.firstOrNull { it.name.contains("-patched") || it.name.contains("base") }
        if (patchedApk != null && patchedApk.name != baseApkFile.name) {
            patchedApk.renameTo(File(outputDir, baseApkFile.name))
        }
    }
}
