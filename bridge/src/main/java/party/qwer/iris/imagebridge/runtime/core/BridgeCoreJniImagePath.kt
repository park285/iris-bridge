package party.qwer.iris.imagebridge.runtime.core

import org.json.JSONObject

internal object BridgeCoreJniImagePath {
    fun nativeImagePathUnderAllowedRoot(
        path: String,
        allowedRootsJson: String,
    ): Boolean =
        BridgeCoreJniDispatcher.booleanValue(
            "imagePath.underAllowedRoot",
            JSONObject()
                .put("path", path)
                .put("allowedRootsJson", allowedRootsJson),
        )

    fun nativeMaterializeImagePath(
        path: String,
        allowedRootsJson: String,
    ): String =
        BridgeCoreJniDispatcher.envelope(
            "imagePath.materialize",
            JSONObject()
                .put("path", path)
                .put("allowedRootsJson", allowedRootsJson),
        )

    fun nativeRevalidateImagePathSnapshot(
        canonicalPath: String,
        allowedRootsJson: String,
        sizeBytes: Long,
        lastModifiedEpochMs: Long,
    ): String =
        BridgeCoreJniDispatcher.envelope(
            "imagePath.revalidateSnapshot",
            JSONObject()
                .put("canonicalPath", canonicalPath)
                .put("allowedRootsJson", allowedRootsJson)
                .put("sizeBytes", sizeBytes)
                .put("lastModifiedEpochMs", lastModifiedEpochMs),
        )
}
