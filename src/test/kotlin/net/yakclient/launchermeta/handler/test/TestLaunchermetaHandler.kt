package net.yakclient.launchermeta.handler.test

import com.durganmcbroom.jobs.launch
import net.yakclient.launchermeta.handler.*
import kotlin.test.Test

class TestLaunchermetaHandler {
    @Test
    fun `Test it`() {
        fun <T> T.print(): T = also { println(it) }

        launch {
            val manifest = loadVersionManifest().print()
            val metadata = manifest.find("1.20.1")!!.print()
            val parsedMetadata = parseMetadata(metadata.metadata().merge()).merge().print()
            parsedMetadata.clientJar().merge().location.print()
            parsedMetadata.clientMappings().merge().location.print()
            parseAssetIndex(parsedMetadata.assetIndex().merge()).merge().print()
        }
    }
}