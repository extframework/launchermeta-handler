package dev.extframework.launchermeta.handler

import com.durganmcbroom.jobs.result
import com.durganmcbroom.resources.RemoteResource
import com.durganmcbroom.resources.Resource
import com.durganmcbroom.resources.ResourceAlgorithm
import com.durganmcbroom.resources.VerifiedResource
import com.durganmcbroom.resources.toResource
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.node.JsonNodeType
import dev.extframework.common.util.Hex
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.url
import io.ktor.http.URLProtocol
import java.net.URI
import java.util.*

@JsonIgnoreProperties(ignoreUnknown = true)
public data class LaunchMetadata(
    val mainClass: String,
    val libraries: List<MetadataLibrary>,

    val downloads: Map<String, McArtifact>,
    @JsonProperty("id")
    val version: String,
    val assetIndex: AssetIndexMetadata,
    val arguments: Arguments?,
    val minecraftArguments: String?
)

public data class Arguments(
    val game: List<Argument>,
    val jvm: List<Argument>
)

@JsonDeserialize(using = ArgumentDeserializer::class)
public sealed class Argument {
    public data class Value(val value: ValueType) : Argument()
    public data class ArgumentWithRules(
        val rules: List<Rule>,
        val value: ValueType
    ) : Argument()
}

public sealed class ValueType {
    public data class StringValue(val value: String) : ValueType()
    public data class ArrayValue(val values: List<String>) : ValueType()
}

internal class ArgumentDeserializer : JsonDeserializer<Argument>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Argument {
        val node = p.codec.readTree<JsonNode>(p)

        // 1a. Check if this node is just a raw JSON string
        //     e.g. `"--username"`, which means no "rules",
        //     and the "value" itself is that string
        if (node.nodeType == JsonNodeType.STRING) {
            // Node is something like "--username"
            val valueType = ValueType.StringValue(node.asText())
            return Argument.Value(valueType)
        }

        // If not a raw string, then it might be an object with either:
        //  - "rules" + "value"
        //  - or maybe just "value"
        val rulesNode = node.get("rules")
        val valueNode = node.get("value")

        // 1b. Parse the 'value' into a ValueType
        val valueType = parseValueType(valueNode, p)

        // 1c. If "rules" is present -> ArgumentWithRules
        return if (rulesNode != null) {
            // convert the rules array
            val rulesList: List<Rule> = rulesNode.map { p.codec.treeToValue(it, Rule::class.java) }
            Argument.ArgumentWithRules(rulesList, valueType)
        } else {
            // no "rules" -> just Value
            Argument.Value(valueType)
        }
    }

    private fun parseValueType(valueNode: JsonNode?, parser: JsonParser): ValueType {
        // If "value" is absent or null, that might be an edge case.
        // But let's assume it's present for valid JSON
        return when {
            valueNode == null -> ValueType.StringValue("") // or handle error
            valueNode.isArray -> {
                val listOfStrings = mutableListOf<String>()
                valueNode.forEach { elem -> listOfStrings.add(elem.asText()) }
                ValueType.ArrayValue(listOfStrings)
            }
            valueNode.isTextual -> ValueType.StringValue(valueNode.asText())
            else -> ValueType.StringValue(valueNode.toString())
        }
    }
}

public data class Rule(
    val action: String,
//    @JsonInclude(JsonInclude.Include.NON_NULL)
    val os: OsRule? = null,
//    @JsonInclude(JsonInclude.Include.NON_NULL)
    val features: Features? = null
)

public data class OsRule(
    val name: String? = null,
    val arch: String? = null
) {
    public companion object {
        public fun current(): OsRule {
            val osName = System.getProperty("os.name")
            val name = when {
                osName.contains("windows", ignoreCase = true) -> "windows"
                osName.contains("mac", ignoreCase = true) -> "osx"
                osName.contains("linux", ignoreCase = true) -> "linux"
                osName.contains("freebsd", ignoreCase = true) -> "freebsd"
                osName.contains("dragonfly", ignoreCase = true) -> "dragonfly"
                osName.contains("openbsd", ignoreCase = true) -> "openbsd"
                osName.contains("netbsd", ignoreCase = true) -> "netbsd"
                osName.contains("android", ignoreCase = true) -> "android"
                else -> null
            }

            val arch = when (System.getProperty("os.arch")) {
                "x86_64", "amd64" -> "x86_64"
                "x86", "i386", "i686" -> "x86"
                "arm" -> "arm"
                "aarch64" -> "aarch64"
                "mips" -> "mips"
                "mips64" -> "mips64"
                else -> null // Unsupported or unknown architecture
            }

            return OsRule(name = name, arch = arch)
        }
    }
}

public data class Features(
    @JsonProperty("is_demo_user")
    val isDemoUser: Boolean? = null,

    @JsonProperty("has_custom_resolution")
    val hasCustomResolution: Boolean? = null,

    @JsonProperty("has_quick_plays_support")
    val hasQuickPlaysSupport: Boolean? = null,

    @JsonProperty("is_quick_play_singleplayer")
    val isQuickPlaySingleplayer: Boolean? = null,

    @JsonProperty("is_quick_play_multiplayer")
    val isQuickPlayMultiplayer: Boolean? = null,

    @JsonProperty("is_quick_play_realms")
    val isQuickPlayRealms: Boolean? = null
)

public data class AssetIndexMetadata(
    val id: String,
    @JsonProperty("sha1")
    val checksum: String,
    val url: String,
)

public object LaunchMetadataDownloadType {
    public const val CLIENT: String = "client"
    public const val CLIENT_MAPPINGS: String = "client_mappings"
    public const val SERVER: String = "server"
    public const val SERVER_MAPPINGS: String = "server_mappings"
}

@JsonIgnoreProperties(ignoreUnknown = true)
public data class MetadataLibrary(
    val name: String,
    val downloads: LibraryDownloads,
    @JsonProperty("extract")
    val extract: LibraryExtracts?,
    val natives: Map<String, String> = HashMap(),
    val rules: List<LibraryRule> = ArrayList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
public data class LibraryRule(
    val action: LibraryRuleAction,
    @JsonProperty("os")
    private val osProperties: Map<String, String> = HashMap()
) {
    val osName: String? = osProperties["name"]
}

public enum class LibraryRuleAction {
    @JsonProperty("allow")
    ALLOW,

    @JsonProperty("disallow")
    DISALLOW
}

@JsonIgnoreProperties(ignoreUnknown = true)
public data class LibraryExtracts(
    val exclude: List<String>
)

@JsonIgnoreProperties(ignoreUnknown = true)
public data class LibraryDownloads(
    val artifact: McArtifact?,
    @JsonProperty("classifiers")
    val classifiers: Map<String, McArtifact> = HashMap()
)

@JsonIgnoreProperties(ignoreUnknown = true)
public data class McArtifact(
    val url: URI,
    @JsonProperty("sha1")
    val checksum: String,
    val path: String?
) {
    public fun toResource(): Result<Resource> =
        result {
            VerifiedResource(
                RemoteResource(HttpRequestBuilder().apply {
                    url(this@McArtifact.url.toURL())
                }),
                ResourceAlgorithm.SHA1,
                Hex.parseHex(checksum)
            )
        }
}

// Asset index
public data class AssetIndex(
    val objects: Map<String, Asset>
)

public data class Asset(
    @JsonProperty("hash")
    val checksum: String,
    val size: Long,
)