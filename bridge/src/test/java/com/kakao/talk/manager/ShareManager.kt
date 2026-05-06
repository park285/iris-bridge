package com.kakao.talk.manager

import android.content.Context

class ShareManager {
    companion object {
        @JvmField
        val INSTANCE = ShareManager()

        var context: Context? = null
        var chatRoom: Any? = null
        var message: String? = null
        var flag: Boolean? = null
        var listener: Any? = null

        fun reset() {
            context = null
            chatRoom = null
            message = null
            flag = null
            listener = null
        }
    }

    fun z9(
        context: Context,
        chatRoom: Any,
        message: String,
        flag: Boolean,
        listener: Any?,
    ) {
        Companion.context = context
        Companion.chatRoom = chatRoom
        Companion.message = message
        Companion.flag = flag
        Companion.listener = listener
    }
}
