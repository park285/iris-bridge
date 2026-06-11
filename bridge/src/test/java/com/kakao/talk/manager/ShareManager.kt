@file:Suppress("FunctionName")

package com.kakao.talk.manager

import android.content.Context
import android.content.Intent

class ShareManager {
    companion object {
        @JvmField
        val INSTANCE = ShareManager()

        var context: Context? = null
        var chatRoom: Any? = null
        var message: String? = null
        var flag: Boolean? = null
        var listener: Any? = null
        val imageIntentPaths = mutableListOf<List<*>>()
        val imageIntentTypes = mutableListOf<Any>()
        var imageDispatchListener: Any? = null
        var imageDispatchIntent: Intent? = null
        var imageDispatchChatRoom: Any? = null
        var imageDispatchFlag: Boolean? = null

        fun reset() {
            context = null
            chatRoom = null
            message = null
            flag = null
            listener = null
            imageIntentPaths.clear()
            imageIntentTypes.clear()
            imageDispatchListener = null
            imageDispatchIntent = null
            imageDispatchChatRoom = null
            imageDispatchFlag = null
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

    fun I(
        paths: List<*>,
        messageType: Any,
    ): Intent {
        imageIntentPaths += paths
        imageIntentTypes += messageType
        return Intent("iris.test.image")
    }

    fun h0(
        listener: com.kakao.talk.manager.send.m?,
        intent: Intent,
        chatRoom: Any,
        flag: Boolean,
    ) {
        imageDispatchListener = listener
        imageDispatchIntent = intent
        imageDispatchChatRoom = chatRoom
        imageDispatchFlag = flag
    }
}
