package party.qwer.iris.imagebridge.runtime.server

import party.qwer.iris.imagebridge.runtime.kakao.KakaoClassRegistry

internal class BridgeHookSpecVerifier(
    private val registry: KakaoClassRegistry?,
    private val registryError: String? = null,
) {
    fun verify(): BridgeSpecStatus {
        val checks = mutableListOf<BridgeSpecCheck>()
        if (registry == null) {
            checks += BridgeSpecCheck("KakaoClassRegistry", ok = false, detail = registryError ?: "not initialized")
            return BridgeSpecStatus(ready = false, checkedAtEpochMs = System.currentTimeMillis(), checks = checks)
        }
        checks += checkField("class ChatMediaSender") { registry.chatMediaSenderClass.name }
        checks += checkField("class MessageType") { registry.messageTypeClass.name }
        checks += checkField("class ChatRoomManager") { registry.chatRoomManagerClass.name }
        checks += checkField("class ChatRoom") { registry.chatRoomClass.name }
        checks += checkField("class MasterDatabase") { registry.masterDatabaseClass.name }
        checks += checkField("class MediaItem") { registry.mediaItemClass.name }
        checks += checkField("class Function0") { registry.function0Class.name }
        checks += checkField("class Function1") { registry.function1Class.name }
        checks += checkField("class WriteType") { registry.writeTypeClass.name }
        checks += checkField("class Listener") { registry.listenerClass.name }
        checks += checkField("MasterDatabase singleton field") { registry.masterDbSingletonField.name }
        checks += checkField("MasterDatabase#roomDao") { registry.roomDaoMethod.name }
        checks += checkField("RoomDao#entityLookup") { registry.entityLookupMethod.name }
        checks += checkField("ChatMediaSender#sendSingle") { registry.singleSendMethod.name }
        checks += checkField("ChatMediaSender#sendMultiple") { registry.multiSendMethod.name }
        checks += checkField("MediaItem(String,long)") { registry.mediaItemConstructor.toString() }
        checks += checkField("ChatRoomManager#broadResolve") { registry.broadRoomResolverMethod.name }
        checks += checkField("ChatRoomManager#directResolve") { registry.directRoomResolverMethod.name }
        checks += checkField("message type Photo") { registry.photoType.toString() }
        checks += checkField("message type MultiPhoto") { registry.multiPhotoType.toString() }
        checks += checkField("write type None") { registry.writeTypeNone.toString() }
        return BridgeSpecStatus(ready = checks.all { it.ok }, checkedAtEpochMs = System.currentTimeMillis(), checks = checks)
    }

    private fun checkField(
        name: String,
        accessor: () -> String,
    ): BridgeSpecCheck =
        try {
            BridgeSpecCheck(name, ok = true, detail = accessor())
        } catch (error: Throwable) {
            BridgeSpecCheck(name, ok = false, detail = error.message ?: error.javaClass.name)
        }
}
