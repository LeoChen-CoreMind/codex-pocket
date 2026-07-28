package com.garfiec.librechat.feature.chat.viewmodel

import com.garfiec.librechat.feature.chat.util.detectMimeTypeFromBytes
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DetectMimeTypeTest {

    private fun detect(bytes: ByteArray): String? =
        detectMimeTypeFromBytes(bytes)

    @Test
    fun `detects JPEG from magic bytes`() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) +
            ByteArray(8)
        assertThat(detect(bytes)).isEqualTo("image/jpeg")
    }

    @Test
    fun `detects PNG from magic bytes`() {
        val bytes = byteArrayOf(
            0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(),
            0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte(),
        ) + ByteArray(4)
        assertThat(detect(bytes)).isEqualTo("image/png")
    }

    @Test
    fun `detects GIF87a from magic bytes`() {
        // "GIF87a"
        val bytes = byteArrayOf(
            0x47.toByte(), 0x49.toByte(), 0x46.toByte(), 0x38.toByte(),
            0x37.toByte(), 0x61.toByte(),
        ) + ByteArray(6)
        assertThat(detect(bytes)).isEqualTo("image/gif")
    }

    @Test
    fun `detects GIF89a from magic bytes`() {
        // "GIF89a"
        val bytes = byteArrayOf(
            0x47.toByte(), 0x49.toByte(), 0x46.toByte(), 0x38.toByte(),
            0x39.toByte(), 0x61.toByte(),
        ) + ByteArray(6)
        assertThat(detect(bytes)).isEqualTo("image/gif")
    }

    @Test
    fun `detects WebP from magic bytes`() {
        // "RIFF" + 4 size bytes + "WEBP"
        val bytes = byteArrayOf(
            0x52.toByte(), 0x49.toByte(), 0x46.toByte(), 0x46.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x57.toByte(), 0x45.toByte(), 0x42.toByte(), 0x50.toByte(),
        )
        assertThat(detect(bytes)).isEqualTo("image/webp")
    }

    @Test
    fun `detects BMP from magic bytes`() {
        val bytes = byteArrayOf(0x42.toByte(), 0x4D.toByte()) + ByteArray(10)
        assertThat(detect(bytes)).isEqualTo("image/bmp")
    }

    @Test
    fun `detects TIFF little-endian from magic bytes`() {
        val bytes = byteArrayOf(
            0x49.toByte(), 0x49.toByte(), 0x2A.toByte(), 0x00.toByte(),
        ) + ByteArray(8)
        assertThat(detect(bytes)).isEqualTo("image/tiff")
    }

    @Test
    fun `detects TIFF big-endian from magic bytes`() {
        val bytes = byteArrayOf(
            0x4D.toByte(), 0x4D.toByte(), 0x00.toByte(), 0x2A.toByte(),
        ) + ByteArray(8)
        assertThat(detect(bytes)).isEqualTo("image/tiff")
    }

    @Test
    fun `detects HEIC from ftyp box`() {
        // ftyp box with "heic" brand
        val bytes = byteArrayOf(
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x18.toByte(), // box size
            0x66.toByte(), 0x74.toByte(), 0x79.toByte(), 0x70.toByte(), // "ftyp"
            0x68.toByte(), 0x65.toByte(), 0x69.toByte(), 0x63.toByte(), // "heic"
        )
        assertThat(detect(bytes)).isEqualTo("image/heic")
    }

    @Test
    fun `detects AVIF from ftyp box`() {
        val bytes = byteArrayOf(
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x1C.toByte(),
            0x66.toByte(), 0x74.toByte(), 0x79.toByte(), 0x70.toByte(), // "ftyp"
            0x61.toByte(), 0x76.toByte(), 0x69.toByte(), 0x66.toByte(), // "avif"
        )
        assertThat(detect(bytes)).isEqualTo("image/avif")
    }

    @Test
    fun `detects ICO from magic bytes`() {
        val bytes = byteArrayOf(
            0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x00.toByte(),
        ) + ByteArray(8)
        assertThat(detect(bytes)).isEqualTo("image/x-icon")
    }

    @Test
    fun `returns null for unknown format`() {
        val bytes = ByteArray(20) { 0x00.toByte() }
        assertThat(detect(bytes)).isNull()
    }

    @Test
    fun `returns null for bytes too short`() {
        val bytes = ByteArray(5) { 0xFF.toByte() }
        assertThat(detect(bytes)).isNull()
    }

    @Test
    fun `returns null for empty bytes`() {
        assertThat(detect(ByteArray(0))).isNull()
    }

    @Test
    fun `detects HEIC with mif1 brand`() {
        val bytes = byteArrayOf(
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x18.toByte(),
            0x66.toByte(), 0x74.toByte(), 0x79.toByte(), 0x70.toByte(), // "ftyp"
            0x6D.toByte(), 0x69.toByte(), 0x66.toByte(), 0x31.toByte(), // "mif1"
        )
        assertThat(detect(bytes)).isEqualTo("image/heic")
    }

    @Test
    fun `returns null for video ftyp brands`() {
        // "isom" is a video brand, not an image
        val bytes = byteArrayOf(
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x18.toByte(),
            0x66.toByte(), 0x74.toByte(), 0x79.toByte(), 0x70.toByte(), // "ftyp"
            0x69.toByte(), 0x73.toByte(), 0x6F.toByte(), 0x6D.toByte(), // "isom"
        )
        assertThat(detect(bytes)).isNull()
    }
}
