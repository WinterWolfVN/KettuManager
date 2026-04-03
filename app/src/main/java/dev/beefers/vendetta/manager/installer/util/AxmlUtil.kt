package dev.beefers.vendetta.manager.installer.util

import com.google.devrel.gmscore.tools.apk.arsc.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object AxmlUtil {
    private fun readAxml(apk: File, resourcePath: String): BinaryResourceFile {
        val bytes = ZipFile(apk).use { zip ->
            val entry = zip.getEntry(resourcePath) ?: error("APK missing resource file at $resourcePath")
            zip.getInputStream(entry).readBytes()
        }

        return try {
            BinaryResourceFile(bytes)
        } catch (t: Throwable) {
            throw Error("Failed to parse axml at $resourcePath", t)
        }
    }

    private fun BinaryResourceFile.getMainAxmlChunk(): XmlChunk {
        if (this.chunks.size > 1)
            error("More than 1 top level chunk in axml")

        return this.chunks.first() as? XmlChunk
            ?: error("Invalid top-level axml chunk")
    }

    private fun XmlChunk.getStartElementChunk(name: String): XmlStartElementChunk? {
        val nameIdx = this.stringPool.indexOf(name)
        return this.chunks.filterIsInstance<XmlStartElementChunk>().find { it.nameIndex == nameIdx }
    }

    private fun XmlStartElementChunk.getAttribute(name: String): XmlAttribute {
        val nameIdx = (this.parent as XmlChunk).stringPool.indexOf(name)

        return this.attributes
            .find { it.nameIndex() == nameIdx }
            ?: error("Failed to find $name attribute in an axml chunk")
    }

    fun patchAdaptiveIcon(
        apk: File,
        resourcePath: String,
        referencePath: String,
        backgroundColor: BinaryResourceIdentifier? = null,
        foregroundIcon: BinaryResourceIdentifier? = null,
        monochromeIcon: BinaryResourceIdentifier? = null,
    ) {
        val xml = readAxml(apk, referencePath)
        val xmlChunk = xml.getMainAxmlChunk()

        if (backgroundColor != null) {
            val chunk = xmlChunk.getStartElementChunk("background")!!
            val attribute = chunk.getAttribute("drawable")
            attribute.typedValue().setValue(
                BinaryResourceValue.Type.REFERENCE,
                backgroundColor.resourceId(),
            )
        }

        if (foregroundIcon != null) {
            val chunk = xmlChunk.getStartElementChunk("foreground")!!
            val attribute = chunk.getAttribute("drawable")
            attribute.typedValue().setValue(
                BinaryResourceValue.Type.REFERENCE,
                foregroundIcon.resourceId(),
            )
        }

        if (monochromeIcon != null) {
            val existingChunk = xmlChunk.getStartElementChunk("monochrome")
            if (existingChunk != null) {
                val attribute = existingChunk.getAttribute("drawable")
                attribute.typedValue().setValue(
                    BinaryResourceValue.Type.REFERENCE,
                    monochromeIcon.resourceId(),
                )
            }
            else {
                val iconEndChunkIdx = xmlChunk.chunks
                    .indexOfLast { it is XmlEndElementChunk && (it as XmlEndElementChunk).name == "adaptive-icon" }

                val namespaceIdx = xmlChunk.stringPool.indexOf("http://schemas.android.com/apk/res/android")
                val drawableIdx = xmlChunk.stringPool.indexOf("drawable")
                val monochromeIdx = xmlChunk.stringPool.addString("monochrome")

                val startChunk = XmlStartElementChunk(
                    -1,
                    monochromeIdx,
                    -1,
                    -1,
                    -1,
                    listOf(
                        XmlAttribute(
                            namespaceIdx,
                            drawableIdx,
                            -1,
                            BinaryResourceValue(
                                BinaryResourceValue.Type.REFERENCE,
                                monochromeIcon.resourceId(),
                            ),
                            null,
                        )
                    ),
                    xmlChunk,
                )
                val endChunk = XmlEndElementChunk(
                    namespaceIdx,
                    monochromeIdx,
                    xmlChunk,
                )

                xmlChunk.addChunk(iconEndChunkIdx, startChunk)
                xmlChunk.addChunk(iconEndChunkIdx + 1, endChunk)
            }
        }

        val tempFile = File(apk.parent, apk.name + ".tmp")
        val buffer = ByteArray(128 * 1024)
        val patchedData = xml.toByteArray()

        ZipInputStream(FileInputStream(apk).buffered(128 * 1024)).use { zis ->
            ZipOutputStream(FileOutputStream(tempFile).buffered(128 * 1024)).use { zos ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val newEntry = ZipEntry(entry.name)
                    zos.putNextEntry(newEntry)
                    
                    if (entry.name == resourcePath) {
                        zos.write(patchedData)
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
        apk.delete()
        tempFile.renameTo(apk)
    }

    fun readManifestIconInfo(apk: File): ManifestIconInfo {
        val manifestBytes = ZipFile(apk).use { zip ->
            val entry = zip.getEntry("AndroidManifest.xml") ?: error("APK missing manifest")
            zip.getInputStream(entry).readBytes()
        }
        val manifest = BinaryResourceFile(manifestBytes)
        val mainChunk = manifest.getMainAxmlChunk()

        val iconStringIdx = mainChunk.stringPool.indexOf("icon")
        val roundIconStringIdx = mainChunk.stringPool.indexOf("roundIcon")
        val applicationStringIdx = mainChunk.stringPool.indexOf("application")

        val applicationChunk = mainChunk.chunks
            .filterIsInstance<XmlStartElementChunk>()
            .find { it.nameIndex == applicationStringIdx }
            ?: error("Unable to find <application> in manifest")

        val squareIcon = applicationChunk.attributes
            .find { it.nameIndex() == iconStringIdx }
            ?: error("Unable to find android:icon in manifest")

        val roundIcon = applicationChunk.attributes
            .find { it.nameIndex() == roundIconStringIdx }
            ?: error("Unable to find android:roundIcon in manifest")

        return ManifestIconInfo(
            squareIcon = BinaryResourceIdentifier.create(squareIcon.typedValue().data()),
            roundIcon = BinaryResourceIdentifier.create(roundIcon.typedValue().data()),
        )
    }

    data class ManifestIconInfo(
        val squareIcon: BinaryResourceIdentifier,
        val roundIcon: BinaryResourceIdentifier,
    )
}
