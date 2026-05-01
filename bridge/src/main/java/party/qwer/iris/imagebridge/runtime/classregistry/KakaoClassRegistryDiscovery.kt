@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime

import android.util.Log
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object KakaoClassRegistryDiscovery {
    fun discover(classLoader: ClassLoader): KakaoClassRegistry {
        Log.i(KAKAO_CLASS_REGISTRY_TAG, "KakaoClassRegistry.discover start")
        val scanner = DexClassScanner(classLoader)

        val mediaItem = stableClass(classLoader, "com.kakao.talk.model.media.MediaItem")
        val function0 = stableClass(classLoader, "kotlin.jvm.functions.Function0")
        val function1 = stableClass(classLoader, "kotlin.jvm.functions.Function1")
        val masterDb = stableClass(classLoader, "com.kakao.talk.database.MasterDatabase")

        val messageType =
            discoverMessageType(classLoader, scanner)
        val chatMediaSender =
            discoverChatMediaSender(classLoader, scanner, mediaItem, function0, function1)
        val chatRoomManager =
            discoverChatRoomManager(classLoader, scanner)

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

        val writeType =
            multiSend.parameterTypes[5].also { derived ->
                check(derived.isEnum) {
                    "WriteType derived from multiSend param[5] is not an enum: ${derived.name}"
                }
                Log.i(KAKAO_CLASS_REGISTRY_TAG, "WriteType derived from multiSend signature: ${derived.name}")
            }
        val listener =
            multiSend.parameterTypes[8].also { derived ->
                check(derived.isInterface) {
                    "Listener derived from multiSend param[8] is not an interface: ${derived.name}"
                }
                Log.i(KAKAO_CLASS_REGISTRY_TAG, "Listener derived from multiSend signature: ${derived.name}")
            }

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
            mediaItemConstructor = mediaItem.getConstructor(String::class.java, Long::class.javaPrimitiveType),
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

    private fun discoverMessageType(
        classLoader: ClassLoader,
        scanner: DexClassScanner,
    ): Class<*> =
        discoverClass(
            classLoader,
            scanner,
            lastKnownNames = arrayOf("Op.EnumC16810c", "Op.c"),
            label = "MessageType",
        ) { clazz ->
            clazz.isEnum && hasEnumConstants(clazz, "Photo", "MultiPhoto")
        }

    private fun discoverChatMediaSender(
        classLoader: ClassLoader,
        scanner: DexClassScanner,
        mediaItem: Class<*>,
        function0: Class<*>,
        function1: Class<*>,
    ): Class<*> =
        discoverClass(
            classLoader,
            scanner,
            lastKnownNames = arrayOf("bh.c"),
            label = "ChatMediaSender",
        ) { clazz ->
            matchesChatMediaSenderClass(
                clazz = clazz,
                mediaItemClass = mediaItem,
                function0Class = function0,
                function1Class = function1,
            )
        }

    private fun discoverChatRoomManager(
        classLoader: ClassLoader,
        scanner: DexClassScanner,
    ): Class<*> =
        discoverClass(
            classLoader,
            scanner,
            lastKnownNames = arrayOf("hp.J0"),
            label = "ChatRoomManager",
        ) { clazz ->
            hasSelfReturningAccessor(clazz) && clazz.declaredMethods.any(::isBroadRoomResolverSignature)
        }

    private fun resolveBroadRoomResolver(chatRoomManager: Class<*>) =
        selectMethodCandidate(
            label = "ChatRoomManager broad resolver on ${chatRoomManager.name}",
            candidates = chatRoomManager.declaredMethods.filter(::isBroadRoomResolverSignature),
            preferredNames = setOf("e0"),
        )

    private fun resolveDirectRoomResolver(
        chatRoomManager: Class<*>,
        broadResolver: java.lang.reflect.Method,
        chatRoom: Class<*>,
    ) = selectMethodCandidate(
        label = "ChatRoomManager direct resolver on ${chatRoomManager.name}",
        candidates =
            chatRoomManager.declaredMethods.filter { method ->
                method != broadResolver &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.contentEquals(arrayOf(Long::class.javaPrimitiveType)) &&
                    chatRoom.isAssignableFrom(method.returnType)
            },
        preferredNames = setOf("d0"),
    )

    private fun resolveMasterDatabaseSingleton(masterDb: Class<*>) =
        selectFieldCandidate(
            label = "MasterDatabase singleton field on ${masterDb.name}",
            candidates =
                masterDb.declaredFields.filter { field ->
                    Modifier.isStatic(field.modifiers) && field.type == masterDb
                },
        ).apply { isAccessible = true }

    private fun resolveRoomDao(masterDb: Class<*>) =
        selectMethodCandidate(
            label = "MasterDatabase roomDao accessor on ${masterDb.name}",
            candidates =
                masterDb.methods.filter { method ->
                    !Modifier.isStatic(method.modifiers) &&
                        method.parameterCount == 0 &&
                        method.returnType != Void.TYPE &&
                        method.returnType != masterDb &&
                        method.returnType != Any::class.java &&
                        method.returnType.methods.any { daoMethod ->
                            isRoomEntityLookupSignature(daoMethod, method.returnType)
                        }
                },
            preferredNames = setOf("O"),
        )

    private fun resolveEntityLookup(daoClass: Class<*>) =
        selectMethodCandidate(
            label = "RoomDao entity lookup on ${daoClass.name}",
            candidates = daoClass.methods.filter { method -> isRoomEntityLookupSignature(method, daoClass) },
            preferredNames = setOf("h"),
        )

    private fun isBroadRoomResolverSignature(method: Method): Boolean =
        !Modifier.isStatic(method.modifiers) &&
            method.parameterTypes.contentEquals(
                arrayOf(
                    Long::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType,
                ),
            )

    private fun isRoomEntityLookupSignature(
        method: Method,
        daoClass: Class<*>,
    ): Boolean =
        !Modifier.isStatic(method.modifiers) &&
            method.parameterTypes.contentEquals(arrayOf(Long::class.javaPrimitiveType)) &&
            !method.returnType.isPrimitive &&
            method.returnType != Void.TYPE &&
            method.returnType != daoClass &&
            method.returnType != Any::class.java &&
            method.returnType != java.lang.Integer::class.java &&
            method.returnType != java.lang.Long::class.java &&
            method.returnType != java.lang.Boolean::class.java &&
            method.returnType != String::class.java

    private fun logDiscoveryComplete(
        chatMediaSender: Class<*>,
        messageType: Class<*>,
        writeType: Class<*>,
        listener: Class<*>,
        chatRoomManager: Class<*>,
        chatRoom: Class<*>,
    ) {
        Log.i(
            KAKAO_CLASS_REGISTRY_TAG,
            "KakaoClassRegistry.discover complete — " +
                "ChatMediaSender=${chatMediaSender.name}, " +
                "MessageType=${messageType.name}, " +
                "WriteType=${writeType.name}, " +
                "Listener=${listener.name}, " +
                "ChatRoomManager=${chatRoomManager.name}, " +
                "ChatRoom=${chatRoom.name}",
        )
    }
}
