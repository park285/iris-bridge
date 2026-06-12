@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime.kakao.classregistry

import android.util.Log
import party.qwer.iris.imagebridge.runtime.kakao.DexClassScanner
import party.qwer.iris.imagebridge.runtime.kakao.KakaoClassRegistry
import party.qwer.iris.imagebridge.runtime.kakao.KakaoTalkTargetContext

internal fun discoverKakaoClassRegistry(
    classLoader: ClassLoader,
    target: KakaoTalkTargetContext,
): KakaoClassRegistry {
    Log.i(KAKAO_CLASS_REGISTRY_TAG, "KakaoClassRegistry.discover start")
    val scanner = DexClassScanner(classLoader)
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
    val common =
        KakaoRegistryCommonParts(
            target = target,
            mediaItemClass = mediaItem,
            function0Class = function0,
            function1Class = function1,
            masterDatabaseClass = masterDb,
            messageTypeClass = messageType,
            chatRoomManagerClass = chatRoomManager,
            chatRoomClass = chatRoom,
            broadRoomResolverMethod = broadResolver,
            directRoomResolverMethod = directResolver,
            masterDbSingletonField = resolveMasterDatabaseSingleton(masterDb),
            roomDaoMethod = roomDao,
            entityLookupMethod = entityLookup,
            photoType = requireEnumConstant(messageType, "Photo"),
            multiPhotoType = requireEnumConstant(messageType, "MultiPhoto"),
            videoType = requireEnumConstant(messageType, "Video"),
        )
    return discoverLegacyMediaClassRegistry(classLoader, scanner, common)
        ?: discoverShareManagerClassRegistry(classLoader, scanner, common)
}
