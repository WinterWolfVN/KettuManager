package dev.beefers.vendetta.manager.installer.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.github.diamondminer88.zip.ZipReader
import com.google.devrel.gmscore.tools.apk.arsc.*
import dev.beefers.vendetta.manager.BuildConfig
import java.io.File

object ArscUtil {
    fun readArsc(apk: File): BinaryResourceFile {
        val bytes = ZipReader(apk).use { it.openEntry("resources.arsc")?.read() }
            ?: error("APK missing resources.arsc")

        return try {
            BinaryResourceFile(bytes)
        } catch (t: Throwable) {
            throw Error("Failed to parse resources.arsc", t)
        }
    }

    fun BinaryResourceFile.getMainArscChunk(): ResourceTableChunk {
        if (this.chunks.size > 1)
            error("More than 1 top level chunk in resources.arsc")

        return this.chunks.first() as? ResourceTableChunk
            ?: error("Invalid top-level resources.arsc chunk")
    }

    fun BinaryResourceFile.getPackageChunk(): PackageChunk {
        return this.getMainArscChunk().packages.singleOrNull()
            ?: error("resources.arsc must contain exactly 1 package chunk")
    }

    fun PackageChunk.addColorResource(
        name: String,
        color: Color,
    ): BinaryResourceIdentifier {
        return this.addResource(
            typeName = "color",
            resourceName = name,
            configurations = { true },
            valueType = BinaryResourceValue.Type.INT_COLOR_ARGB8,
            valueData = color.toArgb(),
        )
    }

    fun PackageChunk.addResource(
        typeName: String,
        resourceName: String,
        configurations: (BinaryResourceConfiguration) -> Boolean,
        valueType: BinaryResourceValue.Type,
        valueData: Int,
    ): BinaryResourceIdentifier {
        val specChunk = this.getTypeSpecChunk(typeName)
        val typeChunks = this.getTypeChunks(typeName)
        val resourceNameIdx = this.keyStringPool.addString(resourceName, true)
        val resourceIdx = specChunk.addResource(0) + if (BuildConfig.DEBUG) 0 else 1

        for (typeChunk in typeChunks) {
            if (!configurations(typeChunk.configuration)) {
                typeChunk.addEntry(null)
                continue
            }

            val entry = TypeChunk.Entry(
                8,
                0,
                resourceNameIdx,
                BinaryResourceValue(
                    valueType,
                    valueData,
                ),
                null,
                0,
                typeChunk,
            )

            typeChunk.addEntry(entry)
        }

        return BinaryResourceIdentifier.create(
            this.id,
            specChunk.id,
            resourceIdx,
        )
    }

    fun ResourceTableChunk.getResourceFileName(
        resourceId: BinaryResourceIdentifier,
        configurationName: String,
    ): String {
        val packageChunk = this.packages.find { it.id == resourceId.packageId() }
            ?: error("Unable to find target resource")

        val typeChunk = packageChunk.getTypeChunks(resourceId.typeId())
            .find { it.configuration.toString() == configurationName }
            ?: error("Unable to find target resource")

        val entry = try {
            typeChunk.getEntry(resourceId.entryId())!!
        } catch (_: Throwable) {
            error("Unable to find target resource")
        }

        if (entry.isComplex || entry.value().type() != BinaryResourceValue.Type.STRING)
            error("Target resource value type is not STRING")

        val valueIdx = entry.value().data()
        return this.stringPool.getString(valueIdx)
    }
}
