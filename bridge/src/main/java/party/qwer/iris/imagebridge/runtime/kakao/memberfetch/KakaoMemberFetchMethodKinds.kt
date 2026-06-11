@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime.kakao.memberfetch

import java.lang.reflect.Method

internal fun Method.isRoomSuspendFetchMembersMethod(): Boolean =
    parameterCount == 2 &&
        parameterTypes[1].isKotlinContinuationType()

internal fun Method.isRequestedSuspendFetchMembersMethod(): Boolean =
    parameterCount == 3 &&
        List::class.java.isAssignableFrom(parameterTypes[1]) &&
        parameterTypes[2].isKotlinContinuationType()

internal fun Class<*>.isKotlinContinuationType(): Boolean = name == kotlinClassName("coroutines", "Continuation")

internal fun kotlinClassName(vararg segments: String): String = ("kotlin." + segments.joinToString("."))
