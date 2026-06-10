package party.qwer.iris.imagebridge.runtime.core

fun BridgeCore.imageLeaseRejectionIsStateError(message: String): Boolean = imageLeaseRejectionIsStateError(message, ::nativeImageLeaseRejectionIsStateError)

internal fun BridgeCore.imageLeaseRejectionIsStateError(
    message: String,
    rejectionKindPolicy: (String) -> Boolean?,
): Boolean = rejectionKindPolicy(message) == true

private fun nativeImageLeaseRejectionIsStateError(message: String): Boolean? {
    if (!bridgeCoreLoadLibraryOnce()) return null
    return runCatching { BridgeCoreJniLease.nativeImageLeaseRejectionIsStateError(message) }
        .getOrElse { error ->
            bridgeCoreLogError("bridge-core image lease rejection policy threw", error)
            null
        }
}
