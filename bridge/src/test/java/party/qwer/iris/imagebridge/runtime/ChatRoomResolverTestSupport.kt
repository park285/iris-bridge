@file:Suppress("ClassName", "FunctionName", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime

internal object FakeChatRuntime {
    val resolvedRoomIds = mutableListOf<Long>()
    val databaseRoomIds = mutableListOf<Long>()
    val managerRoomIds = mutableListOf<Long>()

    fun reset() {
        resolvedRoomIds.clear()
        databaseRoomIds.clear()
        managerRoomIds.clear()
        FakeMasterDatabase.INSTANCE = FakeMasterDatabase()
    }
}

internal class FakeMasterDatabase {
    companion object {
        @JvmField
        var INSTANCE: FakeMasterDatabase? = FakeMasterDatabase()
    }

    @Suppress("FunctionName")
    fun O(): FakeRoomDao = FakeRoomDao()
}

internal class FakeRoomDao {
    fun h(roomId: Long): FakeRoomEntity {
        FakeChatRuntime.databaseRoomIds += roomId
        return FakeRoomEntity(roomId)
    }
}

internal data class FakeRoomEntity(
    val roomId: Long,
)

internal class FakeChatRoomModel private constructor(
    val roomId: Long,
) {
    companion object {
        @JvmField
        val CompanionResolver = Resolver()
    }

    class Resolver {
        fun c(entity: FakeRoomEntity): FakeChatRoomModel {
            FakeChatRuntime.resolvedRoomIds += entity.roomId
            return FakeChatRoomModel(entity.roomId)
        }
    }
}

internal class LegacyNameSensitiveChatRoom private constructor(
    val roomId: Long,
) {
    companion object {
        @JvmField
        val CompanionResolver = Resolver()
    }

    class Resolver {
        fun c(entity: FakeRoomEntity): LegacyNameSensitiveChatRoom {
            LegacyNameSensitiveRecorder.calls += "c"
            return LegacyNameSensitiveChatRoom(entity.roomId)
        }

        fun z(entity: FakeRoomEntity): LegacyNameSensitiveChatRoom {
            LegacyNameSensitiveRecorder.calls += "z"
            return LegacyNameSensitiveChatRoom(entity.roomId)
        }
    }
}

internal object LegacyNameSensitiveRecorder {
    val calls = mutableListOf<String>()
}

internal class ModernEntityNameChatRoom private constructor(
    val roomId: Long,
) {
    companion object {
        @JvmField
        val CompanionResolver = Resolver()
    }

    class Resolver {
        fun b(entity: FakeRoomEntity): ModernEntityNameChatRoom {
            LegacyNameSensitiveRecorder.calls += "b"
            return ModernEntityNameChatRoom(entity.roomId)
        }
    }
}

internal class FakeChatRoomManager {
    companion object {
        @JvmStatic
        fun j(): FakeChatRoomManager = FakeChatRoomManager()
    }

    @Suppress("UNUSED_PARAMETER")
    fun e0(
        roomId: Long,
        includeMembers: Boolean,
        includeOpenLink: Boolean,
    ): FakeChatRoomModel? = null

    fun d0(roomId: Long): FakeChatRoomModel {
        FakeChatRuntime.managerRoomIds += roomId
        return FakeChatRoomModel.CompanionResolver.c(FakeRoomEntity(roomId))
    }
}
