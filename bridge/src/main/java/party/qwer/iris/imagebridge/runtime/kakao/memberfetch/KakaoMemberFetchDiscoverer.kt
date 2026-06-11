@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime.kakao.memberfetch

import android.util.Log
import party.qwer.iris.imagebridge.runtime.kakao.DexClassScanner
import party.qwer.iris.imagebridge.runtime.kakao.classregistry.KAKAO_CLASS_REGISTRY_TAG
import party.qwer.iris.imagebridge.runtime.kakao.classregistry.discoverClass
import party.qwer.iris.imagebridge.runtime.kakao.classregistry.hasSelfReturningAccessor
import party.qwer.iris.imagebridge.runtime.kakao.classregistry.isBroadRoomResolverSignature
import party.qwer.iris.imagebridge.runtime.kakao.classregistry.isConcreteClass
import party.qwer.iris.imagebridge.runtime.kakao.isKotlinContinuationType
import java.lang.reflect.Modifier

internal fun discoverKakaoMemberFetchAccess(classLoader: ClassLoader): KakaoMemberFetchAccess? =
    runCatching {
        val scanner = DexClassScanner(classLoader)
        val clientClass =
            discoverClass(
                classLoader = classLoader,
                scanner = scanner,
                lastKnownNames =
                    arrayOf(
                        "com.kakao.talk.core.loco.Loco",
                        "ry0.C1298d",
                        "Xp.d",
                    ),
                label = "MemberFetchClient",
                signatureMatcher = ::matchesMemberFetchFacade,
            )
        val singleton =
            resolveMemberFetchSingleton(clientClass)
                ?: error("member-fetch client singleton not found on ${clientClass.name}")
        val fetchMembersMethod =
            findFetchMembersMethod(clientClass)
                ?: error("member-fetch client(long, List) not found on ${clientClass.name}")
        val roomFetchMembersMethod =
            findSuspendRoomMembersMethod(clientClass)
                ?.takeUnless { method -> method == fetchMembersMethod }
        val resultClass = discoverMemberFetchResultClass(classLoader, scanner)
        val unwrapValueMethod =
            resultClass.methods.singleOrNull { method ->
                Modifier.isStatic(method.modifiers) &&
                    method.name == "e" &&
                    method.parameterCount == 1
            } ?: error("member-fetch result#e(Object) not found on ${resultClass.name}")
        val unwrapErrorMethod =
            resultClass.methods.singleOrNull { method ->
                Modifier.isStatic(method.modifiers) &&
                    method.name == "d" &&
                    method.parameterCount == 1
            } ?: error("member-fetch result#d(Object) not found on ${resultClass.name}")
        KakaoMemberFetchAccess(
            clientSingleton = singleton,
            fetchMembersMethod = fetchMembersMethod,
            roomFetchMembersMethod = roomFetchMembersMethod,
            resultClass = resultClass,
            unwrapValueMethod = unwrapValueMethod,
            unwrapErrorMethod = unwrapErrorMethod,
        )
    }.getOrElse { error ->
        Log.w(KAKAO_CLASS_REGISTRY_TAG, "Kakao member-fetch discovery failed: ${error.message}")
        null
    }

private fun matchesMemberFetchFacade(clazz: Class<*>): Boolean {
    if (!isConcreteClass(clazz) || !hasMemberFetchSingletonCandidate(clazz)) {
        return false
    }
    if (clazz.declaredMethods.any(::isBroadRoomResolverSignature)) {
        return false
    }
    return findFetchMembersMethod(clazz) != null
}

internal fun matchesMemberFetchFacadeForTest(clazz: Class<*>): Boolean = matchesMemberFetchFacade(clazz)

private fun hasMemberFetchSingletonCandidate(clazz: Class<*>): Boolean = clazz.declaredFields.any { field -> Modifier.isStatic(field.modifiers) && field.type == clazz } || hasSelfReturningAccessor(clazz)

internal fun findFetchMembersMethodForTest(clazz: Class<*>): java.lang.reflect.Method? = findFetchMembersMethod(clazz)

private fun findFetchMembersMethod(clazz: Class<*>): java.lang.reflect.Method? =
    findSuspendRequestedMembersMethod(clazz)
        ?: findSuspendRoomMembersMethod(clazz)

private fun findSuspendRequestedMembersMethod(clazz: Class<*>): java.lang.reflect.Method? {
    val candidates =
        clazz.methods.filter { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.parameterCount == 3 &&
                method.parameterTypes[0] == Long::class.javaPrimitiveType &&
                List::class.java.isAssignableFrom(method.parameterTypes[1]) &&
                method.parameterTypes[2].isKotlinContinuationType() &&
                method.returnType == Any::class.java
        }
    return candidates.singleOrNull { method -> method.name == "Y" }
        ?: candidates.singleOrNull()
}

private fun findSuspendRoomMembersMethod(clazz: Class<*>): java.lang.reflect.Method? {
    val candidates =
        clazz.methods.filter { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.parameterCount == 2 &&
                method.parameterTypes[0] == Long::class.javaPrimitiveType &&
                method.parameterTypes[1].isKotlinContinuationType() &&
                method.returnType == Any::class.java
        }
    return candidates.singleOrNull { method -> method.name == "D" }
        ?: candidates.singleOrNull()
}
