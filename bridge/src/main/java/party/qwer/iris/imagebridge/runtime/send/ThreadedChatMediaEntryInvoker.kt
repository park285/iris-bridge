package party.qwer.iris.imagebridge.runtime.send

import org.json.JSONObject
import party.qwer.iris.imagebridge.runtime.discovery.HOOK_SEND_THREADED_ENTRY
import party.qwer.iris.imagebridge.runtime.discovery.defaultBridgeDiscovery
import party.qwer.iris.imagebridge.runtime.kakao.KakaoClassRegistry
import java.lang.reflect.Method
import java.lang.reflect.Proxy

internal class ThreadedChatMediaEntryInvoker(
    private val registry: KakaoClassRegistry,
    private val pathArgumentFactory: (String) -> Any,
) {
    private val entryMethod: Method by lazy {
        selectThreadedChatMediaEntryMethod(
            chatMediaSenderClass = registry.chatMediaSenderClass,
            messageTypeClass = registry.messageTypeClass,
            writeTypeClass = registry.writeTypeClass,
            function1Class = registry.function1Class,
        )
    }

    fun warmUp() {
        runCatching { entryMethod }
            .onSuccess {
                defaultBridgeDiscovery.markInstalled(HOOK_SEND_THREADED_ENTRY)
            }.onFailure { error ->
                defaultBridgeDiscovery.markInstallError(HOOK_SEND_THREADED_ENTRY, error.message ?: error.javaClass.name)
            }
    }

    fun invoke(
        sender: Any,
        imagePaths: List<String>,
    ) {
        val method =
            runCatching { entryMethod }
                .getOrElse { error ->
                    throw IllegalStateException("threaded entry method not ready", error)
                }
        val uris =
            ArrayList<Any>(imagePaths.size).apply {
                imagePaths.forEach { path -> add(pathArgumentFactory(path)) }
            }
        val type = if (imagePaths.size == 1) registry.photoType else registry.multiPhotoType
        val callingPkgAttachment =
            JSONObject().apply {
                put("callingPkg", registry.target.packageName)
            }
        val loader = registry.chatMediaSenderClass.classLoader ?: error("no classLoader")
        val identityFunctionProxy =
            Proxy.newProxyInstance(loader, arrayOf(registry.function1Class)) { _, method, args ->
                when (method.name) {
                    "invoke" -> args?.getOrNull(0)
                    "toString" -> "IrisBridgeIdentityFunction"
                    "hashCode" -> 0
                    "equals" -> false
                    else -> null
                }
            }
        val noopFunctionProxy =
            Proxy.newProxyInstance(loader, arrayOf(registry.function1Class)) { _, method, _ ->
                when (method.name) {
                    "invoke" -> null
                    "toString" -> "IrisBridgeNoopFunction"
                    "hashCode" -> 0
                    "equals" -> false
                    else -> null
                }
            }
        val writeTypeConnect =
            registry.writeTypeClass.enumConstants?.firstOrNull { constant ->
                (constant as Enum<*>).name == "Connect"
            } ?: registry.writeTypeNone

        defaultBridgeDiscovery.recordHook(HOOK_SEND_THREADED_ENTRY, "uris=${uris.size} type=$type")
        method.invoke(
            sender,
            uris,
            type,
            "",
            callingPkgAttachment,
            null,
            writeTypeConnect,
            false,
            true,
            identityFunctionProxy,
            noopFunctionProxy,
        )
    }
}

internal fun selectThreadedChatMediaEntryMethodForTest(
    chatMediaSenderClass: Class<*>,
    messageTypeClass: Class<*>,
    writeTypeClass: Class<*>,
    function1Class: Class<*>,
): Method =
    selectThreadedChatMediaEntryMethod(
        chatMediaSenderClass = chatMediaSenderClass,
        messageTypeClass = messageTypeClass,
        writeTypeClass = writeTypeClass,
        function1Class = function1Class,
    )

private fun selectThreadedChatMediaEntryMethod(
    chatMediaSenderClass: Class<*>,
    messageTypeClass: Class<*>,
    writeTypeClass: Class<*>,
    function1Class: Class<*>,
): Method =
    KakaoClassRegistry.selectMethodCandidateForTest(
        label = "ChatMediaSender threaded entry on ${chatMediaSenderClass.name}",
        candidates =
            methodsInHierarchy(chatMediaSenderClass).filter { method ->
                !java.lang.reflect.Modifier
                    .isStatic(method.modifiers) &&
                    method.parameterCount == 10 &&
                    method.parameterTypes[0] == List::class.java &&
                    method.parameterTypes[1] == messageTypeClass &&
                    method.parameterTypes[5] == writeTypeClass &&
                    method.parameterTypes[8] == function1Class &&
                    method.parameterTypes[9] == function1Class
            },
        preferredNames = setOf("o"),
    )

private fun methodsInHierarchy(clazz: Class<*>): List<Method> {
    val methods = mutableListOf<Method>()
    var current: Class<*>? = clazz
    while (current != null && current != Any::class.java) {
        current.declaredMethods.forEach { method ->
            runCatching { method.isAccessible = true }
            methods += method
        }
        current = current.superclass
    }
    return methods
}
