package dev.beefers.vendetta.manager.installer.step.download.base

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.beefers.vendetta.manager.R
import dev.beefers.vendetta.manager.domain.manager.DownloadManager
import dev.beefers.vendetta.manager.domain.manager.DownloadResult
import dev.beefers.vendetta.manager.domain.manager.PreferenceManager
import dev.beefers.vendetta.manager.installer.step.Step
import dev.beefers.vendetta.manager.installer.step.StepGroup
import dev.beefers.vendetta.manager.installer.step.StepRunner
import dev.beefers.vendetta.manager.installer.step.StepStatus
import dev.beefers.vendetta.manager.utils.mainThread
import dev.beefers.vendetta.manager.utils.showToast
import kotlinx.coroutines.CancellationException
import org.koin.core.component.inject
import java.io.File
import java.util.zip.ZipFile
import kotlin.math.roundToInt

@Stable
abstract class DownloadStep : Step() {

    protected val preferenceManager: PreferenceManager by inject()
    protected val baseUrl = preferenceManager.mirror.baseUrl

    private val downloadManager: DownloadManager by inject()
    private val context: Context by inject()

    abstract val url: String
    abstract val destination: File
    abstract val workingCopy: File

    override val group: StepGroup = StepGroup.DL

    var cached by mutableStateOf(false)
        private set

    open suspend fun verify() {
        if (!destination.exists() || destination.length() <= 0) {
            destination.delete()
            error("Invalid file: ${destination.absolutePath}")
        }
        try {
            ZipFile(destination).use { zip ->
                if (!zip.entries().hasMoreElements()) {
                    destination.delete()
                    error("Empty zip archive: ${destination.absolutePath}")
                }
            }
        } catch (e: Exception) {
            destination.delete()
            error("Corrupted zip: ${destination.absolutePath}")
        }
    }

    override suspend fun run(runner: StepRunner) {
        val fileName = destination.name

        destination.parentFile?.mkdirs()
        workingCopy.parentFile?.mkdirs()
        
        if (destination.exists()) {
            try {
                verify()
                cached = true
                destination.copyTo(workingCopy, true)
                status = StepStatus.SUCCESSFUL
                return
            } catch (e: Exception) {
                destination.delete()
            }
        }

        var lastProgress = 0f
        
        val result = downloadManager.download(url, destination) { newProgress ->
            if (newProgress != null) {
                if (newProgress - lastProgress >= 0.2f || newProgress == 1f) {
                    progress = newProgress
                    lastProgress = newProgress
                    runner.logger.d("$fileName: ${(newProgress * 100f).roundToInt()}%")
                }
            }
        }

        when (result) {
            is DownloadResult.Success -> {
                try {
                    verify()
                    destination.copyTo(workingCopy, true)
                } catch (t: Throwable) {
                    mainThread { context.showToast(R.string.msg_download_verify_failed) }
                    throw t
                }
            }
            is DownloadResult.Error -> {
                mainThread {
                    context.showToast(R.string.msg_download_failed)
                    runner.downloadErrored = true
                }
                throw Error("Download failed: ${result.debugReason}")
            }
            is DownloadResult.Cancelled -> {
                status = StepStatus.UNSUCCESSFUL
                throw CancellationException("Cancelled")
            }
        }
    }
}
