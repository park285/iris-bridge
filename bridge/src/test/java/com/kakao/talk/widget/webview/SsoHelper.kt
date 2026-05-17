package com.kakao.talk.widget.webview

object SsoHelper {
    fun getTgtIfNeed(
        type: SsoType,
        url: String,
        callback: Function1<Any?, Unit>,
    ) {
        callback(mapOf("KA-TGT" to "${type.name}:$url"))
    }
}
