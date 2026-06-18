package party.qwer.iris.imagebridge.runtime.core

import org.json.JSONObject

internal object BridgeCoreJniKakaoTarget {
    fun nativeResolveKakaoTarget(packageName: String): String =
        BridgeCoreJniDispatcher.envelope(
            "kakaoTarget.resolve",
            JSONObject().put("packageName", packageName),
        )
}
