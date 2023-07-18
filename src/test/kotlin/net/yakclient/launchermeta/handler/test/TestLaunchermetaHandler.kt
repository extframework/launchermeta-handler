package net.yakclient.launchermeta.handler.test

import net.yakclient.launchermeta.handler.*
import kotlin.test.Test

class TestLaunchermetaHandler {
    @Test
    fun `Test it`() {
        fun <T> T.print(): T = also { println(it) }

        val manifest = loadVersionManifest().print()
        val metadata = manifest.find("1.19.2")!!.print()
        val parsedMetadata = parseMetadata(metadata.metadata()).print()
        parsedMetadata.clientJar().uri.print()
        parsedMetadata.clientMappings().uri.print()
        parseAssetIndex(parsedMetadata.assetIndex()).print()
    }
}