package dev.beefers.vendetta.manager.installer.step.patching

import android.content.Context
import androidx.compose.ui.graphics.Color
import dev.beefers.vendetta.manager.R
import dev.beefers.vendetta.manager.domain.manager.PreferenceManager
import dev.beefers.vendetta.manager.installer.step.Step
import dev.beefers.vendetta.manager.installer.step.StepGroup
import dev.beefers.vendetta.manager.installer.step.StepRunner
import dev.beefers.vendetta.manager.installer.step.download.DownloadBaseStep
import dev.beefers.vendetta.manager.installer.util.ArscUtil
import dev.beefers.vendetta.manager.installer.util.ArscUtil.addColorResource
import dev.beefers.vendetta.manager.installer.util.ArscUtil.getMainArscChunk
import dev.beefers.vendetta.manager.installer.util.ArscUtil.getPackageChunk
import dev.beefers.vendetta.manager.installer.util.ArscUtil.getResourceFileName
import dev.beefers.vendetta.manager.installer.util.AxmlUtil
import dev.beefers.vendetta.manager.utils.DiscordVersion
import org.koin.core.component.inject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ReplaceIconStep : Step() {

    private val preferences: PreferenceManager by inject()

    val context: Context by inject()

    override val group = StepGroup.PATCHING
    override val nameRes = R.string.step_change_icon

    override suspend fun run(runner: StepRunner) {
        val baseApk = runner.getCompletedStep<DownloadBaseStep>().workingCopy

        val arsc = ArscUtil.readArsc(baseApk)
        val iconRscIds = AxmlUtil.readManifestIconInfo(baseApk)
        val squareIconFile = arsc.getMainArscChunk().getResourceFileName(iconRscIds.squareIcon, "anydpi-v26")
        val roundIconFile = arsc.getMainArscChunk().getResourceFileName(iconRscIds.roundIcon, "anydpi-v26")

        val backgroundColor = arsc.getPackageChunk().addColorResource("kettu_color", Color(0xFF000000))

        val postfix = when (preferences.channel) {
            DiscordVersion.Type.BETA -> "beta"
            DiscordVersion.Type.ALPHA -> "canary"
            else -> null
        }
        
        for (rscFile in setOf(squareIconFile, roundIconFile)) {
            val referencePath = if (postfix == null) rscFile else {
                rscFile.replace("_$postfix.xml", ".xml")
            }

            AxmlUtil.patchAdaptiveIcon(
                apk = baseApk,
                resourcePath = rscFile,
                referencePath = referencePath,
                backgroundColor = backgroundColor,
            )
        }

        val tempFile = File(baseApk.parent, baseApk.name + ".arsc_tmp")
        val arscBytes = arsc.toByteArray()
        val buffer = ByteArray(128 * 1024)

        ZipInputStream(FileInputStream(baseApk).buffered(128 * 1024)).use { zis ->
            ZipOutputStream(FileOutputStream(tempFile).buffered(128 * 1024)).use { zos ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name != "resources.arsc") {
                        zos.putNextEntry(ZipEntry(entry.name))
                        var len: Int
                        while (zis.read(buffer).also { len = it } > 0) {
                            zos.write(buffer, 0, len)
                        }
                        zos.closeEntry()
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
                zos.putNextEntry(ZipEntry("resources.arsc"))
                zos.write(arscBytes)
                zos.closeEntry()
            }
        }
        baseApk.delete()
        tempFile.renameTo(baseApk)
    }
}
