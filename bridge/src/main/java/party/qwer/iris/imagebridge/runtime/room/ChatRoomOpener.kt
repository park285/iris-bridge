package party.qwer.iris.imagebridge.runtime.room

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import party.qwer.iris.imagebridge.runtime.kakao.KakaoTalkTarget
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

internal class ChatRoomOpener(
    context: Context,
    private val kakaoPackage: String = KakaoTalkTarget.OFFICIAL_PACKAGE,
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
    startActivity: ((Intent) -> Unit)? = null,
) {
    private val appContext = context.applicationContext
    private val startActivity = startActivity ?: appContext::startActivity

    fun open(roomId: Long) {
        require(roomId > 0L) { "roomId must be positive" }
        val task =
            Runnable {
                startActivity(buildChatRoomIntent(roomId))
            }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            task.run()
            return
        }

        val failure = AtomicReference<Throwable?>()
        val latch = CountDownLatch(1)
        val posted =
            mainHandler.post {
                try {
                    task.run()
                } catch (error: Throwable) {
                    failure.set(error)
                } finally {
                    latch.countDown()
                }
            }
        check(posted) { "chatroom open dispatch rejected" }
        check(latch.await(DISPATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "chatroom open dispatch timed out" }
        failure.get()?.let { throw it }
    }

    private fun buildChatRoomIntent(roomId: Long): Intent =
        Intent(ENTER_CHAT_ROOM_ACTION)
            .setClassName(kakaoPackage, ENTER_CHAT_ROOM_ACTIVITY)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .apply {
                putExtra(CHAT_ROOM_ID_EXTRA_KEY, roomId)
            }

    private companion object {
        private const val ENTER_CHAT_ROOM_ACTIVITY = "com.kakao.talk.activity.RecentExcludeIntentFilterActivity"
        private const val ENTER_CHAT_ROOM_ACTION = "com.kakao.talk.intent.action.ENTER_CHAT_ROOM"
        private const val DISPATCH_TIMEOUT_SECONDS = 5L
        private const val CHAT_ROOM_ID_EXTRA_KEY = "chatRoomId"
    }
}
