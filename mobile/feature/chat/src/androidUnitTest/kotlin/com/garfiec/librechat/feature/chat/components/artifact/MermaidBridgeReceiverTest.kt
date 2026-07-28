package com.garfiec.librechat.feature.chat.components.artifact

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MermaidBridgeReceiverTest {

    @Test
    fun `onSvg writes to cache under bound key`() {
        val cache = MermaidRenderCache()
        val receiver = MermaidBridgeReceiver(cache, key = "k1")
        receiver.onSvg("<svg id='one'/>")

        assertThat(cache["k1"]).isEqualTo("<svg id='one'/>")
    }

    @Test
    fun `two receivers with different keys write to distinct entries`() {
        val cache = MermaidRenderCache()
        val r1 = MermaidBridgeReceiver(cache, key = "a")
        val r2 = MermaidBridgeReceiver(cache, key = "b")

        r1.onSvg("<svg id='a'/>")
        r2.onSvg("<svg id='b'/>")

        assertThat(cache["a"]).isEqualTo("<svg id='a'/>")
        assertThat(cache["b"]).isEqualTo("<svg id='b'/>")
    }

    @Test
    fun `repeated onSvg overwrites the cache entry for the bound key`() {
        val cache = MermaidRenderCache()
        val receiver = MermaidBridgeReceiver(cache, key = "k")
        receiver.onSvg("<svg id='v1'/>")
        receiver.onSvg("<svg id='v2'/>")

        assertThat(cache["k"]).isEqualTo("<svg id='v2'/>")
    }
}
