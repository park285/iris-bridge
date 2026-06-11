@file:Suppress("ClassName", "FunctionName")

package party.qwer.iris.imagebridge.runtime.kakao.userdb

import kotlin.coroutines.Continuation

internal class FakeUserModel(
    val userId: Long,
    private val nickName: String,
) {
    fun getNickName(): String = nickName
}

internal class FakeUserDataSource(
    private val lookup: (Long) -> FakeUserModel?,
) {
    @Suppress("UNUSED_PARAMETER")
    suspend fun getUserByIdV2(userId: Long): FakeUserModel? = lookup(userId)
}

internal fun buildFakeUserDbAccess(lookup: (Long) -> FakeUserModel?): KakaoUserDatabaseAccess {
    val instance = FakeUserDataSource(lookup)
    val method =
        FakeUserDataSource::class.java.methods.first { method ->
            method.name == "getUserByIdV2" &&
                method.parameterCount == 2 &&
                Continuation::class.java.isAssignableFrom(method.parameterTypes[1])
        }
    return KakaoUserDatabaseAccess(
        singleton = instance,
        getUserByIdV2Method = method,
    )
}
