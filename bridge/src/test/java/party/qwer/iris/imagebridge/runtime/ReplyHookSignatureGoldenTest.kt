@file:Suppress("ClassName", "FunctionName")

package party.qwer.iris.imagebridge.runtime

import party.qwer.iris.imagebridge.runtime.core.BridgeCore
import party.qwer.iris.imagebridge.runtime.core.mentionsHashFromJson
import party.qwer.iris.imagebridge.runtime.core.replyHookSign
import kotlin.test.Test
import kotlin.test.assertEquals

// iris-bridge-core의 src/server/tests/{reply_hook,mentions_hash}.rs와 공유하는 cross-language
// 고정값을 native(BridgeCore JNI) 경유로 검증한다. protocol 모듈은 JNI를 못 실어 같은 고정값을
// Kotlin 단독으로 비교할 수 없으므로, bridge 모듈(host libiris_bridge_core.so 로드)에서 단일
// 소스(Rust)의 출력이 동결 골든과 일치함을 보증한다.
class ReplyHookSignatureGoldenTest {
    @Test
    fun `mentions hash matches the rust golden`() {
        assertEquals(
            "cbdee567897480fbbfc4dc21159c79d8b2488d5e4152e8525aa469f35f55f3fc",
            BridgeCore.mentionsHashFromJson(
                """{"mentions":[{"user_id":123,"at":[1],"len":3}]}""",
            ),
        )
    }

    @Test
    fun `escaped mentions hash matches the rust golden`() {
        assertEquals(
            "90ba3cbb314af930950908317c498d959a7f92e6855baafa501c8393663c1334",
            BridgeCore.mentionsHashFromJson(
                """{"mentions":[{"user_id":1,"nick":"a\"b\\c\nd"}]}""",
            ),
        )
    }

    @Test
    fun `prepared signature matches the rust golden`() {
        assertEquals(
            "79d5a2a35924c010f1984eb53ad492aea6421150afa648e015ca841f2f82431a",
            BridgeCore.replyHookSign(
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
        val mentionsHash =
            BridgeCore.mentionsHashFromJson(
                """{"mentions":[{"user_id":123,"at":[1],"len":3}]}""",
            )
        assertEquals(
            "01e8b9c4d783174da5f4ccc94bd31358e28ef0421daf1705bcdbcdae83b77a2a",
            BridgeCore.replyHookSign(
                bridgeToken = "bridge-token",
                roomId = 42,
                messageText = "hi @user",
                sessionId = "req-8",
                createdAtEpochMs = 1_700_000_000_000,
                mentionsHash = mentionsHash,
            ),
        )
    }
}
