@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime.kakao.classregistry

import android.util.Log
import party.qwer.iris.imagebridge.runtime.kakao.DexClassScanner
import party.qwer.iris.imagebridge.runtime.kakao.KakaoClassRegistry
import party.qwer.iris.imagebridge.runtime.kakao.KakaoImageSendStrategy
import java.lang.reflect.Method

internal fun discoverLegacyMediaClassRegistry(
    classLoader: ClassLoader,
    scanner: DexClassScanner,
    common: KakaoRegistryCommonParts,
): KakaoClassRegistry? {
    val legacyMediaSender =
        runCatching {
            discoverChatMediaSender(
                classLoader,
                scanner,
                common.messageTypeClass,
                common.function0Class,
                common.function1Class,
            )
        }.getOrNull()
            ?: return null
    val registry = buildLegacyMediaRegistry(common, legacyMediaSender)
    logDiscoveryComplete(
        legacyMediaSender,
        common.messageTypeClass,
        registry.writeTypeClass,
        registry.listenerClass,
        common.chatRoomManagerClass,
        common.chatRoomClass,
    )
    return registry
}

private fun buildLegacyMediaRegistry(
    common: KakaoRegistryCommonParts,
    legacyMediaSender: Class<*>,
): KakaoClassRegistry {
    val (singleSend, multiSend) =
        resolveChatMediaSendMethods(
            chatMediaSenderClass = legacyMediaSender,
            mediaItemClass = common.mediaItemClass,
            messageTypeClass = common.messageTypeClass,
        )
    val writeType = deriveWriteType(multiSend)
    val listener = deriveListener(multiSend)
    return KakaoClassRegistry(
        target = common.target,
        mediaItemClass = common.mediaItemClass,
        function0Class = common.function0Class,
        function1Class = common.function1Class,
        masterDatabaseClass = common.masterDatabaseClass,
        writeTypeClass = writeType,
        listenerClass = listener,
        chatMediaSenderClass = legacyMediaSender,
        messageTypeClass = common.messageTypeClass,
        chatRoomManagerClass = common.chatRoomManagerClass,
        chatRoomClass = common.chatRoomClass,
        singleSendMethod = singleSend,
        multiSendMethod = multiSend,
        mediaItemConstructor =
            common.mediaItemClass?.let {
                runCatching { it.getConstructor(String::class.java, Long::class.javaPrimitiveType) }.getOrNull()
            },
        masterDbSingletonField = common.masterDbSingletonField,
        roomDaoMethod = common.roomDaoMethod,
        entityLookupMethod = common.entityLookupMethod,
        broadRoomResolverMethod = common.broadRoomResolverMethod,
        directRoomResolverMethod = common.directRoomResolverMethod,
        photoType = common.photoType,
        multiPhotoType = common.multiPhotoType,
        writeTypeNone = requireEnumConstant(writeType, "None"),
        imageSendStrategy = KakaoImageSendStrategy.LEGACY_REFLECTION,
    )
}

private fun deriveWriteType(multiSend: Method): Class<*> =
    multiSend.parameterTypes[5].also { derived ->
        check(derived.isEnum) {
            "WriteType derived from multiSend param[5] is not an enum: ${derived.name}"
        }
        Log.i(KAKAO_CLASS_REGISTRY_TAG, "WriteType derived from multiSend signature: ${derived.name}")
    }

private fun deriveListener(multiSend: Method): Class<*> =
    multiSend.parameterTypes[8].also { derived ->
        check(derived.isInterface) {
            "Listener derived from multiSend param[8] is not an interface: ${derived.name}"
        }
        Log.i(KAKAO_CLASS_REGISTRY_TAG, "Listener derived from multiSend signature: ${derived.name}")
    }
