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
        contentTypes: List<String> = emptyList(),
    ) {
        val method =
            runCatching { entryMethod }
                .getOrElse { error ->
                    throw IllegalStateException("threaded entry method not ready", error)
                }
        val uris = mediaUris(imagePaths, pathArgumentFactory)
        val normalizedContentTypes = normalizeMediaContentTypes(imagePaths, contentTypes)
        val type = mediaMessageType(registry, imagePaths, normalizedContentTypes)
        val callingPkgAttachment = callingPackageAttachment(registry.target.packageName)
        val loader = registry.chatMediaSenderClass.classLoader ?: error("no classLoader")
        val identityFunctionProxy = identityFunctionProxy(loader, registry.function1Class)
        val noopFunctionProxy = noopFunctionProxy(loader, registry.function1Class)
        val writeTypeConnect = writeTypeConnect(registry)

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

private fun mediaUris(
    imagePaths: List<String>,
    pathArgumentFactory: (String) -> Any,
): ArrayList<Any> =
    ArrayList<Any>(imagePaths.size).apply {
        imagePaths.forEach { path -> add(pathArgumentFactory(path)) }
    }

private fun callingPackageAttachment(packageName: String): JSONObject =
    JSONObject().apply {
        put("callingPkg", packageName)
    }

private fun identityFunctionProxy(
    loader: ClassLoader,
    function1Class: Class<*>,
): Any =
    Proxy.newProxyInstance(loader, arrayOf(function1Class)) { _, method, args ->
        when (method.name) {
            "invoke" -> args?.getOrNull(0)
            "toString" -> "IrisBridgeIdentityFunction"
            "hashCode" -> 0
            "equals" -> false
            else -> null
        }
    }

private fun noopFunctionProxy(
    loader: ClassLoader,
    function1Class: Class<*>,
): Any =
    Proxy.newProxyInstance(loader, arrayOf(function1Class)) { _, method, _ ->
        when (method.name) {
            "invoke" -> null
            "toString" -> "IrisBridgeNoopFunction"
            "hashCode" -> 0
            "equals" -> false
            else -> null
        }
    }

private fun writeTypeConnect(registry: KakaoClassRegistry): Any =
    registry.writeTypeClass.enumConstants?.firstOrNull { constant ->
        (constant as Enum<*>).name == "Connect"
    } ?: registry.writeTypeNone

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
