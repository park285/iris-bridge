@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime.kakao.classregistry

import android.util.Log
import party.qwer.iris.imagebridge.runtime.kakao.DexClassScanner
import party.qwer.iris.imagebridge.runtime.kakao.KakaoClassRegistry
import party.qwer.iris.imagebridge.runtime.kakao.KakaoImageSendStrategy
import party.qwer.iris.imagebridge.runtime.kakao.KakaoTalkTargetContext
import party.qwer.iris.imagebridge.runtime.send.discoverShareManagerImageMethods

internal fun discoverKakaoClassRegistry(
    classLoader: ClassLoader,
    target: KakaoTalkTargetContext,
): KakaoClassRegistry {
    Log.i(KAKAO_CLASS_REGISTRY_TAG, "KakaoClassRegistry.discover start")
    val scanner = DexClassScanner(classLoader)
    val dexPackage = target.dexPackage
    val mediaItem = runCatching { stableClass(classLoader, target.dexClassName("model.media.MediaItem")) }.getOrNull()
    val function0 = stableClass(classLoader, "kotlin.jvm.functions.Function0")
    val function1 = stableClass(classLoader, "kotlin.jvm.functions.Function1")
    val masterDb = stableClass(classLoader, target.dexClassName("database.MasterDatabase"))
    val messageType = discoverMessageType(classLoader, scanner)
    val chatRoomManager = discoverChatRoomManager(classLoader, scanner)
    val broadResolver = resolveBroadRoomResolver(chatRoomManager)
    val chatRoom = broadResolver.returnType
    Log.i(KAKAO_CLASS_REGISTRY_TAG, "ChatRoom derived as ${chatRoom.name}")
    val directResolver = resolveDirectRoomResolver(chatRoomManager, broadResolver, chatRoom)
    val roomDao = resolveRoomDao(masterDb)
    val entityLookup = resolveEntityLookup(roomDao.returnType)
    val masterDbSingletonField = resolveMasterDatabaseSingleton(masterDb)
    val photoType = requireEnumConstant(messageType, "Photo")
    val multiPhotoType = requireEnumConstant(messageType, "MultiPhoto")

    val legacyMediaSender =
        runCatching {
            discoverChatMediaSender(classLoader, scanner, messageType, function0, function1)
        }.getOrNull()

    if (legacyMediaSender != null) {
        val (singleSend, multiSend) =
            resolveChatMediaSendMethods(
                chatMediaSenderClass = legacyMediaSender,
                mediaItemClass = mediaItem,
                messageTypeClass = messageType,
            )
        val writeType = deriveWriteType(multiSend)
        val listener = deriveListener(multiSend)
        return KakaoClassRegistry(
            target = target,
            mediaItemClass = mediaItem,
            function0Class = function0,
            function1Class = function1,
            masterDatabaseClass = masterDb,
            writeTypeClass = writeType,
            listenerClass = listener,
            chatMediaSenderClass = legacyMediaSender,
            messageTypeClass = messageType,
            chatRoomManagerClass = chatRoomManager,
            chatRoomClass = chatRoom,
            singleSendMethod = singleSend,
            multiSendMethod = multiSend,
            mediaItemConstructor = mediaItem?.let { runCatching { it.getConstructor(String::class.java, Long::class.javaPrimitiveType) }.getOrNull() },
            masterDbSingletonField = masterDbSingletonField,
            roomDaoMethod = roomDao,
            entityLookupMethod = entityLookup,
            broadRoomResolverMethod = broadResolver,
            directRoomResolverMethod = directResolver,
            photoType = photoType,
            multiPhotoType = multiPhotoType,
            writeTypeNone = requireEnumConstant(writeType, "None"),
            imageSendStrategy = KakaoImageSendStrategy.LEGACY_REFLECTION,
        ).also {
            logDiscoveryComplete(legacyMediaSender, messageType, writeType, listener, chatRoomManager, chatRoom)
        }
    }

    Log.w(KAKAO_CLASS_REGISTRY_TAG, "ChatMediaSender not found, falling back to ShareManager image path")
    val shareManagerClass = discoverShareManagerClass(classLoader, scanner, dexPackage)
    val listener = discoverSendListenerClass(classLoader, scanner, shareManagerClass, chatRoom, dexPackage)
    val writeType = discoverModernWriteType(classLoader, scanner, dexPackage)
    val (intentMethod, dispatchMethod) = discoverShareManagerImageMethods(shareManagerClass, chatRoom, messageType, listener)
    return KakaoClassRegistry(
        target = target,
        mediaItemClass = mediaItem,
        function0Class = function0,
        function1Class = function1,
        masterDatabaseClass = masterDb,
        writeTypeClass = writeType,
        listenerClass = listener,
        chatMediaSenderClass = shareManagerClass,
        messageTypeClass = messageType,
        chatRoomManagerClass = chatRoomManager,
        chatRoomClass = chatRoom,
        singleSendMethod = null,
        multiSendMethod = intentMethod,
        mediaItemConstructor = mediaItem?.let { runCatching { it.getConstructor(String::class.java, Long::class.javaPrimitiveType) }.getOrNull() },
        masterDbSingletonField = masterDbSingletonField,
        roomDaoMethod = roomDao,
        entityLookupMethod = entityLookup,
        broadRoomResolverMethod = broadResolver,
        directRoomResolverMethod = directResolver,
        photoType = photoType,
        multiPhotoType = multiPhotoType,
        writeTypeNone = requireEnumConstant(writeType, "None"),
        imageSendStrategy = KakaoImageSendStrategy.SHARE_MANAGER_INTENT,
        shareManagerImageIntentMethod = intentMethod,
        shareManagerImageDispatchMethod = dispatchMethod,
    ).also {
        logDiscoveryComplete(shareManagerClass, messageType, writeType, listener, chatRoomManager, chatRoom)
    }
}

private fun deriveWriteType(multiSend: java.lang.reflect.Method): Class<*> =
    multiSend.parameterTypes[5].also { derived ->
        check(derived.isEnum) {
            "WriteType derived from multiSend param[5] is not an enum: ${derived.name}"
        }
        Log.i(KAKAO_CLASS_REGISTRY_TAG, "WriteType derived from multiSend signature: ${derived.name}")
    }

private fun deriveListener(multiSend: java.lang.reflect.Method): Class<*> =
    multiSend.parameterTypes[8].also { derived ->
        check(derived.isInterface) {
            "Listener derived from multiSend param[8] is not an interface: ${derived.name}"
        }
        Log.i(KAKAO_CLASS_REGISTRY_TAG, "Listener derived from multiSend signature: ${derived.name}")
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
