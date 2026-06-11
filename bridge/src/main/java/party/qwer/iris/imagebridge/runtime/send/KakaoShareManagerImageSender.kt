package party.qwer.iris.imagebridge.runtime.send

import android.util.Log
import party.qwer.iris.imagebridge.runtime.kakao.KakaoClassRegistry
import party.qwer.iris.imagebridge.runtime.kakao.classregistry.resolveShareManagerSingleton
import party.qwer.iris.imagebridge.runtime.kakao.classregistry.selectMethodBySignature
import java.lang.reflect.Modifier
import java.util.ArrayList

internal class KakaoShareManagerImageSender(
    private val registry: KakaoClassRegistry,
    private val logInfo: (String, String) -> Unit = { tag, message -> Log.i(tag, message) },
) {
    fun send(
        chatRoom: Any,
        imagePaths: List<String>,
        pathArgumentFactory: (String) -> Any,
    ) {
        require(imagePaths.isNotEmpty()) { "no image paths" }
        val shareManagerClass = registry.chatMediaSenderClass
        val target = resolveShareManagerSingleton(shareManagerClass)
        val messageType = if (imagePaths.size == 1) registry.photoType else registry.multiPhotoType
        val mediaPaths =
            ArrayList<Any>(imagePaths.size).apply {
                imagePaths.forEach { path -> add(pathArgumentFactory(path)) }
            }
        val intentMethod =
            registry.shareManagerImageIntentMethod
                ?: error("ShareManager image intent method not discovered")
        val intent =
            intentMethod.apply { isAccessible = true }.invoke(target, mediaPaths, messageType)
                ?: error("ShareManager image intent builder returned null")
        val dispatchMethod =
            registry.shareManagerImageDispatchMethod
                ?: error("ShareManager image dispatch method not discovered")
        dispatchMethod.apply { isAccessible = true }.invoke(target, null, intent, chatRoom, false)
        logInfo(
            KAKAO_TEXT_SEND_TAG,
            "image send shareManager path images=${imagePaths.size} chatRoom=${chatRoom.javaClass.name}",
        )
    }
}

internal fun discoverShareManagerImageMethods(
    shareManagerClass: Class<*>,
    chatRoomClass: Class<*>,
    messageTypeClass: Class<*>,
    listenerClass: Class<*>,
): Pair<java.lang.reflect.Method, java.lang.reflect.Method> {
    val intentMethod =
        selectMethodBySignature(
            label = "ShareManager image intent on ${shareManagerClass.name}",
            candidates =
                shareManagerClass.methods.filter { method ->
                    !Modifier.isStatic(method.modifiers) &&
                        method.parameterCount == 2 &&
                        method.parameterTypes[0] == List::class.java &&
                        method.parameterTypes[1] == messageTypeClass &&
                        method.returnType.name == "android.content.Intent"
                },
            preferredNames = setOf("I"),
        )
    val dispatchMethod =
        selectMethodBySignature(
            label = "ShareManager image dispatch on ${shareManagerClass.name}",
            candidates =
                shareManagerClass.methods.filter { method ->
                    !Modifier.isStatic(method.modifiers) &&
                        method.parameterCount == 4 &&
                        listenerClass.isAssignableFrom(method.parameterTypes[0]) &&
                        method.parameterTypes[1].name == "android.content.Intent" &&
                        method.parameterTypes[2].isAssignableFrom(chatRoomClass) &&
                        method.parameterTypes[3] == Boolean::class.javaPrimitiveType
                },
            preferredNames = setOf("h0"),
        )
    return intentMethod to dispatchMethod
}
