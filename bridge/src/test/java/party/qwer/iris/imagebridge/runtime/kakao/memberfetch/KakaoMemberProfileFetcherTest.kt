@file:Suppress("ClassName", "FunctionName", "UNUSED_PARAMETER")

package party.qwer.iris.imagebridge.runtime.kakao.memberfetch

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.Executors
import kotlin.coroutines.Continuation
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class KakaoMemberProfileFetcherTest {
    @Test
    fun `fetchMemberProfiles invokes latest MEMBER API and filters requested profiles`() {
        val fetcher = KakaoMemberProfileFetcher(buildFakeLatestMemberFetchAccess())

        val executor = Executors.newSingleThreadExecutor()
        val result =
            try {
                executor
                    .submit<Map<Long, UpstreamMemberProfile>> {
                        fetcher.fetchMemberProfiles(chatId = 55L, userIds = listOf(56L, 57L, 56L))
                    }.get()
            } finally {
                executor.shutdownNow()
            }

        assertEquals(setOf(56L, 57L), result.keys)
        assertEquals("Member 56", result[56L]?.nickName)
        assertEquals("https://example.test/member-57.png", result[57L]?.profileImageUrl)
    }
}

internal fun buildFakeLatestMemberFetchAccess(): KakaoMemberFetchAccess {
    val method =
        FakeLatestMemberFetchClient::class.java.methods.first { method ->
            method.name == "Y" &&
                method.parameterCount == 3 &&
                Continuation::class.java.isAssignableFrom(method.parameterTypes[2])
        }
    val roomMethod =
        FakeLatestMemberFetchClient::class.java.methods.first { method ->
            method.name == "D" &&
                method.parameterCount == 2 &&
                Continuation::class.java.isAssignableFrom(method.parameterTypes[1])
        }
    val unwrapValueMethod =
        FakeLocoResult::class.java.methods.first { method ->
            method.name == "e" && method.parameterCount == 1
        }
    val unwrapErrorMethod =
        FakeLocoResult::class.java.methods.first { method ->
            method.name == "d" && method.parameterCount == 1
        }
    return KakaoMemberFetchAccess(
        clientSingleton = FakeLatestMemberFetchClient.b,
        fetchMembersMethod = method,
        roomFetchMembersMethod = roomMethod,
        resultClass = FakeLocoResult::class.java,
        unwrapValueMethod = unwrapValueMethod,
        unwrapErrorMethod = unwrapErrorMethod,
    )
}

internal class FakeLocoResult(
    private val value: Any?,
    private val error: Any? = null,
) {
    fun j(): Any? = error ?: value

    companion object {
        @JvmStatic
        fun e(result: Any): Any? = result.takeUnless { it is FakeLocoFailure }

        @JvmStatic
        fun d(result: Any): Any? = (result as? FakeLocoFailure)?.error
    }
}

internal data class FakeLocoFailure(
    val error: Any,
)

internal data class FakeMemberResponse(
    private val members: List<FakeMember>,
) {
    fun a(): List<FakeMember> = members
}

internal class FakeMember(
    private val userId: Long,
    private val nickName: String,
    private val profileUrl: String = "https://example.test/member-$userId.png",
) {
    fun e(): Int = 7

    fun n(): Long = userId

    fun f(): String = nickName

    fun j(): String = profileUrl
}

internal class FakeOptionalProperty(
    private val value: String,
) {
    fun a(): Any = value
}
