package party.qwer.iris.imagebridge.runtime.discovery

internal const val HOOK_ROOM_DAO = "MasterDatabase#roomDao"
internal const val HOOK_MANAGER_DIRECT = "ChatRoomManager#directResolve"
internal const val HOOK_MANAGER_BROAD = "ChatRoomManager#broadResolve"
internal const val HOOK_REPLY_MARKDOWN_INGRESS = "ReplyMarkdown#ingress"
internal const val HOOK_REPLY_MARKDOWN_REUSE = "ReplyMarkdown#reuseIntent"
internal const val HOOK_REPLY_MARKDOWN_REQUEST = "ReplyMarkdown#requestDispatch"
internal const val HOOK_REPLY_LEVERAGE_COMMIT = "ReplyLeverage#chatLogCommit"
internal const val HOOK_SEND_SINGLE = "ChatMediaSender#sendSingle"
internal const val HOOK_SEND_MULTIPLE = "ChatMediaSender#sendMultiple"
internal const val HOOK_SEND_THREADED_ENTRY = "ChatMediaSender#threadedEntry"
internal const val HOOK_SEND_THREADED_INJECT = "ChatMediaSender#threadedInject"

internal val defaultDiscoveryHookNames =
    listOf(
        HOOK_ROOM_DAO,
        HOOK_MANAGER_DIRECT,
        HOOK_MANAGER_BROAD,
        HOOK_REPLY_MARKDOWN_INGRESS,
        HOOK_REPLY_MARKDOWN_REUSE,
        HOOK_REPLY_MARKDOWN_REQUEST,
        HOOK_REPLY_LEVERAGE_COMMIT,
        HOOK_SEND_SINGLE,
        HOOK_SEND_MULTIPLE,
        HOOK_SEND_THREADED_ENTRY,
        HOOK_SEND_THREADED_INJECT,
    )
