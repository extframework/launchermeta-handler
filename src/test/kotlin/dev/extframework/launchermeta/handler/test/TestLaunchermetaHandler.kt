package dev.extframework.launchermeta.handler.test

import com.durganmcbroom.jobs.launch
import dev.extframework.launchermeta.handler.*
import kotlin.test.Test

class TestLaunchermetaHandler {
    @Test
    fun `Test 1_21_1`() {
        fun <T> T.print(): T = also { println(it) }

        launch {
            val manifest = loadVersionManifest()
            val metadata = manifest.find("1.21.1")!!
            val parsedMetadata = parseMetadata(metadata.metadata().merge()).merge()
            parsedMetadata.clientJar().merge().location.print()
            parsedMetadata.clientMappings().merge().location.print()
            parseAssetIndex(parsedMetadata.assetIndex().merge()).merge().print()
        }
    }

    @Test
    fun `Test 1_8_9`() {
        fun <T> T.print(): T = also { println(it) }

        launch {
            val manifest = loadVersionManifest()
            val metadata = manifest.find("1.8.9")!!
            val parsedMetadata = parseMetadata(metadata.metadata().merge()).merge()
            parsedMetadata.clientJar().merge().location.print()
            parseAssetIndex(parsedMetadata.assetIndex().merge()).merge().print()
        }
    }
}