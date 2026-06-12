package party.qwer.iris.imagebridge.runtime.send

import party.qwer.iris.imagebridge.runtime.kakao.KakaoClassRegistry

internal const val CONTENT_TYPE_VIDEO_MP4 = "video/mp4"

internal fun normalizeMediaContentTypes(
    imagePaths: List<String>,
    contentTypes: List<String>,
): List<String> {
    if (contentTypes.isEmpty()) {
        return List(imagePaths.size) { "" }
    }
    require(contentTypes.size == imagePaths.size) {
        "media content type count ${contentTypes.size} does not match image count ${imagePaths.size}"
    }
    return contentTypes.map { contentType -> contentType.substringBefore(';').trim().lowercase() }
}

internal fun mediaMessageType(
    registry: KakaoClassRegistry,
    imagePaths: List<String>,
    contentTypes: List<String>,
): Any {
    if (imagePaths.size == 1 && contentTypes.firstOrNull() == CONTENT_TYPE_VIDEO_MP4) {
        return registry.videoType
    }
    require(contentTypes.none { it == CONTENT_TYPE_VIDEO_MP4 }) {
        "multiple video media send is not supported"
    }
    return if (imagePaths.size == 1) registry.photoType else registry.multiPhotoType
}
