package dev.beefers.vendetta.manager.installer.step.patching

import android.os.Build
import dev.beefers.vendetta.manager.R
import dev.beefers.vendetta.manager.installer.step.Step
import dev.beefers.vendetta.manager.installer.step.StepGroup
import dev.beefers.vendetta.manager.installer.step.StepRunner
import dev.beefers.vendetta.manager.installer.step.download.DownloadBaseStep
import dev.beefers.vendetta.manager.installer.step.download.DownloadLangStep
import dev.beefers.vendetta.manager.installer.step.download.DownloadLibsStep
import dev.beefers.vendetta.manager.installer.step.download.DownloadResourcesStep
import dev.beefers.vendetta.manager.installer.util.Signer
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class PresignApksStep(
    private val signedDir: File
) : Step() {

    override val group = StepGroup.PATCHING
    override val nameRes = R.string.step_signing

    override suspend fun run(runner: StepRunner) {
        val baseApk = runner.getCompletedStep<DownloadBaseStep>().workingCopy
        val libsApk = runner.getCompletedStep<DownloadLibsStep>().workingCopy
        val langApk = runner.getCompletedStep<DownloadLangStep>().workingCopy
        val resApk = runner.getCompletedStep<DownloadResourcesStep>().workingCopy

        signedDir.mkdirs()
        val apks = listOf(baseApk, libsApk, langApk, resApk)

        if (Build.VERSION.SDK_INT >= 30) {
            for (file in apks) {
                val tempFile = File(file.parent, file.name + ".aligned")
                val buffer = ByteArray(128 * 1024)
                
                try {
                    ZipFile(file).use { zipFile ->
                        ZipOutputStream(FileOutputStream(tempFile)).use { zos ->
                            val entries = zipFile.entries()
                            while (entries.hasMoreElements()) {
                                val entry = entries.nextElement()
                                val newEntry = ZipEntry(entry.name)
                                zos.putNextEntry(newEntry)
                                zipFile.getInputStream(entry).use { it.copyTo(zos) }
                                zos.closeEntry()
                            }
                        }
                    }
                    file.delete()
                    tempFile.renameTo(file)
                } catch (e: Exception) {
                    tempFile.delete()
                }
            }
        }

        apks.forEach {
            Signer.signApk(it, File(signedDir, it.name))
        }
    }
}
