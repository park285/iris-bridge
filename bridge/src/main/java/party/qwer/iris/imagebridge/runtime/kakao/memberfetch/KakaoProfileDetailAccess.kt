@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime.kakao.memberfetch

import java.lang.reflect.Method

internal data class KakaoProfileDetailAccess(
    val profileApi: Any,
    val otherProfileMethod: Method,
    val refreshOtherProfileMethod: Method? = null,
)
