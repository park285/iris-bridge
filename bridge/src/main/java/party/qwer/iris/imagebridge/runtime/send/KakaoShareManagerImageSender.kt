package party.qwer.iris.imagebridge.runtime.send

import android.content.Context
import android.content.Intent
import android.util.Log
import org.json.JSONObject
import party.qwer.iris.imagebridge.runtime.kakao.KakaoClassRegistry
import party.qwer.iris.imagebridge.runtime.kakao.classregistry.resolveShareManagerSingleton
import party.qwer.iris.imagebridge.runtime.kakao.classregistry.selectMethodBySignature
import java.lang.reflect.Modifier
import java.util.ArrayList

internal class KakaoShareManagerImageSender(
    private val registry: KakaoClassRegistry,
    private val context: Context?,
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
            invokeShareManagerImageIntent(
                target = target,
                intentMethod = intentMethod,
                context = context,
                messageType = messageType,
                mediaPaths = mediaPaths,
                forwardExtraList = ArrayList(),
            )
                ?: error("ShareManager image intent builder returned null")
        markShareManagerStreamArray(intent)
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

private fun invokeShareManagerImageIntent(
    target: Any,
    intentMethod: java.lang.reflect.Method,
    context: Context?,
    messageType: Any,
    mediaPaths: ArrayList<Any>,
    forwardExtraList: ArrayList<JSONObject>,
): Any? =
    when (intentMethod.parameterCount) {
        4 ->
            intentMethod.apply { isAccessible = true }.invoke(
                target,
                requireNotNull(context) { "Android context required for ShareManager image intent method" },
                messageType,
                mediaPaths,
                forwardExtraList,
            )

        2 -> intentMethod.apply { isAccessible = true }.invoke(target, mediaPaths, messageType)
        else -> error("unsupported ShareManager image intent signature: ${intentMethod.toGenericString()}")
    }

private fun markShareManagerStreamArray(intent: Any) {
    if (intent is Intent && intent.hasExtra(Intent.EXTRA_STREAM)) {
        intent.putExtra("EXTRA_STREAM_IS_ARRAY", true)
    }
}

internal fun discoverShareManagerImageMethods(
    shareManagerClass: Class<*>,
    chatRoomClass: Class<*>,
    messageTypeClass: Class<*>,
    listenerClass: Class<*>,
): Pair<java.lang.reflect.Method, java.lang.reflect.Method> {
    val directIntentCandidates =
        shareManagerClass.methods.filter { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.parameterCount == 4 &&
                method.parameterTypes[0].name == "android.content.Context" &&
                method.parameterTypes[1].isAssignableFrom(messageTypeClass) &&
                ArrayList::class.java.isAssignableFrom(method.parameterTypes[2]) &&
                ArrayList::class.java.isAssignableFrom(method.parameterTypes[3]) &&
                method.returnType.name == "android.content.Intent"
        }
    val intentMethod =
        if (directIntentCandidates.isNotEmpty()) {
            selectMethodBySignature(
                label = "ShareManager direct image intent on ${shareManagerClass.name}",
                candidates = directIntentCandidates,
                preferredNames = setOf("H"),
            )
        } else {
            selectMethodBySignature(
                label = "ShareManager forwarded image intent on ${shareManagerClass.name}",
                candidates =
                    shareManagerClass.methods.filter { method ->
                        !Modifier.isStatic(method.modifiers) &&
                            method.parameterCount == 2 &&
                            method.parameterTypes[0] == List::class.java &&
                            method.parameterTypes[1].isAssignableFrom(messageTypeClass) &&
                            method.returnType.name == "android.content.Intent"
                    },
                preferredNames = setOf("I"),
            )
        }
    val dispatchMethod =
        selectMethodBySignature(
            label = "ShareManager image dispatch on ${shareManagerClass.name}",
            candidates =
                shareManagerClass.methods.filter { method ->
                    !Modifier.isStatic(method.modifiers) &&
                        method.parameterCount == 4 &&
                        method.parameterTypes[0].isAssignableFrom(listenerClass) &&
                        method.parameterTypes[1].name == "android.content.Intent" &&
                        method.parameterTypes[2].isAssignableFrom(chatRoomClass) &&
                        method.parameterTypes[3] == Boolean::class.javaPrimitiveType
                },
            preferredNames = setOf("h0"),
        )
    return intentMethod to dispatchMethod
}
