@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime.kakao.memberfetch

import android.util.Log
import party.qwer.iris.imagebridge.runtime.kakao.classregistry.KAKAO_CLASS_REGISTRY_TAG
import party.qwer.iris.imagebridge.runtime.kakao.isKotlinContinuationType
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal fun discoverKakaoProfileDetailAccess(classLoader: ClassLoader): KakaoProfileDetailAccess? =
    runCatching {
        val profileApiClass = loadFirstClass(classLoader, PROFILE_API_CLASS_NAMES)
        val apiClientClass = loadFirstClass(classLoader, API_CLIENT_CLASS_NAMES)
        val apiClient = resolveApiClientSingleton(apiClientClass)
        val apiFactoryMethod =
            findApiFactoryMethod(apiClientClass)
                ?: error("Kakao API factory method not found on ${apiClientClass.name}")
        val profileApi = createProfileApi(classLoader, profileApiClass, apiClient, apiFactoryMethod)
        val otherMethod =
            findOtherProfileMethod(profileApiClass, OTHER_PROFILE_METHOD_NAMES, "profile25/other")
                ?: error("profile25/other method not found on ${profileApiClass.name}")
        val refreshMethod = findOtherProfileMethod(profileApiClass, REFRESH_OTHER_PROFILE_METHOD_NAMES, "profile25/other/refresh")
        KakaoProfileDetailAccess(
            profileApi = checkNotNull(profileApi) { "profile API proxy is null" },
            otherProfileMethod = otherMethod,
            refreshOtherProfileMethod = refreshMethod,
        )
    }.getOrElse { error ->
        Log.w(KAKAO_CLASS_REGISTRY_TAG, "Kakao profile detail discovery failed: ${rootMessage(error)}")
        null
    }

private fun loadFirstClass(
    classLoader: ClassLoader,
    names: Array<String>,
): Class<*> =
    names.firstNotNullOfOrNull { name ->
        runCatching { Class.forName(name, false, classLoader) }.getOrNull()
    } ?: error("none of ${names.toList()} could be loaded")

private fun resolveApiClientSingleton(apiClientClass: Class<*>): Any =
    apiClientClass.declaredFields
        .firstOrNull { field -> Modifier.isStatic(field.modifiers) && apiClientClass.isAssignableFrom(field.type) }
        ?.let { field -> field.apply { isAccessible = true }.get(null) }
        ?: error("Kakao API client singleton not found on ${apiClientClass.name}")

private fun createProfileApi(
    classLoader: ClassLoader,
    profileApiClass: Class<*>,
    apiClient: Any,
    apiFactoryMethod: Method,
): Any {
    var factoryFailure: Throwable? = null
    runCatching {
        apiFactoryMethod.apply { isAccessible = true }.invoke(apiClient, profileApiClass)
    }.onSuccess { profileApi ->
        return checkNotNull(profileApi) { "profile API proxy is null" }
    }.onFailure { error ->
        factoryFailure = error
        Log.w(KAKAO_CLASS_REGISTRY_TAG, "Kakao profile API factory failed: ${rootMessage(error)}")
    }
    runCatching {
        createProfileApiFromProvider(classLoader, profileApiClass)
    }.onSuccess { profileApi ->
        if (profileApi != null) {
            return profileApi
        }
    }.onFailure { error ->
        Log.w(KAKAO_CLASS_REGISTRY_TAG, "Kakao profile API provider failed: ${rootMessage(error)}")
    }
    throw factoryFailure ?: IllegalStateException("Kakao profile API proxy could not be created")
}

private fun createProfileApiFromProvider(
    classLoader: ClassLoader,
    profileApiClass: Class<*>,
): Any? {
    val providerClass =
        PROFILE_API_PROVIDER_CLASS_NAMES.firstNotNullOfOrNull { name ->
            runCatching { Class.forName(name, false, classLoader) }.getOrNull()
        } ?: return null
    val provider =
        providerClass.declaredFields
            .firstOrNull { field -> Modifier.isStatic(field.modifiers) && providerClass.isAssignableFrom(field.type) }
            ?.let { field -> field.apply { isAccessible = true }.get(null) }
            ?: return null
    val providerMethod =
        findProfileApiProviderMethod(providerClass, profileApiClass)
            ?: return null
    return providerMethod.apply { isAccessible = true }.invoke(provider)
}

private fun findProfileApiProviderMethod(
    providerClass: Class<*>,
    profileApiClass: Class<*>,
): Method? {
    val candidates =
        allMethods(providerClass).filter { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.parameterCount == 0 &&
                profileApiClass.isAssignableFrom(method.returnType)
        }
    return PROFILE_API_PROVIDER_METHOD_NAMES.firstNotNullOfOrNull { preferredName ->
        candidates.singleOrNull { method -> method.name == preferredName }
    } ?: candidates.singleOrNull()
}

private fun findApiFactoryMethod(apiClientClass: Class<*>): Method? {
    val candidates =
        allMethods(apiClientClass).filter { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.parameterCount == 1 &&
                method.parameterTypes[0] == Class::class.java &&
                method.returnType == Any::class.java
        }
    return API_FACTORY_METHOD_NAMES.firstNotNullOfOrNull { preferredName ->
        candidates.singleOrNull { method -> method.name == preferredName }
    } ?: candidates.singleOrNull()
}

private fun findOtherProfileMethod(
    profileApiClass: Class<*>,
    preferredNames: List<String>,
    endpoint: String,
): Method? {
    val candidates = allMethods(profileApiClass).filter(::isOtherProfileMethodSignature)
    return preferredNames.firstNotNullOfOrNull { preferredName ->
        candidates.singleOrNull { method -> method.name == preferredName }
    } ?: candidates.singleOrNull { method -> method.hasEndpoint(endpoint) }
}

private fun isOtherProfileMethodSignature(method: Method): Boolean =
    !Modifier.isStatic(method.modifiers) &&
        method.parameterCount == 4 &&
        method.parameterTypes[0] == Long::class.javaPrimitiveType &&
        method.parameterTypes[1] == String::class.java &&
        method.parameterTypes[2] == java.lang.Long::class.java &&
        method.parameterTypes[3].isKotlinContinuationType() &&
        method.returnType == Any::class.java

private fun Method.hasEndpoint(endpoint: String): Boolean =
    annotations.any { annotation ->
        annotation.javaClass.methods.any { method ->
            method.parameterCount == 0 &&
                method.returnType == String::class.java &&
                runCatching { method.invoke(annotation) == endpoint }.getOrDefault(false)
        }
    }

private fun allMethods(type: Class<*>): List<Method> =
    (type.methods.asSequence() + type.declaredMethods.asSequence())
        .distinctBy(Method::toGenericString)
        .toList()
