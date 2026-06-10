package party.qwer.iris.imagebridge.runtime.core

internal object BridgeCoreJniImagePath {
    external fun nativeImagePathUnderAllowedRoot(
        path: String,
        allowedRootsJson: String,
    ): Boolean

    external fun nativeMaterializeImagePath(
        path: String,
        allowedRootsJson: String,
    ): String

    external fun nativeRevalidateImagePathSnapshot(
        canonicalPath: String,
        allowedRootsJson: String,
        sizeBytes: Long,
        lastModifiedEpochMs: Long,
    ): String
}
