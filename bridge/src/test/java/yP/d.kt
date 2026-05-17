@file:Suppress("ClassName")

package yP

class d private constructor() {
    var refreshCalls: Int = 0
    var lastCaller: String = ""

    fun u(caller: String) {
        refreshCalls += 1
        lastCaller = caller
    }

    fun e(): String = "access-token"

    companion object {
        @JvmField
        val a: d = d()
    }
}
