@file:Suppress("ClassName", "FunctionName", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime

import android.content.Intent

internal class RenamedShareManager {
    companion object {
        @JvmField
        val INSTANCE = RenamedShareManager()
    }

    fun buildImageIntent(
        paths: List<*>,
        messageType: FakeMessageType,
    ): Intent =
        Intent("iris.test.renamed.image").apply {
            putExtra("paths", paths.size)
            putExtra("type", messageType.toString())
        }

    @Suppress("UNUSED_PARAMETER")
    fun dispatchImage(
        listener: FakeListener?,
        intent: Intent,
        chatRoom: FakeChatRoom,
        flag: Boolean,
    ) {
        check(intent.action == "iris.test.renamed.image")
        check(!flag)
    }
}
