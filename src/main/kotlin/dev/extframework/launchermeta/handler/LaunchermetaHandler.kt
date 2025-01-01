@file:JvmName("LaunchermetaHandler")

package dev.extframework.launchermeta.handler

import com.durganmcbroom.jobs.result
import com.durganmcbroom.resources.*
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import dev.extframework.common.util.Hex
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.url
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

public fun loadVersionManifest(): VersionManifest {
    return mapper.readValue<VersionManifest>(URL(LAUNCHER_META).openStream())
}


public fun VersionManifestReference.metadata(): Result<Resource> = result {
    VerifiedResource(RemoteResource(HttpRequestBuilder().apply { url(this@metadata.url) }), ResourceAlgorithm.SHA1, Hex.parseHex(sha1))
}

public suspend fun parseMetadata(resource: Resource): Result<LaunchMetadata> = runCatching {
    mapper.readValue(resource.open().toByteArray())
}

public suspend fun LaunchMetadata.assetIndex(): Result<Resource> = runCatching {
    VerifiedResource(
        URL(assetIndex.url).toResource(),
        ResourceAlgorithm.SHA1,
        Hex.parseHex(assetIndex.checksum),
    )
}

public suspend fun parseAssetIndex(resource: Resource): Result<AssetIndex> = runCatching {
    mapper.readValue<AssetIndex>(resource.open().toByteArray())
}

public interface LaunchMetadataProcessor {
    public fun deriveDependencies(os: OsType, metadata: LaunchMetadata): List<MetadataLibrary>

    public fun deriveArtifacts(osType: OsType, metadataLibrary: MetadataLibrary): List<McArtifact>

    public fun formatArg(values: Map<String, String>, arg: Argument): List<String>?
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

    private fun replaceOptionVariable(
        str: String,
        values: Map<String, String>
    ): String? {
        var result = str

        while (true) {
            val startIndex = result.indexOf("\${")
            if (startIndex != -1) {
                // Find the closing brace
                val closingBraceIndex = result.indexOf('}', startIndex).takeIf { it != -1 } ?: result.length
                val name = result.substring(startIndex + 2, closingBraceIndex)
                val value = values[name]

                if (value != null) {
                    result = result.replaceRange(startIndex, closingBraceIndex + 1, value)
                } else {
                    return null
                }
            } else {
                return result
            }
        }
    }

    private fun checkRule(os: OsRule, rule: Rule): Boolean {
        return if (rule.action == "allow") {
            val osRule = when (val osRule = rule.os) {
                null -> true
                else -> {
                    (osRule.name == os.name) &&
                            (osRule.arch == null || osRule.arch == os.arch)
                }
            }

            val featuresRule = rule.features == null // TODO Don't ignore features

            osRule && featuresRule
        } else {
            false
        }
    }

    override fun formatArg(values: Map<String, String>, arg: Argument): List<String>? {
        return when (arg) {
            is Argument.Value -> {
                when (val value = arg.value) {
                    is ValueType.StringValue -> {
                        replaceOptionVariable(value.value, values)?.let { listOf(it) }
                    }
                    is ValueType.ArrayValue -> {
                        value.values.mapNotNull { s ->
                            replaceOptionVariable(s, values)
                        }.takeIf { it.isNotEmpty() }
                    }
                }
            }
            is Argument.ArgumentWithRules -> {
                val osRule = OsRule.current()

                val apply = arg.rules.all {
                    checkRule(osRule, it)
                }

                if (apply) {
                    when (val value = arg.value) {
                        is ValueType.StringValue -> {
                            replaceOptionVariable(value.value, values)?.let { listOf(it) }
                        }
                        is ValueType.ArrayValue -> {
                            value.values.mapNotNull { s ->
                                replaceOptionVariable(s, values)
                            }.takeIf { it.isNotEmpty() }
                        }
                    }
                } else {
                    null
                }
            }
        }
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