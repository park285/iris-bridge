package party.qwer.iris.imagebridge.runtime.core

import org.json.JSONArray

internal data class BridgeCoreImagePathSnapshot(
    val canonicalPath: String,
    val sizeBytes: Long,
    val lastModifiedEpochMs: Long,
)

fun BridgeCore.imagePathUnderAllowedRoot(
    path: String,
    allowedRoots: Collection<String>,
): Boolean {
    if (!bridgeCoreLoadLibraryOnce()) return false
    val allowedRootsJson = JSONArray(allowedRoots).toString()
    return runCatching { BridgeCoreJniImagePath.nativeImagePathUnderAllowedRoot(path, allowedRootsJson) }
        .getOrElse { error ->
            bridgeCoreLogError("bridge-core image path root check threw", error)
            false
        }
}

internal fun BridgeCore.materializeImagePath(
    path: String,
    allowedRoots: Collection<String>,
): BridgeCoreImagePathSnapshot? {
    if (!bridgeCoreLoadLibraryOnce()) return null
    val allowedRootsJson = JSONArray(allowedRoots).toString()
    return runCatching {
        val envelope =
            BridgeCoreEnvelope.parse(
                BridgeCoreJniImagePath.nativeMaterializeImagePath(path, allowedRootsJson),
            )
        if (!envelope.isOk) {
            throw IllegalArgumentException(envelope.errorMessage ?: "image path validation failed")
        }
        envelope.imagePathSnapshot() ?: error("bridge-core image path materialization omitted fields")
    }.getOrElse { error ->
        if (error is IllegalArgumentException) throw error
        bridgeCoreLogError("bridge-core image path materialization threw", error)
        null
    }
}

internal fun BridgeCore.revalidateImagePathSnapshot(
    canonicalPath: String,
    allowedRoots: Collection<String>,
    sizeBytes: Long,
    lastModifiedEpochMs: Long,
): BridgeCoreImagePathSnapshot? {
    if (!bridgeCoreLoadLibraryOnce()) return null
    val allowedRootsJson = JSONArray(allowedRoots).toString()
    return runCatching {
        val envelope =
            BridgeCoreEnvelope.parse(
                BridgeCoreJniImagePath.nativeRevalidateImagePathSnapshot(
                    canonicalPath,
                    allowedRootsJson,
                    sizeBytes,
                    lastModifiedEpochMs,
                ),
            )
        if (!envelope.isOk) {
            throw IllegalArgumentException(envelope.errorMessage ?: "image path validation failed")
        }
        envelope.imagePathSnapshot() ?: error("bridge-core image path revalidation omitted fields")
    }.getOrElse { error ->
        if (error is IllegalArgumentException) throw error
        bridgeCoreLogError("bridge-core image path revalidation threw", error)
        null
    }
}

private fun BridgeCoreEnvelope.imagePathSnapshot(): BridgeCoreImagePathSnapshot? {
    val canonicalPath = string("canonicalPath") ?: return null
    val sizeBytes = long("sizeBytes") ?: return null
    val lastModifiedEpochMs = long("lastModifiedEpochMs") ?: return null
    return BridgeCoreImagePathSnapshot(
        canonicalPath = canonicalPath,
        sizeBytes = sizeBytes,
        lastModifiedEpochMs = lastModifiedEpochMs,
    )
}
