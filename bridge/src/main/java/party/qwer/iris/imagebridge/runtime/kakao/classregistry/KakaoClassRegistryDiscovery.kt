package party.qwer.iris.imagebridge.runtime.kakao.classregistry

import party.qwer.iris.imagebridge.runtime.kakao.KakaoClassRegistry

internal object KakaoClassRegistryDiscovery {
    fun discover(classLoader: ClassLoader): KakaoClassRegistry = discoverKakaoClassRegistry(classLoader)
}
