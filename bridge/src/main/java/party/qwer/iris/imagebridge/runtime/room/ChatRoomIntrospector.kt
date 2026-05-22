package party.qwer.iris.imagebridge.runtime.room

internal class ChatRoomIntrospector(
    private val scanner: ChatRoomFieldScanner = ChatRoomFieldScanner(),
) {

    data class ScanResult(
        val className: String,
        val scannedAt: Long,
        val fields: List<FieldInfo>,
    )

    data class FieldInfo(
        val name: String,
        val type: String,
        val value: String? = null,
        val size: Int? = null,
        val elementType: String? = null,
        val nested: List<FieldInfo> = emptyList(),
        val elements: List<FieldInfo> = emptyList(),
    )

    fun scan(
        obj: Any,
        maxDepth: Int = 1,
    ): ScanResult =
        ScanResult(
            className = obj.javaClass.canonicalName ?: obj.javaClass.name,
            scannedAt = System.currentTimeMillis() / 1000,
            fields = scanner.scanFields(obj, maxDepth, currentDepth = 0),
        )

    fun scanJson(
        obj: Any,
        maxDepth: Int = 1,
    ): String = scan(obj, maxDepth).toJson().toString()
}

internal val defaultChatRoomIntrospector = ChatRoomIntrospector()
