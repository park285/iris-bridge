package party.qwer.iris.imagebridge.runtime.core

import org.json.JSONObject

internal object BridgeCoreJniMedia {
    fun nativeNormalizeMediaContentTypes(
        imageCount: Int,
        contentTypesJson: String,
    ): String =
        BridgeCoreJniDispatcher.envelope(
            "media.normalizeContentTypes",
            JSONObject()
                .put("imageCount", imageCount)
                .put("contentTypesJson", contentTypesJson),
        )

    fun nativeNormalizeMediaContentTypesFromLeases(
        imageCount: Int,
        leasesJson: String,
    ): String =
        BridgeCoreJniDispatcher.envelope(
            "media.normalizeContentTypesFromLeases",
            JSONObject()
                .put("imageCount", imageCount)
                .put("leasesJson", leasesJson),
        )

    fun nativeMediaMessageKind(
        imageCount: Int,
        contentTypesJson: String,
    ): String =
        BridgeCoreJniDispatcher.envelope(
            "media.messageKind",
            JSONObject()
                .put("imageCount", imageCount)
                .put("contentTypesJson", contentTypesJson),
        )

    fun nativeValidateShareManagerImageMedia(contentTypesJson: String): String =
        BridgeCoreJniDispatcher.envelope(
            "media.validateShareManagerImage",
            JSONObject().put("contentTypesJson", contentTypesJson),
        )
}
