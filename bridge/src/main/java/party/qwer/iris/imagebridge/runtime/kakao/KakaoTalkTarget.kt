package party.qwer.iris.imagebridge.runtime.kakao

import party.qwer.iris.imagebridge.runtime.core.BridgeCoreEnvelope
import party.qwer.iris.imagebridge.runtime.core.BridgeCoreJniKakaoTarget
import party.qwer.iris.imagebridge.runtime.core.bridgeCoreLoadLibraryOnce
import party.qwer.iris.imagebridge.runtime.core.bridgeCoreLogError

internal object KakaoTalkTarget {
    const val OFFICIAL_PACKAGE = "com.kakao.talk"
    const val REVANCED_PACKAGE = "com.kakao.talk.revanced"

    val SUPPORTED_PACKAGES: Set<String> = setOf(OFFICIAL_PACKAGE, REVANCED_PACKAGE)

    fun isSupported(packageName: String): Boolean = packageName in SUPPORTED_PACKAGES

    fun resolve(
        packageName: String,
        nativeResolver: (String) -> KakaoTalkTargetContext? = ::resolveNativeTarget,
    ): KakaoTalkTargetContext {
        nativeResolver(packageName)?.let { return it }
        require(isSupported(packageName)) { "unsupported KakaoTalk package: $packageName" }
        error("bridge core unavailable to resolve KakaoTalk target")
    }

    private fun resolveNativeTarget(packageName: String): KakaoTalkTargetContext? {
        if (!bridgeCoreLoadLibraryOnce()) return null
        return runCatching {
            val envelope =
                BridgeCoreEnvelope.parse(
                    BridgeCoreJniKakaoTarget.nativeResolveKakaoTarget(packageName),
                )
            if (!envelope.isOk) {
                null
            } else {
                val resolvedPackageName = envelope.string("packageName") ?: return@runCatching null
                val dexPackage = envelope.string("dexPackage") ?: return@runCatching null
                KakaoTalkTargetContext(
                    packageName = resolvedPackageName,
                    dexPackage = dexPackage,
                )
            }
        }.getOrElse { error ->
            bridgeCoreLogError("bridge-core KakaoTalk target policy threw", error)
            null
        }
    }
}

internal data class KakaoTalkTargetContext(
    val packageName: String,
    val dexPackage: String = KakaoTalkTarget.OFFICIAL_PACKAGE,
) {
    fun dexClassName(suffix: String): String = "$dexPackage.$suffix"

    fun dataPath(suffix: String): String = "/data/data/$packageName/$suffix"

    fun externalDataPath(suffix: String): String = "/sdcard/Android/data/$packageName/$suffix"
}
