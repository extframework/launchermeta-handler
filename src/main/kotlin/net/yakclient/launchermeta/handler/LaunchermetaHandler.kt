@file:JvmName("LaunchermetaHandler")

package net.yakclient.launchermeta.handler

import com.durganmcbroom.jobs.result
import com.durganmcbroom.resources.*
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.runBlocking
import net.yakclient.common.util.copyTo
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Path
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

public fun loadVersionManifest(): VersionManifest {
    val url = URL(LAUNCHER_META)
    val conn = url.openConnection() as HttpURLConnection
    if (conn.responseCode != 200) throw IllegalStateException("Failed to load launcher metadata for minecraft!")

    return mapper.readValue(conn.inputStream)
}

public fun VersionManifestReference.metadata(): Result<Resource> = result {
    VerifiedResource(URL(url).toResource(), ResourceAlgorithm.SHA1, HexFormat.of().parseHex(sha1))
}

public fun parseMetadata(resource: Resource): Result<LaunchMetadata> = result {
    mapper.readValue(resource.openStream())
}

public fun LaunchMetadata.assetIndex(): Result<Resource> = result {
    VerifiedResource(
        URL(assetIndex.url).toResource(),
        ResourceAlgorithm.SHA1,
        HexFormat.of().parseHex(assetIndex.checksum),
    )
}

public fun parseAssetIndex(resource: Resource): Result<AssetIndex> = result {
    mapper.readValue<AssetIndex>(resource.openStream())
}

public interface LaunchMetadataProcessor {
    public fun deriveDependencies(os: OsType, metadata: LaunchMetadata): List<MetadataLibrary>
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

//internal fun loadMinecraftRef(
//    mcVersion: String,
//    path: Path,
////    store: DataStore<String, LaunchMetadata>,
//): DefaultMinecraftReference {
//    // Convert an operating system name to its type
//
//
//    val versionPath = path resolve mcVersion
//    val minecraftPath = versionPath resolve "minecraft-${mcVersion}.jar"
//    val mappingsPath = versionPath resolve "minecraft-mappings-${mcVersion}.txt"
//
//    // Get manifest or download manifest
//    val manifest = store[mcVersion] ?: run {
//        val url = URL(LAUNCHER_META)
//        val conn = url.openConnection() as HttpURLConnection
//        if (conn.responseCode != 200) throw IllegalStateException("Failed to load launcher metadata for minecraft! Was trying to load minecraft version: '$mcVersion' but it was not already cached.")
//
//
//        val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
//            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
//
//        val launcherManifest = mapper.readValue<VersionManifest>(conn.inputStream)
//
//        val version = launcherManifest.versions.find { it.id == mcVersion }
//            ?: throw IllegalStateException("Failed to find minecraft version: '$mcVersion'. Looked in: '$LAUNCHER_META'.")
//
//        val manifest = mapper.readValue<LaunchMetadata>(URL(version.url).openStream())
//
//        // Download minecraft jar
//        if (minecraftPath.make()) {
//            val client = (manifest.downloads[LaunchMetadataDownloadType.CLIENT]
//                ?: throw IllegalStateException("Invalid client.json manifest. Must have a client download available!"))
//
//            client.toResource().copyToBlocking(minecraftPath)
//        }
//
//        // Download mappings
//        if (mappingsPath.make()) {
//            val mappings = (manifest.downloads[LaunchMetadataDownloadType.CLIENT_MAPPINGS]
//                ?: throw IllegalStateException("Invalid client.json manifest. Must have a client mappings download available!"))
//            mappings.toResource().copyToBlocking(mappingsPath)
//        }
//
//        // Download manifest
//        store.put(mcVersion, manifest)
//
//        manifest
//    }
//
//
//    val libPath = versionPath resolve "lib"
//
//    // Load libraries, from manifest
//    val libraries: List<MetadataLibrary> = manifest.libraries.filter { lib ->
//        val allTypes = setOf(
//            OsType.OS_X, OsType.WINDOWS, OsType.UNIX
//        )
//
//        val allowableOperatingSystems = if (lib.rules.isEmpty()) allTypes.toMutableSet()
//        else lib.rules.filter { it.action == LibraryRuleAction.ALLOW }.flatMapTo(HashSet()) {
//            it.osName?.osNameToType()?.let(::listOf) ?: allTypes
//        }
//
//        lib.rules.filter { it.action == LibraryRuleAction.DISALLOW }.forEach {
//            it.osName?.osNameToType()?.let(allowableOperatingSystems::remove)
//        }
//
//        allowableOperatingSystems.contains(OsType.type)
//    }
//
//    // Loads minecraft dependencies
//    val minecraftDependencies = libraries
//        .map {
//            val desc = SimpleMavenDescriptor.parseDescription(it.name)!!
//            val toPath = libPath resolve (desc.group.replace(
//                '.',
//                File.separatorChar
//            )) resolve desc.artifact resolve desc.version resolve "${desc.artifact}-${desc.version}${if (desc.classifier == null) "" else "-${desc.classifier}"}.jar"
//            if (toPath.make()) {
//                it.downloads.artifact.toResource().copyToBlocking(toPath)
//            }
//
//            Archives.Finders.ZIP_FINDER.find(toPath)
//        }
//
//    // Loads minecraft reference
//    val mcReference = Archives.find(minecraftPath, Archives.Finders.ZIP_FINDER)
//    return DefaultMinecraftReference(
//        mcVersion,
//        mcReference,
//        minecraftDependencies,
//        manifest,
//        ProGuardMappingParser.parse(mappingsPath.inputStream())
//    )
//}