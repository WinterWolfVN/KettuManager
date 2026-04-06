package dev.beefers.vendetta.manager.installer.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.lsposed.patch.NPatch
import org.lsposed.patch.util.Logger
import java.io.File

object Patcher {

    suspend fun patch(
        logger: Logger,
        outputDir: File,
        apkPaths: List<String>,
        embeddedModules: List<String>
    ) {
        withContext(Dispatchers.IO) {
            NPatch(
                logger,
                *apkPaths.toTypedArray(),
                "-o",
                outputDir.absolutePath,
                "-f",
                "-v",
                "-m",
                *embeddedModules.toTypedArray(),
                "-k",
                Signer.keyStore.absolutePath,
                "password",
                "alias",
                "password"
            ).doCommandLine()
        }
    }

}
