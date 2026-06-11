@file:Suppress("FunctionName")

package party.qwer.iris.imagebridge.runtime.kakao.userdb

import party.qwer.iris.imagebridge.runtime.kakao.memberfetch.MemberProfileUpstream
import party.qwer.iris.imagebridge.runtime.kakao.memberfetch.UpstreamMemberProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class KakaoCachedMemberProfileFetcherTest {
    @Test
    fun `fetchMemberProfiles warms GETMEM then returns userdb nicknames`() {
        val warmCalls = mutableListOf<Pair<Long, List<Long>>>()
        val baseFetcher =
            MemberProfileUpstream { chatId, userIds ->
                warmCalls += chatId to userIds.toList()
                userIds.associateWith { userId -> UpstreamMemberProfile(userId, "Member $userId", null) }
            }
        val access =
            buildFakeUserDbAccess { userId ->
                if (userId == 101L) FakeUserModel(101L, "Cached Member 101") else null
            }
        val dbReader = KakaoUserDatabaseReader(access)
        val fetcher = KakaoCachedMemberProfileFetcher(baseFetcher, dbReader)

        val result = fetcher.fetchMemberProfiles(chatId = 10L, userIds = listOf(101L, 999L))

        assertEquals(1, warmCalls.size)
        assertEquals(10L, warmCalls[0].first)
        assertEquals("Cached Member 101", result[101L]?.nickName)
        assertEquals("Member 999", result[999L]?.nickName)
    }

    @Test
    fun `fetchMemberProfiles falls back to upstream member API when userdb misses`() {
        val baseFetcher =
            MemberProfileUpstream { _, userIds ->
                userIds.associateWith { userId -> UpstreamMemberProfile(userId, "Member $userId", null) }
            }
        val access = buildFakeUserDbAccess { null }
        val dbReader = KakaoUserDatabaseReader(access)
        val fetcher = KakaoCachedMemberProfileFetcher(baseFetcher, dbReader)

        val result = fetcher.fetchMemberProfiles(chatId = 1L, userIds = listOf(42L))

        assertEquals("Member 42", result[42L]?.nickName)
    }

    @Test
    fun `fetchMemberProfiles only reads userdb for upstream confirmed members`() {
        val dbReadIds = mutableListOf<Long>()
        val baseFetcher =
            MemberProfileUpstream { _, _ ->
                mapOf(101L to UpstreamMemberProfile(101L, "Member 101", null))
            }
        val access =
            buildFakeUserDbAccess { userId ->
                dbReadIds += userId
                when (userId) {
                    101L -> FakeUserModel(101L, "Cached Member 101")
                    202L -> FakeUserModel(202L, "Cached Member 202")
                    else -> null
                }
            }
        val dbReader = KakaoUserDatabaseReader(access)
        val fetcher = KakaoCachedMemberProfileFetcher(baseFetcher, dbReader)

        val result = fetcher.fetchMemberProfiles(chatId = 1L, userIds = listOf(101L, 202L))

        assertEquals(listOf(101L), dbReadIds)
        assertEquals("Cached Member 101", result[101L]?.nickName)
        assertEquals(null, result[202L])
    }

    @Test
    fun `fetchMemberProfiles returns empty for empty userIds`() {
        val baseFetcher = MemberProfileUpstream { _, _ -> emptyMap() }
        val access = buildFakeUserDbAccess { null }
        val dbReader = KakaoUserDatabaseReader(access)
        val fetcher = KakaoCachedMemberProfileFetcher(baseFetcher, dbReader)

        assertTrue(fetcher.fetchMemberProfiles(chatId = 1L, userIds = emptyList()).isEmpty())
    }

    @Test
    fun `fetchMemberProfiles requires positive chatId`() {
        val baseFetcher = MemberProfileUpstream { _, _ -> emptyMap() }
        val access = buildFakeUserDbAccess { null }
        val fetcher = KakaoCachedMemberProfileFetcher(baseFetcher, KakaoUserDatabaseReader(access))

        assertFailsWith<IllegalArgumentException> {
            fetcher.fetchMemberProfiles(chatId = 0L, userIds = listOf(1L))
        }
    }

    @Test
    fun `fetchMemberProfiles skips invalid user ids`() {
        val baseFetcher =
            MemberProfileUpstream { _, userIds ->
                userIds.filter { it == 5L }.associateWith { userId -> UpstreamMemberProfile(userId, "Member $userId", null) }
            }
        val access =
            buildFakeUserDbAccess { userId ->
                if (userId == 5L) FakeUserModel(5L, "Valid") else null
            }
        val dbReader = KakaoUserDatabaseReader(access)
        val fetcher = KakaoCachedMemberProfileFetcher(baseFetcher, dbReader)

        val result = fetcher.fetchMemberProfiles(chatId = 1L, userIds = listOf(-1L, 0L, 5L))

        assertEquals("Valid", result[5L]?.nickName)
        assertTrue(-1L !in result)
        assertTrue(0L !in result)
    }
}
