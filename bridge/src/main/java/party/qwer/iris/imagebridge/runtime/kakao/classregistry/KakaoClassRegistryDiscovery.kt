package party.qwer.iris.imagebridge.runtime.kakao.classregistry

import party.qwer.iris.imagebridge.runtime.kakao.KakaoClassRegistry
import party.qwer.iris.imagebridge.runtime.kakao.KakaoTalkTargetContext

internal object KakaoClassRegistryDiscovery {
    fun discover(
        classLoader: ClassLoader,
        target: KakaoTalkTargetContext,
    ): KakaoClassRegistry = discoverKakaoClassRegistry(classLoader, target)
}
