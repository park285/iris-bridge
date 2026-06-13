package party.qwer.iris.imagebridge.runtime.send

import party.qwer.iris.imagebridge.runtime.core.BridgeCore
import party.qwer.iris.imagebridge.runtime.core.BridgeCoreMediaMessageKind
import party.qwer.iris.imagebridge.runtime.core.mediaMessageKind
import party.qwer.iris.imagebridge.runtime.core.normalizeMediaContentTypes
import party.qwer.iris.imagebridge.runtime.kakao.KakaoClassRegistry

internal fun normalizeMediaContentTypes(
    imagePaths: List<String>,
    contentTypes: List<String>,
): List<String> = BridgeCore.normalizeMediaContentTypes(imagePaths.size, contentTypes)

internal fun mediaMessageType(
    registry: KakaoClassRegistry,
    imagePaths: List<String>,
    contentTypes: List<String>,
): Any =
    when (BridgeCore.mediaMessageKind(imagePaths.size, contentTypes)) {
        BridgeCoreMediaMessageKind.Photo -> registry.photoType
        BridgeCoreMediaMessageKind.MultiPhoto -> registry.multiPhotoType
        BridgeCoreMediaMessageKind.Video -> registry.videoType
    }
