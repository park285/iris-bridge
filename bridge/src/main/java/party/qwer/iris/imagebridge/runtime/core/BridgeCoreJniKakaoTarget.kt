package party.qwer.iris.imagebridge.runtime.core

internal object BridgeCoreJniKakaoTarget {
    external fun nativeResolveKakaoTarget(packageName: String): String
}
