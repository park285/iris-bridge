package party.qwer.iris.imagebridge.runtime.kakao.classregistry

import party.qwer.iris.imagebridge.runtime.kakao.DexClassScanner

internal fun discoverMessageType(
    classLoader: ClassLoader,
    scanner: DexClassScanner,
): Class<*> =
    discoverClass(
        classLoader,
        scanner,
        lastKnownNames = arrayOf("vr.c", "Op.EnumC16810c", "Op.c"),
        label = "MessageType",
    ) { clazz ->
        clazz.isEnum && hasEnumConstants(clazz, "Photo", "MultiPhoto", "Video")
    }

internal fun discoverChatMediaSender(
    classLoader: ClassLoader,
    scanner: DexClassScanner,
    messageType: Class<*>,
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
            messageTypeClass = messageType,
            function0Class = function0,
            function1Class = function1,
        )
    }

internal fun discoverChatRoomManager(
    classLoader: ClassLoader,
    scanner: DexClassScanner,
): Class<*> =
    discoverClass(
        classLoader,
        scanner,
        lastKnownNames = arrayOf("Kq.U0", "hp.J0"),
        label = "ChatRoomManager",
    ) { clazz ->
        hasSelfReturningAccessor(clazz) && clazz.declaredMethods.any(::isBroadRoomResolverSignature)
    }
