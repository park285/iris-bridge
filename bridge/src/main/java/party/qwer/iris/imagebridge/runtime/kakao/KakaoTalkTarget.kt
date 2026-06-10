package party.qwer.iris.imagebridge.runtime.kakao

internal object KakaoTalkTarget {
    const val OFFICIAL_PACKAGE = "com.kakao.talk"
    const val REVANCED_PACKAGE = "com.kakao.talk.revanced"

    val SUPPORTED_PACKAGES: Set<String> = setOf(OFFICIAL_PACKAGE, REVANCED_PACKAGE)

    fun isSupported(packageName: String): Boolean = packageName in SUPPORTED_PACKAGES

    fun resolve(packageName: String): KakaoTalkTargetContext {
        require(isSupported(packageName)) { "unsupported KakaoTalk package: $packageName" }
        return KakaoTalkTargetContext(packageName)
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
