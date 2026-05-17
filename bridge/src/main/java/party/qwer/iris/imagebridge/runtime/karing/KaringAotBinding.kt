package party.qwer.iris.imagebridge.runtime.karing

import java.lang.reflect.Method

internal data class KaringAotBinding(
    val oauthHelper: Any,
    val hardware: Any,
    val refresh: () -> Unit,
    val accessToken: () -> String,
    val deviceId: () -> String,
    val kaTgt: () -> String,
)

internal data class KaringAotClasses(
    val ssoHelper: Class<*>,
    val ssoType: Class<*>,
    val function1: Class<*>,
    val oauth: Class<*>,
    val hardware: Class<*>,
)

internal data class KaringAotSingletons(
    val ssoHelper: Any,
    val ssoType: Any,
    val oauthHelper: Any,
    val hardware: Any,
)

internal data class KaringAotMethods(
    val accessToken: Method,
    val refresh: Method,
    val deviceId: Method,
    val getTgtIfNeed: Method,
)
