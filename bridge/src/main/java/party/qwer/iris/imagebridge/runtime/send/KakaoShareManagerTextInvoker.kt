package party.qwer.iris.imagebridge.runtime.send

import android.content.Context
import party.qwer.iris.imagebridge.runtime.kakao.classregistry.selectMethodBySignature
import party.qwer.iris.imagebridge.runtime.kakao.classregistry.resolveShareManagerSingleton
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal class KakaoShareManagerTextInvoker(
    private val target: Any,
    private val method: Method,
    private val context: Context,
) {
    fun invoke(
        chatRoom: Any,
        message: String,
    ) {
        method.apply { isAccessible = true }.invoke(
            target,
            context,
            chatRoom,
            message,
            false,
            null,
        )
    }
}

internal fun discoverShareManagerTextInvoker(
    context: Context?,
    chatRoomClass: Class<*>,
    listenerClass: Class<*>,
    logInfo: (String, String) -> Unit,
): KakaoShareManagerTextInvoker? {
    val appContext = context ?: return null
    val shareManagerClass =
        runCatching {
            Class.forName(SHARE_MANAGER_CLASS, false, chatRoomClass.classLoader)
        }.getOrNull() ?: return null
    val target =
        runCatching { resolveShareManagerSingleton(shareManagerClass) }.getOrNull()
            ?: return null
    val method =
        runCatching {
            selectMethodBySignature(
                label = "ShareManager text send on ${shareManagerClass.name}",
                candidates =
                    shareManagerClass.methods.filter { method ->
                        val types = method.parameterTypes
                        types.size == 5 &&
                            types[0].isAssignableFrom(appContext.javaClass) &&
                            types[1].isAssignableFrom(chatRoomClass) &&
                            types[2] == String::class.java &&
                            types[3] == Boolean::class.javaPrimitiveType &&
                            types[4].isAssignableFrom(listenerClass)
                    },
                preferredNames = setOf("g0"),
            )
        }.getOrNull()
            ?: return null
    logInfo(KAKAO_TEXT_SEND_TAG, "text send discovery shareManagerMethod=${method.toGenericString()}")
    return KakaoShareManagerTextInvoker(target, method, appContext)
}

internal fun createShareManagerSendListener(
    context: Context?,
    loader: ClassLoader?,
    listenerClass: Class<*>,
    logInfo: (String, String) -> Unit,
): Any? {
    val appContext = context ?: return null
    val listenerWrapperClass =
        runCatching {
            Class.forName("$SHARE_MANAGER_CLASS\$i", false, loader)
        }.getOrNull() ?: return null
    val constructor =
        listenerWrapperClass.declaredConstructors
            .firstOrNull { constructor ->
                val types = constructor.parameterTypes
                types.size == 3 &&
                    types[0].isAssignableFrom(appContext.javaClass) &&
                    types[1] == Boolean::class.javaPrimitiveType &&
                    types[2].isAssignableFrom(listenerClass)
            } ?: return null
    return runCatching {
        constructor.apply { isAccessible = true }.newInstance(appContext, false, null)
    }.onSuccess {
        logInfo(KAKAO_TEXT_SEND_TAG, "text send discovery shareManager listener=${listenerWrapperClass.name}")
    }.getOrNull()
}
