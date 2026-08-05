/*
 * DCF2 payload container adapted from decimen-optical-transfer/shared/protocol.ts
 * and snippet metadata adapted from decimen-optical-transfer/shared/snippet.ts.
 * Copyright (c) 2026 BashAlarmist. Licensed under the MIT License.
 * See third_party/decimen-optical-transfer/LICENSE.
 */
package com.sunnychung.application.easytransfer.optical

data class TransferPayload(
    val kind: TransferKind,
    val bytes: ByteArray,
    val name: String? = null,
    val mediaType: String? = null,
) {
    init {
        require(bytes.isNotEmpty()) { "Transfer content cannot be empty" }
    }

    fun text(): String? = when (kind) {
        TransferKind.Text,
        TransferKind.Link,
        -> bytes.decodeToString()

        TransferKind.Image,
        TransferKind.File,
        -> null
    }

    fun textPreview(
        maxBytes: Int = 512 * 1_024,
        chunkCharacters: Int = 4_096,
    ): TransferTextPreview? = when (kind) {
        TransferKind.Text,
        TransferKind.Link,
        -> {
            val previewLength = bytes.size.coerceAtMost(maxBytes)
            val previewText = bytes.copyOfRange(0, previewLength).decodeToString()
            TransferTextPreview(
                chunks = previewText.chunked(chunkCharacters),
                isTruncated = previewLength < bytes.size,
            )
        }

        TransferKind.Image,
        TransferKind.File,
        -> null
    }

    companion object {
        fun text(value: String): TransferPayload = TransferPayload(
            kind = TransferKind.Text,
            bytes = value.encodeToByteArray(),
            mediaType = "text/plain; charset=utf-8",
        )

        fun link(value: String): TransferPayload = TransferPayload(
            kind = TransferKind.Link,
            bytes = value.encodeToByteArray(),
            mediaType = "text/uri-list",
        )
    }
}

data class TransferTextPreview(
    val chunks: List<String>,
    val isTruncated: Boolean,
)

enum class TransferPayloadCompression(
    val label: String,
) {
    None(label = "none"),
    Gzip(label = "gzip"),
}

data class PackedTransferPayload(
    val container: ByteArray,
    val compression: TransferPayloadCompression,
    val originalSize: Int,
    val transmittedSize: Int,
)

internal object TransferPayloadCodec {
    private val magic = byteArrayOf(0x44, 0x43, 0x46, 0x32)
    private const val HEADER_LENGTH = 49
    private const val COMPRESSION_NONE = 0
    private const val COMPRESSION_GZIP = 1
    private const val SNIPPET_MEDIA_TYPE = "application/vnd.decimen.snippet"
    private const val SNIPPET_FILE_NAME = "snippet.txt"
    private const val DEFAULT_MEDIA_TYPE = "application/octet-stream"
    private const val MIN_GZIP_CANDIDATE_BYTES = 768

    fun encode(
        payload: TransferPayload,
        compressionEnabled: Boolean = true,
    ): ByteArray = pack(payload = payload, compressionEnabled = compressionEnabled).container

    fun pack(
        payload: TransferPayload,
        compressionEnabled: Boolean = true,
    ): PackedTransferPayload {
        val name = payload.officialContainerName()
        val mediaType = payload.officialContainerMediaType()
        val nameBytes = safeFileName(name).encodeToByteArray()
        val mediaTypeBytes = mediaType.encodeToByteArray()
        require(nameBytes.size <= 0xFFFF)
        require(mediaTypeBytes.size <= 0xFFFF)
        val compressed = if (
            compressionEnabled &&
            payload.bytes.size >= MIN_GZIP_CANDIDATE_BYTES &&
            !isPrecompressedType(mediaType)
        ) {
            gzip(payload.bytes)
        } else {
            null
        }
        val useGzip = compressed != null && compressed.size + 64 < payload.bytes.size
        val transmitted = if (useGzip) requireNotNull(compressed) else payload.bytes
        val compression = if (useGzip) TransferPayloadCompression.Gzip else TransferPayloadCompression.None
        val output = ByteArray(HEADER_LENGTH + nameBytes.size + mediaTypeBytes.size + transmitted.size)
        magic.copyInto(output)
        output[4] = if (useGzip) COMPRESSION_GZIP.toByte() else COMPRESSION_NONE.toByte()
        output.writeUInt16(5, nameBytes.size)
        output.writeUInt16(7, mediaTypeBytes.size)
        output.writeUInt32(9, payload.bytes.size)
        output.writeUInt32(13, transmitted.size)
        sha256(payload.bytes).copyInto(output, destinationOffset = 17)
        var offset = HEADER_LENGTH
        nameBytes.copyInto(output, offset)
        offset += nameBytes.size
        mediaTypeBytes.copyInto(output, offset)
        offset += mediaTypeBytes.size
        transmitted.copyInto(output, offset)
        return PackedTransferPayload(
            container = output,
            compression = compression,
            originalSize = payload.bytes.size,
            transmittedSize = transmitted.size,
        )
    }

    fun decode(bytes: ByteArray): TransferPayload? {
        if (bytes.size < HEADER_LENGTH || !bytes.copyOfRange(0, 4).contentEquals(magic)) return null
        val compression = bytes[4].toUByte().toInt()
        if (compression != COMPRESSION_NONE && compression != COMPRESSION_GZIP) return null
        val nameLength = bytes.readUInt16(5)
        val mediaTypeLength = bytes.readUInt16(7)
        val fileLength = bytes.readUInt32(9) ?: return null
        val transmittedLength = bytes.readUInt32(13) ?: return null
        if (fileLength <= 0 || transmittedLength <= 0) return null
        val contentOffset = HEADER_LENGTH + nameLength + mediaTypeLength
        if (contentOffset < HEADER_LENGTH || contentOffset + transmittedLength != bytes.size) return null
        val name = bytes.copyOfRange(HEADER_LENGTH, HEADER_LENGTH + nameLength)
            .decodeToString()
            .let(::safeFileName)
        val mediaTypeOffset = HEADER_LENGTH + nameLength
        val mediaType = bytes.copyOfRange(mediaTypeOffset, mediaTypeOffset + mediaTypeLength)
            .decodeToString()
            .ifBlank { DEFAULT_MEDIA_TYPE }
        val transmitted = bytes.copyOfRange(contentOffset, bytes.size)
        val payloadBytes = when (compression) {
            COMPRESSION_NONE -> transmitted
            COMPRESSION_GZIP -> gunzip(transmitted, fileLength) ?: return null
            else -> return null
        }
        if (payloadBytes.size != fileLength) return null
        if (!sha256(payloadBytes).contentEquals(bytes.copyOfRange(17, 49))) return null
        return TransferPayload(
            kind = mediaType.toTransferKind(),
            bytes = payloadBytes,
            name = name.ifBlank { null },
            mediaType = mediaType,
        )
    }

    private fun TransferPayload.officialContainerName(): String = when (kind) {
        TransferKind.Text -> SNIPPET_FILE_NAME
        TransferKind.Link -> name ?: "link.txt"
        TransferKind.Image,
        TransferKind.File,
        -> name ?: "transfer.bin"
    }

    private fun TransferPayload.officialContainerMediaType(): String = when (kind) {
        TransferKind.Text -> SNIPPET_MEDIA_TYPE
        TransferKind.Link -> mediaType ?: "text/uri-list"
        TransferKind.Image,
        TransferKind.File,
        -> mediaType ?: DEFAULT_MEDIA_TYPE
    }

    private fun isPrecompressedType(type: String): Boolean {
        val mediaType = type.substringBefore(';').trim().lowercase()
        if (mediaType.startsWith("video/")) return true
        if (mediaType.startsWith("image/")) {
            return mediaType !in setOf(
                "image/bmp",
                "image/x-ms-bmp",
                "image/svg+xml",
                "image/tiff",
                "image/x-icon",
                "image/vnd.microsoft.icon",
            )
        }
        if (mediaType.startsWith("audio/")) {
            return mediaType !in setOf(
                "audio/wav",
                "audio/x-wav",
                "audio/wave",
                "audio/vnd.wave",
                "audio/aiff",
                "audio/x-aiff",
                "audio/basic",
                "audio/l16",
            )
        }
        if (mediaType.startsWith("application/vnd.openxmlformats-officedocument.")) return true
        if (mediaType.startsWith("application/vnd.oasis.opendocument.")) return true
        if (mediaType.endsWith("+zip")) return true
        return mediaType in PRECOMPRESSED_MEDIA_TYPES
    }

    private fun String.toTransferKind(): TransferKind {
        val mediaType = substringBefore(';').trim().lowercase()
        return when {
            mediaType == SNIPPET_MEDIA_TYPE -> TransferKind.Text
            mediaType == "text/uri-list" -> TransferKind.Link
            mediaType.startsWith("image/") -> TransferKind.Image
            else -> TransferKind.File
        }
    }

    private fun safeFileName(name: String): String {
        val base = name
            .replace('\\', '/')
            .substringAfterLast('/')
            .filterNot { it.code in 0..0x1F || it.code == 0x7F }
            .trim()
        return if (base.isBlank() || base == "." || base == "..") "transfer.bin" else base
    }

    private fun ByteArray.writeUInt16(offset: Int, value: Int) {
        this[offset] = value.toByte()
        this[offset + 1] = (value ushr 8).toByte()
    }

    private fun ByteArray.writeUInt32(offset: Int, value: Int) {
        repeat(4) { index -> this[offset + index] = (value ushr (index * 8)).toByte() }
    }

    private fun ByteArray.readUInt16(offset: Int): Int =
        this[offset].toUByte().toInt() or (this[offset + 1].toUByte().toInt() shl 8)

    private fun ByteArray.readUInt32(offset: Int): Int? {
        var value = 0L
        repeat(4) { index ->
            value = value or (this[offset + index].toUByte().toLong() shl (index * 8))
        }
        return value.takeIf { it <= Int.MAX_VALUE }?.toInt()
    }

    private val PRECOMPRESSED_MEDIA_TYPES = setOf(
        "application/gzip",
        "application/java-archive",
        "application/vnd.rar",
        "application/x-7z-compressed",
        "application/x-brotli",
        "application/x-bzip",
        "application/x-bzip2",
        "application/x-gzip",
        "application/x-lzma",
        "application/x-rar-compressed",
        "application/x-xz",
        "application/x-zip-compressed",
        "application/zip",
        "application/zstd",
    )
}
