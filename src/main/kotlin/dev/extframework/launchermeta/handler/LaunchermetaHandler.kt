@file:JvmName("LaunchermetaHandler")

package dev.extframework.launchermeta.handler

import com.durganmcbroom.jobs.result
import com.durganmcbroom.resources.*
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import dev.extframework.common.util.Hex
import java.net.URL
import java.util.*

private const val LAUNCHER_META = "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json"

private val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

public data class VersionManifest(
    val versions: List<VersionManifestReference>,
) {
    public fun find(version: String): VersionManifestReference? {
        return versions.find { version == it.id }
    }
}

public data class VersionManifestReference(
    val id: String,
    val url: String,
    val sha1: String,
)

public fun loadVersionManifest(): VersionManifest = URL(LAUNCHER_META).useConnection { conn ->
    if (conn.responseCode != 200) throw IllegalStateException("Failed to load launcher metadata for minecraft!")

    mapper.readValue<VersionManifest>(conn.inputStream)
}.use { it.value }

public fun VersionManifestReference.metadata(): Result<Resource> = result {
    VerifiedResource(URL(url).toResource(), ResourceAlgorithm.SHA1, Hex.parseHex(sha1))
}

public fun parseMetadata(resource: Resource): Result<LaunchMetadata> = result {
    mapper.readValue(resource.openStream())
}

public fun LaunchMetadata.assetIndex(): Result<Resource> = result {
    VerifiedResource(
        URL(assetIndex.url).toResource(),
        ResourceAlgorithm.SHA1,
        Hex.parseHex(assetIndex.checksum),
    )
}

public fun parseAssetIndex(resource: Resource): Result<AssetIndex> = result {
    mapper.readValue<AssetIndex>(resource.openStream())
}

public interface LaunchMetadataProcessor {
    public fun deriveDependencies(os: OsType, metadata: LaunchMetadata): List<MetadataLibrary>

    public fun deriveArtifacts(osType: OsType, metadataLibrary: MetadataLibrary): List<McArtifact>
}


public class DefaultMetadataProcessor : LaunchMetadataProcessor {
    override fun deriveDependencies(os: OsType, metadata: LaunchMetadata): List<MetadataLibrary> {
        fun String.osNameToType(): OsType? = when (this) {
            "linux" -> OsType.UNIX
            "windows" -> OsType.WINDOWS
            "osx" -> OsType.OS_X
            else -> null
        }

        // Load libraries, from manifest
        val libraries: List<MetadataLibrary> = metadata.libraries.filter { lib ->
            val allTypes = setOf(
                OsType.OS_X, OsType.WINDOWS, OsType.UNIX
            )

            val allowableOperatingSystems = if (lib.rules.isEmpty()) allTypes.toMutableSet()
            else lib.rules.filter { it.action == LibraryRuleAction.ALLOW }.flatMapTo(HashSet()) {
                it.osName?.osNameToType()?.let(::listOf) ?: allTypes
            }

            lib.rules.filter { it.action == LibraryRuleAction.DISALLOW }.forEach {
                it.osName?.osNameToType()?.let(allowableOperatingSystems::remove)
            }

            allowableOperatingSystems.contains(os)
        }

        return libraries
    }

    override fun deriveArtifacts(osType: OsType, metadataLibrary: MetadataLibrary): List<McArtifact> {
        val osNames = when (OsType.type) {
            OsType.OS_X -> listOf("osx", "macos")
            OsType.WINDOWS -> listOf("windows")
            OsType.UNIX -> listOf("linux")
        }

        return listOfNotNull(
            metadataLibrary.downloads.artifact,
            osNames.firstNotNullOfOrNull(metadataLibrary.natives::get)
                ?.let { metadataLibrary.downloads.classifiers[it] }
        )
    }
}

public fun LaunchMetadata.clientJar(): Result<Resource> {
    val client = (downloads[LaunchMetadataDownloadType.CLIENT]
        ?: throw IllegalStateException("Invalid client.json manifest. Must have a client download available!"))

    return client.toResource()
}

public fun LaunchMetadata.clientMappings(): Result<Resource> {
    val client = (downloads[LaunchMetadataDownloadType.CLIENT_MAPPINGS]
        ?: throw IllegalStateException("Invalid client.json manifest. Must have a client mappings download available!"))

    return client.toResource()
}