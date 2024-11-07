package dev.extframework.launchermeta.handler

import com.durganmcbroom.jobs.result
import com.durganmcbroom.resources.Resource
import com.durganmcbroom.resources.ResourceAlgorithm
import com.durganmcbroom.resources.VerifiedResource
import com.durganmcbroom.resources.toResource
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import dev.extframework.common.util.Hex
import java.net.URI
import java.util.*

@JsonIgnoreProperties(ignoreUnknown = true)
public data class LaunchMetadata(
    val mainClass: String,
    val libraries: List<MetadataLibrary>,

    val downloads: Map<String, McArtifact>,
    @JsonProperty("id")
    val version: String,
    val assetIndex: AssetIndexMetadata
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
) {
    public fun toResource(): Result<Resource> =
        result {
            VerifiedResource(
                url.toURL().toResource(),
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