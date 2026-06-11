@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime.kakao.memberfetch

import java.lang.reflect.Method

internal data class KakaoMemberFetchAccess(
    val clientSingleton: Any,
    val fetchMembersMethod: Method,
    val resultClass: Class<*>,
    val unwrapValueMethod: Method,
    val unwrapErrorMethod: Method,
)
