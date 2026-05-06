package party.qwer.iris.imagebridge.runtime.send

import android.content.Context
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
        shareManagerClass.declaredFields
            .asSequence()
            .filter { field -> Modifier.isStatic(field.modifiers) && shareManagerClass.isAssignableFrom(field.type) }
            .mapNotNull { field ->
                runCatching {
                    field.isAccessible = true
                    field.get(null)
                }.getOrNull()
            }.firstOrNull()
            ?: return null
    val method =
        shareManagerClass.methods
            .filter { method ->
                val types = method.parameterTypes
                types.size == 5 &&
                    types[0].isAssignableFrom(appContext.javaClass) &&
                    types[1].isAssignableFrom(chatRoomClass) &&
                    types[2] == String::class.java &&
                    types[3] == Boolean::class.javaPrimitiveType &&
                    types[4].isAssignableFrom(listenerClass)
            }.minWithOrNull(compareBy<Method> { if (it.name == "g0") 0 else 1 }.thenBy { it.name })
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
