@file:Suppress("ClassName", "FunctionName")

package party.qwer.iris.imagebridge.runtime.kakao.userdb

import kotlin.coroutines.Continuation

internal class FakeUserModel(
    val userId: Long,
    private val nickName: String,
) {
    fun getNickName(): String = nickName
}

private annotation class FakeDebugMetadata(
    val c: String,
    val f: String,
    val l: IntArray,
    val m: String,
)

internal class FakeUserDataSource(
    private val lookup: (Long) -> FakeUserModel?,
) {
    @Suppress("UNUSED_PARAMETER")
    suspend fun getUserByIdV2(userId: Long): FakeUserModel? = lookup(userId)
}

internal class FakeMangledUserDataSource(
    private val lookup: (Long) -> FakeUserModel?,
) {
    @Suppress("UNUSED_PARAMETER")
    suspend fun `getUserByIdV2-gIAlu-s`(userId: Long): FakeUserModel? = lookup(userId)
}

internal class FakeObfuscatedUserDataSource(
    private val lookup: (Long) -> FakeUserModel?,
) {
    @Suppress("UNUSED_PARAMETER")
    suspend fun t(userId: Long): FakeUserModel? = lookup(userId)

    @FakeDebugMetadata(
        c = "com.kakao.talk.singleton.UserDatabaseDataSource",
        f = "UserDatabaseDataSource.kt",
        l = [41],
        m = "getUserByIdV2-gIAlu-s",
    )
    private class UserByIdContinuation
}

internal class FakeObfuscatedWithoutMetadataUserDataSource(
    private val lookup: (Long) -> FakeUserModel?,
) {
    @Suppress("UNUSED_PARAMETER")
    suspend fun t(userId: Long): FakeUserModel? = lookup(userId)
}

internal class FakeObfuscatedWrongMetadataUserDataSource(
    private val lookup: (Long) -> FakeUserModel?,
) {
    @Suppress("UNUSED_PARAMETER")
    suspend fun t(userId: Long): FakeUserModel? = lookup(userId)

    @FakeDebugMetadata(
        c = "com.kakao.talk.singleton.UserDatabaseDataSource",
        f = "UserDatabaseDataSource.kt",
        l = [41],
        m = "getUserByIdsV2",
    )
    private class UserByIdsContinuation
}

internal class FakeWrongObfuscatedNameUserDataSource(
    private val lookup: (Long) -> FakeUserModel?,
) {
    @Suppress("UNUSED_PARAMETER")
    suspend fun u(userId: Long): FakeUserModel? = lookup(userId)

    @FakeDebugMetadata(
        c = "com.kakao.talk.singleton.UserDatabaseDataSource",
        f = "UserDatabaseDataSource.kt",
        l = [41],
        m = "getUserByIdV2-gIAlu-s",
    )
    private class UserByIdContinuation
}

internal class FakeExactAndObfuscatedUserDataSource(
    private val lookup: (Long) -> FakeUserModel?,
) {
    @Suppress("UNUSED_PARAMETER")
    suspend fun getUserByIdV2(userId: Long): FakeUserModel? = lookup(userId)

    @Suppress("UNUSED_PARAMETER")
    suspend fun t(userId: Long): FakeUserModel? = lookup(userId)

    @FakeDebugMetadata(
        c = "com.kakao.talk.singleton.UserDatabaseDataSource",
        f = "UserDatabaseDataSource.kt",
        l = [41],
        m = "getUserByIdV2-gIAlu-s",
    )
    private class UserByIdContinuation
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
