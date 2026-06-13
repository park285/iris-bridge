package party.qwer.iris.imagebridge.runtime.core

internal object BridgeCoreJniMedia {
    external fun nativeNormalizeMediaContentTypes(
        imageCount: Int,
        contentTypesJson: String,
    ): String

    external fun nativeNormalizeMediaContentTypesFromLeases(
        imageCount: Int,
        leasesJson: String,
    ): String

    external fun nativeMediaMessageKind(
        imageCount: Int,
        contentTypesJson: String,
    ): String

    external fun nativeValidateShareManagerImageMedia(contentTypesJson: String): String
}
