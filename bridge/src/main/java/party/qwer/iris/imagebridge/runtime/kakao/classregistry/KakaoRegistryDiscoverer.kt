@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime.kakao.classregistry

import android.util.Log
import party.qwer.iris.imagebridge.runtime.kakao.DexClassScanner
import party.qwer.iris.imagebridge.runtime.kakao.KakaoClassRegistry

internal fun discoverKakaoClassRegistry(classLoader: ClassLoader): KakaoClassRegistry {
    Log.i(KAKAO_CLASS_REGISTRY_TAG, "KakaoClassRegistry.discover start")
    val scanner = DexClassScanner(classLoader)
    val mediaItem = runCatching { stableClass(classLoader, "com.kakao.talk.model.media.MediaItem") }.getOrNull()
    val function0 = stableClass(classLoader, "kotlin.jvm.functions.Function0")
    val function1 = stableClass(classLoader, "kotlin.jvm.functions.Function1")
    val masterDb = stableClass(classLoader, "com.kakao.talk.database.MasterDatabase")
    val messageType = discoverMessageType(classLoader, scanner)
    val chatMediaSender = discoverChatMediaSender(classLoader, scanner, messageType, function0, function1)
    val chatRoomManager = discoverChatRoomManager(classLoader, scanner)
    val broadResolver = resolveBroadRoomResolver(chatRoomManager)
    val chatRoom = broadResolver.returnType
    Log.i(KAKAO_CLASS_REGISTRY_TAG, "ChatRoom derived as ${chatRoom.name}")
    val directResolver = resolveDirectRoomResolver(chatRoomManager, broadResolver, chatRoom)
    val (singleSend, multiSend) =
        resolveChatMediaSendMethods(
            chatMediaSenderClass = chatMediaSender,
            mediaItemClass = mediaItem,
            messageTypeClass = messageType,
        )
    val writeType = deriveWriteType(multiSend)
    val listener = deriveListener(multiSend)
    val roomDao = resolveRoomDao(masterDb)
    val entityLookup = resolveEntityLookup(roomDao.returnType)
    return KakaoClassRegistry(
        mediaItemClass = mediaItem,
        function0Class = function0,
        function1Class = function1,
        masterDatabaseClass = masterDb,
        writeTypeClass = writeType,
        listenerClass = listener,
        chatMediaSenderClass = chatMediaSender,
        messageTypeClass = messageType,
        chatRoomManagerClass = chatRoomManager,
        chatRoomClass = chatRoom,
        singleSendMethod = singleSend,
        multiSendMethod = multiSend,
        mediaItemConstructor = mediaItem?.let { runCatching { it.getConstructor(String::class.java, Long::class.javaPrimitiveType) }.getOrNull() },
        masterDbSingletonField = resolveMasterDatabaseSingleton(masterDb),
        roomDaoMethod = roomDao,
        entityLookupMethod = entityLookup,
        broadRoomResolverMethod = broadResolver,
        directRoomResolverMethod = directResolver,
        photoType = requireEnumConstant(messageType, "Photo"),
        multiPhotoType = requireEnumConstant(messageType, "MultiPhoto"),
        writeTypeNone = requireEnumConstant(writeType, "None"),
    ).also {
        logDiscoveryComplete(chatMediaSender, messageType, writeType, listener, chatRoomManager, chatRoom)
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
