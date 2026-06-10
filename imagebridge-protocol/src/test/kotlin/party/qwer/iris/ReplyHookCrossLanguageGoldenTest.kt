package party.qwer.iris

import kotlin.test.Test
import kotlin.test.assertEquals

// iris-bridge-core의 src/server/tests/{reply_hook,mentions_hash}.rs와 공유하는 cross-language 고정값.
class ReplyHookCrossLanguageGoldenTest {
    @Test
    fun `mentions hash matches the rust golden`() {
        assertEquals(
            "cbdee567897480fbbfc4dc21159c79d8b2488d5e4152e8525aa469f35f55f3fc",
            ReplyHookSignatureProtocol.mentionsHashFromMentionsJson(
                """{"mentions":[{"user_id":123,"at":[1],"len":3}]}""",
            ),
        )
    }

    @Test
    fun `escaped mentions hash matches the rust golden`() {
        assertEquals(
            "90ba3cbb314af930950908317c498d959a7f92e6855baafa501c8393663c1334",
            ReplyHookSignatureProtocol.mentionsHashFromMentionsJson(
                """{"mentions":[{"user_id":1,"nick":"a\"b\\c\nd"}]}""",
            ),
        )
    }

    @Test
    fun `prepared signature matches the rust golden`() {
        assertEquals(
            "79d5a2a35924c010f1984eb53ad492aea6421150afa648e015ca841f2f82431a",
            ReplyHookSignatureProtocol.signPreparedOrNull(
                bridgeToken = "bridge-token",
                roomId = 42,
                messageText = "hello **world**",
                sessionId = "req-7",
                createdAtEpochMs = 1_700_000_000_000,
                mentionsHash = null,
            ),
        )
    }

    @Test
    fun `signature with mentions hash matches the rust golden`() {
        assertEquals(
            "01e8b9c4d783174da5f4ccc94bd31358e28ef0421daf1705bcdbcdae83b77a2a",
            ReplyHookSignatureProtocol.signOrNull(
                bridgeToken = "bridge-token",
                roomId = 42,
                messageText = "hi @user",
                sessionId = "req-8",
                createdAtEpochMs = 1_700_000_000_000,
                mentionsJson = """{"mentions":[{"user_id":123,"at":[1],"len":3}]}""",
            ),
        )
    }
}
