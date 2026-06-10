package party.qwer.iris.imagebridge.runtime.core

import android.util.Log

private const val TAG = "IrisBridge"
private const val LIBRARY_NAME = "iris_bridge_core"

internal fun bridgeCoreLogInfo(message: String) {
    runCatching { Log.i(TAG, message) }
}

internal fun bridgeCoreLogWarn(
    message: String,
    error: Throwable,
) {
    runCatching { Log.w(TAG, message, error) }
}

internal fun bridgeCoreLogError(message: String) {
    runCatching { Log.e(TAG, message) }
}

internal fun bridgeCoreLogError(
    message: String,
    error: Throwable,
) {
    runCatching { Log.e(TAG, message, error) }
}

internal fun bridgeCoreLoadLibraryOnce(): Boolean =
    runCatching { System.loadLibrary(LIBRARY_NAME) }
        .onFailure { bridgeCoreLogError("bridge-core load failed", it) }
        .isSuccess
