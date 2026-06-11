@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime.kakao.userdb

import java.lang.reflect.Method

internal data class KakaoUserDatabaseAccess(
    val singleton: Any,
    val getUserByIdV2Method: Method,
)
