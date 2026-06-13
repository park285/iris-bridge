package party.qwer.iris.imagebridge.runtime.core

import org.json.JSONArray

internal enum class BridgeCoreMediaMessageKind {
    Photo,
    MultiPhoto,
    Video,
}

internal fun BridgeCore.normalizeMediaContentTypes(
    imageCount: Int,
    contentTypes: List<String>,
): List<String> {
    val envelope =
        nativeMediaEnvelope(
            "normalize media content types",
        ) {
            BridgeCoreJniMedia.nativeNormalizeMediaContentTypes(imageCount, contentTypesJson(contentTypes))
        }
    requireMediaOk(envelope, "media content type normalization rejected")
    return envelope.stringList("normalizedContentTypes")
        ?: error("bridge core returned malformed media content type normalization")
}

internal fun BridgeCore.mediaMessageKind(
    imageCount: Int,
    contentTypes: List<String>,
): BridgeCoreMediaMessageKind {
    val envelope =
        nativeMediaEnvelope(
            "select media message kind",
        ) {
            BridgeCoreJniMedia.nativeMediaMessageKind(imageCount, contentTypesJson(contentTypes))
        }
    requireMediaOk(envelope, "media message kind rejected")
    return when (envelope.string("messageKind")) {
        "photo" -> BridgeCoreMediaMessageKind.Photo
        "multiPhoto" -> BridgeCoreMediaMessageKind.MultiPhoto
        "video" -> BridgeCoreMediaMessageKind.Video
        else -> error("bridge core returned malformed media message kind")
    }
}

internal fun BridgeCore.validateShareManagerImageMedia(contentTypes: List<String>) {
    val envelope =
        nativeMediaEnvelope(
            "validate ShareManager image media",
        ) {
            BridgeCoreJniMedia.nativeValidateShareManagerImageMedia(contentTypesJson(contentTypes))
        }
    requireMediaOk(envelope, "ShareManager image media rejected")
}

private fun nativeMediaEnvelope(
    label: String,
    dispatch: () -> String,
): BridgeCoreEnvelope {
    if (!bridgeCoreLoadLibraryOnce()) {
        error("bridge core unavailable to $label")
    }
    return runCatching { BridgeCoreEnvelope.parse(dispatch()) }
        .getOrElse { error ->
            bridgeCoreLogError("bridge-core $label threw", error)
            error("bridge core unavailable to $label")
        }
}

private fun requireMediaOk(
    envelope: BridgeCoreEnvelope,
    defaultMessage: String,
) {
    if (!envelope.isOk) {
        throw IllegalArgumentException(envelope.errorMessage ?: defaultMessage)
    }
}

private fun contentTypesJson(contentTypes: List<String>): String =
    JSONArray()
        .apply {
            contentTypes.forEach { contentType -> put(contentType) }
        }.toString()
