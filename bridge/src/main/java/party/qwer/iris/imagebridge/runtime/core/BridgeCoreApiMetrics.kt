package party.qwer.iris.imagebridge.runtime.core

fun BridgeCore.failureMetricBucket(errorCode: String): String = failureMetricBucket(errorCode, ::nativeFailureMetricBucket)

internal fun BridgeCore.failureMetricBucket(
    errorCode: String,
    bucketPolicy: (String) -> String?,
): String =
    bucketPolicy(errorCode)
        ?: error("bridge core unavailable to resolve failure metric bucket")

private fun nativeFailureMetricBucket(errorCode: String): String? {
    if (!bridgeCoreLoadLibraryOnce()) return null
    return runCatching { BridgeCoreJniRequest.nativeFailureMetricBucket(errorCode) }
        .getOrElse { error ->
            bridgeCoreLogError("bridge-core failure metric bucket policy threw", error)
            null
        }
}
