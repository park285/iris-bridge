@file:Suppress("ClassName")

package IZ

class V private constructor() {
    fun u(): String = "device-id"

    companion object {
        @JvmField
        val a: V = V()
    }
}
