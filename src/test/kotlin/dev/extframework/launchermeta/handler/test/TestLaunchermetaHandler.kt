package dev.extframework.launchermeta.handler.test

import com.durganmcbroom.jobs.launch
import dev.extframework.launchermeta.handler.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class TestLaunchermetaHandler {
    @Test
    fun `Test arguments`() {
        launch {
            runBlocking {
                val processor = DefaultMetadataProcessor()
                val manifest = loadVersionManifest()
                val metadata = manifest.find("1.21")!!
                val parsedMetadata = parseMetadata(metadata.metadata().merge()).merge()

                parsedMetadata.arguments!!.jvm.forEach { t ->
                    processor.formatArg(mapOf(), t)
                }
                println(
                    processor.formatArg(
                        mapOf("a" to "ValueA"),
                        Argument.Value(ValueType.StringValue("--something \${a}"))
                    )
                )
            }
        }

    }

    @Test
    fun `Test 1_21_1`() {
        fun <T> T.print(): T = also { println(it) }

        launch {
            runBlocking {
                val manifest = loadVersionManifest()
                val metadata = manifest.find("1.21")!!
                val parsedMetadata = parseMetadata(metadata.metadata().merge()).merge()
                parsedMetadata.clientJar().merge().location.print()
                parsedMetadata.clientMappings().merge().location.print()
                parseAssetIndex(parsedMetadata.assetIndex().merge()).merge().print()
            }
        }
    }

    @Test
    fun `Test 1_8_9`() {
        fun <T> T.print(): T = also { println(it) }

        launch {
            runBlocking {
                val manifest = loadVersionManifest()
                val metadata = manifest.find("1.8.9")!!
                val parsedMetadata = parseMetadata(metadata.metadata().merge()).merge()

                val processor = DefaultMetadataProcessor()

                println(
                    processor.deriveDependencies(
                        OsType.type,
                        parsedMetadata
                    )
                )

                parsedMetadata.clientJar().merge().location.print()
                parseAssetIndex(parsedMetadata.assetIndex().merge()).merge().print()
            }
        }
    }
}