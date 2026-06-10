@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime.kakao.classregistry

import android.util.Log
import party.qwer.iris.imagebridge.runtime.kakao.DexClassScanner
import party.qwer.iris.imagebridge.runtime.kakao.KakaoClassRegistry
import party.qwer.iris.imagebridge.runtime.kakao.KakaoImageSendStrategy
import party.qwer.iris.imagebridge.runtime.kakao.KakaoTalkTargetContext
import party.qwer.iris.imagebridge.runtime.send.discoverShareManagerImageMethods
import java.lang.reflect.Field
import java.lang.reflect.Method

internal data class KakaoRegistryCommonParts(
    val target: KakaoTalkTargetContext,
    val mediaItemClass: Class<*>?,
    val function0Class: Class<*>,
    val function1Class: Class<*>,
    val masterDatabaseClass: Class<*>,
    val messageTypeClass: Class<*>,
    val chatRoomManagerClass: Class<*>,
    val chatRoomClass: Class<*>,
    val broadRoomResolverMethod: Method,
    val directRoomResolverMethod: Method,
    val masterDbSingletonField: Field,
    val roomDaoMethod: Method,
    val entityLookupMethod: Method,
    val photoType: Any,
    val multiPhotoType: Any,
)

internal fun discoverShareManagerClassRegistry(
    classLoader: ClassLoader,
    scanner: DexClassScanner,
    common: KakaoRegistryCommonParts,
): KakaoClassRegistry {
    val dexPackage = common.target.dexPackage
    Log.w(KAKAO_CLASS_REGISTRY_TAG, "ChatMediaSender not found, falling back to ShareManager image path")
    val shareManagerClass = discoverShareManagerClass(classLoader, scanner, dexPackage)
    val listener = discoverSendListenerClass(classLoader, scanner, shareManagerClass, common.chatRoomClass, dexPackage)
    val writeType = discoverModernWriteType(classLoader, scanner, dexPackage)
    val (intentMethod, dispatchMethod) =
        discoverShareManagerImageMethods(
            shareManagerClass,
            common.chatRoomClass,
            common.messageTypeClass,
            listener,
        )
    return KakaoClassRegistry(
        target = common.target,
        mediaItemClass = common.mediaItemClass,
        function0Class = common.function0Class,
        function1Class = common.function1Class,
        masterDatabaseClass = common.masterDatabaseClass,
        writeTypeClass = writeType,
        listenerClass = listener,
        chatMediaSenderClass = shareManagerClass,
        messageTypeClass = common.messageTypeClass,
        chatRoomManagerClass = common.chatRoomManagerClass,
        chatRoomClass = common.chatRoomClass,
        singleSendMethod = null,
        multiSendMethod = intentMethod,
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
        imageSendStrategy = KakaoImageSendStrategy.SHARE_MANAGER_INTENT,
        shareManagerImageIntentMethod = intentMethod,
        shareManagerImageDispatchMethod = dispatchMethod,
    ).also {
        logDiscoveryComplete(
            shareManagerClass,
            common.messageTypeClass,
            writeType,
            listener,
            common.chatRoomManagerClass,
            common.chatRoomClass,
        )
    }
}

private fun discoverModernWriteType(
    classLoader: ClassLoader,
    scanner: DexClassScanner,
    dexPackage: String,
): Class<*> {
    runCatching {
        val requestWriteType = stableClass(classLoader, "$dexPackage.manager.send.ChatSendingLogRequest\$c")
        if (hasEnumConstants(requestWriteType, "None", "Connect")) {
            Log.i(KAKAO_CLASS_REGISTRY_TAG, "WriteType resolved from ChatSendingLogRequest\$c")
            return requestWriteType
        }
    }
    val scanned =
        scanner.findAll { clazz ->
            clazz.isEnum && hasEnumConstants(clazz, "None")
        }
    if (scanned.size == 1) {
        Log.i(KAKAO_CLASS_REGISTRY_TAG, "WriteType resolved from DEX scan: ${scanned.single().name}")
        return scanned.single()
    }
    runCatching {
        val legacyWriteType = Class.forName("G1.b", false, classLoader)
        if (hasEnumConstants(legacyWriteType, "None")) {
            Log.i(KAKAO_CLASS_REGISTRY_TAG, "WriteType resolved from G1.b")
            return legacyWriteType
        }
    }
    error("modern WriteType enum not found")
}
