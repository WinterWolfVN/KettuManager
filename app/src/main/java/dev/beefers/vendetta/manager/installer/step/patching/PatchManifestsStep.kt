package dev.beefers.vendetta.manager.installer.step.patching

import dev.beefers.vendetta.manager.R
import dev.beefers.vendetta.manager.domain.manager.PreferenceManager
import dev.beefers.vendetta.manager.installer.step.Step
import dev.beefers.vendetta.manager.installer.step.StepGroup
import dev.beefers.vendetta.manager.installer.step.StepRunner
import dev.beefers.vendetta.manager.installer.step.download.DownloadBaseStep
import dev.beefers.vendetta.manager.installer.step.download.DownloadLangStep
import dev.beefers.vendetta.manager.installer.step.download.DownloadLibsStep
import dev.beefers.vendetta.manager.installer.step.download.DownloadResourcesStep
import dev.beefers.vendetta.manager.installer.util.ManifestPatcher
import org.koin.core.component.inject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class PatchManifestsStep : Step() {

    private val preferences: PreferenceManager by inject()

    override val group = StepGroup.PATCHING
    override val nameRes = R.string.step_patch_manifests

    override suspend fun run(runner: StepRunner) {
        val baseApk = runner.getCompletedStep<DownloadBaseStep>().workingCopy
        val libsApk = runner.getCompletedStep<DownloadLibsStep>().workingCopy
        val langApk = runner.getCompletedStep<DownloadLangStep>().workingCopy
        val resApk = runner.getCompletedStep<DownloadResourcesStep>().workingCopy

        listOf(baseApk, libsApk, langApk, resApk).forEach { apk ->
            val tempFile = File(apk.parent, apk.name + ".tmp")
            val buffer = ByteArray(128 * 1024)

            FileInputStream(apk).buffered(128 * 1024).use { fis ->
                ZipInputStream(fis).use { zis ->
                    FileOutputStream(tempFile).buffered(128 * 1024).use { fos ->
                        ZipOutputStream(fos).use { zos ->
                            zos.setLevel(Deflater.BEST_SPEED)
                            
                            var entry: ZipEntry? = zis.nextEntry
                            while (entry != null) {
                                val newEntry = ZipEntry(entry.name)
                                zos.putNextEntry(newEntry)

                                if (entry.name == "AndroidManifest.xml") {
                                    val manifestBytes = zis.readBytes()
                                    val patchedManifest = if (apk == baseApk) {
                                        ManifestPatcher.patchManifest(
                                            manifestBytes = manifestBytes,
                                            packageName = preferences.packageName,
                                            appName = preferences.appName,
                                            debuggable = preferences.debuggable,
                                        )
                                    } else {
                                        ManifestPatcher.renamePackage(manifestBytes, preferences.packageName)
                                    }
                                    zos.write(patchedManifest)
                                } else {
                                    var len: Int
                                    while (zis.read(buffer).also { len = it } > 0) {
                                        zos.write(buffer, 0, len)
                                    }
                                }
                                
                                zos.closeEntry()
                                zis.closeEntry()
                                entry = zis.nextEntry
                            }
                        }
                    }
                }
            }
            apk.delete()
            tempFile.renameTo(apk)
        }
    }
}
