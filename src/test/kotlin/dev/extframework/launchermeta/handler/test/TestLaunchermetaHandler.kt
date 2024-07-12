package dev.extframework.launchermeta.handler.test

import com.durganmcbroom.jobs.launch
import dev.extframework.launchermeta.handler.*
import kotlin.test.Test

class TestLaunchermetaHandler {
    @Test
    fun `Test it`() {
        fun <T> T.print(): T = also { println(it) }

        launch {
            val manifest = loadVersionManifest()
            val metadata = manifest.find("1.21")!!
            val parsedMetadata = parseMetadata(metadata.metadata().merge()).merge()
            parsedMetadata.clientJar().merge().location.print()
            parsedMetadata.clientMappings().merge().location.print()
            parseAssetIndex(parsedMetadata.assetIndex().merge()).merge().print()
        }
    }
}