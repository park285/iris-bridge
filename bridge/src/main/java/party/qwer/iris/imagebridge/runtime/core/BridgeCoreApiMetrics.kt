package party.qwer.iris.imagebridge.runtime.core

private const val FAILURE_METRIC_BUCKET_SEND_FAILURE = "sendFailure"

fun BridgeCore.failureMetricBucket(errorCode: String): String {
    if (!bridgeCoreLoadLibraryOnce()) return FAILURE_METRIC_BUCKET_SEND_FAILURE
    return runCatching { BridgeCoreJniRequest.nativeFailureMetricBucket(errorCode) }
        .getOrElse { error ->
            bridgeCoreLogError("bridge-core failure metric bucket policy threw", error)
            FAILURE_METRIC_BUCKET_SEND_FAILURE
        }
}
