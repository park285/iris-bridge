@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime.kakao.memberfetch

import android.util.Log
import party.qwer.iris.imagebridge.runtime.kakao.classregistry.KAKAO_CLASS_REGISTRY_TAG

internal class KakaoMemberProfileParser(
    private val access: KakaoMemberFetchAccess,
) {
    fun parseProfiles(
        rawResult: Any,
        wantedUserIds: Set<Long>,
    ): Map<Long, UpstreamMemberProfile> {
        val resultPayload = unwrapLocoResultPayload(rawResult) ?: return emptyMap()
        access.unwrapErrorMethod
            .apply { isAccessible = true }
            .invoke(null, resultPayload)
            ?.let { error ->
                Log.w(KAKAO_CLASS_REGISTRY_TAG, "upstream member fetch failed: $error")
                return emptyMap()
            }
        val response =
            access.unwrapValueMethod
                .apply { isAccessible = true }
                .invoke(null, resultPayload)
                ?: return emptyMap()
        return runCatching {
            extractMembers(response)
                .mapNotNull(::memberObjectToProfile)
                .filter { profile -> profile.userId in wantedUserIds }
                .associateBy(UpstreamMemberProfile::userId)
        }.getOrElse { error ->
            Log.w(KAKAO_CLASS_REGISTRY_TAG, "member profile parse failed: ${rootMessage(error)}")
            emptyMap()
        }
    }

    private fun unwrapLocoResultPayload(rawResult: Any): Any? {
        if (!access.resultClass.isInstance(rawResult)) {
            return rawResult
        }
        val payloadMethod =
            rawResult.javaClass.methods.singleOrNull { method ->
                method.name == "j" &&
                    method.parameterCount == 0 &&
                    method.returnType == Any::class.java
            } ?: return rawResult
        return payloadMethod.apply { isAccessible = true }.invoke(rawResult)
    }

    private fun extractMembers(response: Any): List<Any> {
        val listMethod =
            response.javaClass.methods.firstOrNull { method ->
                method.parameterCount == 0 &&
                    List::class.java.isAssignableFrom(method.returnType)
            } ?: error("member fetch response has no member list accessor on ${response.javaClass.name}")
        @Suppress("UNCHECKED_CAST")
        return listMethod.apply { isAccessible = true }.invoke(response) as? List<Any> ?: emptyList()
    }
}
