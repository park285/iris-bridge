@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime.kakao.userdb

import android.util.Log
import party.qwer.iris.imagebridge.runtime.kakao.DexClassScanner
import party.qwer.iris.imagebridge.runtime.kakao.classregistry.KAKAO_CLASS_REGISTRY_TAG
import party.qwer.iris.imagebridge.runtime.kakao.classregistry.discoverClass
import party.qwer.iris.imagebridge.runtime.kakao.classregistry.isConcreteClass
import party.qwer.iris.imagebridge.runtime.kakao.isKotlinContinuationType
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal fun discoverKakaoUserDatabaseAccess(
    classLoader: ClassLoader,
    scanFallback: Boolean = true,
): KakaoUserDatabaseAccess? =
    runCatching {
        val scanner = DexClassScanner(classLoader)
        val dataSourceClass =
            discoverUserDatabaseDataSourceClass(
                classLoader = classLoader,
                scanner = scanner,
                scanFallback = scanFallback,
            )
        val singleton =
            resolveUserDatabaseSingleton(dataSourceClass)
                ?: error("UserDatabaseDataSource singleton not found on ${dataSourceClass.name}")
        val getUserByIdV2Method =
            findGetUserByIdV2Method(dataSourceClass)
                ?: error("getUserByIdV2(Long, Continuation) not found on ${dataSourceClass.name}")
        KakaoUserDatabaseAccess(
            singleton = singleton,
            getUserByIdV2Method = getUserByIdV2Method,
        )
    }.getOrElse { error ->
        runCatching {
            Log.w(KAKAO_CLASS_REGISTRY_TAG, "Kakao UserDatabase discovery failed: ${error.message}")
        }
        null
    }

private const val USER_DATABASE_DATA_SOURCE_CLASS_NAME = "com.kakao.talk.singleton.UserDatabaseDataSource"
private const val USER_DATABASE_DATA_SOURCE_FILE_NAME = "UserDatabaseDataSource.kt"
private const val OBFUSCATED_GET_USER_BY_ID_V2_METHOD_NAME = "t"

private val USER_DATABASE_DATA_SOURCE_NAMES =
    arrayOf(
        USER_DATABASE_DATA_SOURCE_CLASS_NAME,
        "X20.r2",
    )

private fun discoverUserDatabaseDataSourceClass(
    classLoader: ClassLoader,
    scanner: DexClassScanner,
    scanFallback: Boolean,
): Class<*> {
    if (scanFallback) {
        return discoverClass(
            classLoader = classLoader,
            scanner = scanner,
            lastKnownNames = USER_DATABASE_DATA_SOURCE_NAMES,
            label = "UserDatabaseDataSource",
            signatureMatcher = ::matchesUserDatabaseDataSource,
        )
    }
    return USER_DATABASE_DATA_SOURCE_NAMES.firstNotNullOfOrNull { name ->
        runCatching {
            Class.forName(name, false, classLoader)
        }.getOrNull()?.takeIf(::matchesUserDatabaseDataSource)
    } ?: error("UserDatabaseDataSource not found at known names ${USER_DATABASE_DATA_SOURCE_NAMES.toList()}")
}

private fun matchesUserDatabaseDataSource(clazz: Class<*>): Boolean {
    if (!isConcreteClass(clazz)) return false
    return findGetUserByIdV2Method(clazz) != null
}

private fun findGetUserByIdV2Method(clazz: Class<*>): Method? {
    val signatureMatches = clazz.methods.filter(::hasGetUserByIdV2Signature)
    return signatureMatches.firstOrNull { method ->
        isGetUserByIdV2MethodName(method.name)
    } ?: findObfuscatedGetUserByIdV2Method(clazz, signatureMatches)
}

private fun hasGetUserByIdV2Signature(method: Method): Boolean =
    method.parameterCount == 2 &&
        (
            method.parameterTypes[0] == Long::class.javaPrimitiveType ||
                method.parameterTypes[0] == Long::class.javaObjectType
        ) &&
        method.parameterTypes[1].isKotlinContinuationType()

private fun isGetUserByIdV2MethodName(name: String): Boolean = name == "getUserByIdV2" || name.startsWith("getUserByIdV2-")

private fun findObfuscatedGetUserByIdV2Method(
    clazz: Class<*>,
    signatureMatches: List<Method>,
): Method? {
    if (!hasSingleGetUserByIdV2DebugMetadata(clazz)) return null
    return signatureMatches.singleOrNull { method ->
        method.declaringClass == clazz &&
            method.name == OBFUSCATED_GET_USER_BY_ID_V2_METHOD_NAME
    }
}

private fun hasSingleGetUserByIdV2DebugMetadata(clazz: Class<*>): Boolean =
    runCatching {
        clazz.declaredClasses.count { nestedClass ->
            nestedClass.annotations.any { annotation ->
                annotation.stringValue("c") == USER_DATABASE_DATA_SOURCE_CLASS_NAME &&
                    annotation.stringValue("f") == USER_DATABASE_DATA_SOURCE_FILE_NAME &&
                    annotation.stringValue("m")?.startsWith("getUserByIdV2") == true
            }
        } == 1
    }.getOrDefault(false)

private fun Annotation.stringValue(name: String): String? =
    runCatching {
        javaClass.methods
            .firstOrNull { method -> method.name == name && method.parameterCount == 0 }
            ?.invoke(this) as? String
    }.getOrNull()

private fun resolveUserDatabaseSingleton(clazz: Class<*>): Any? {
    clazz.declaredFields
        .filter { field -> Modifier.isStatic(field.modifiers) && field.type == clazz }
        .forEach { field ->
            runCatching {
                field.isAccessible = true
                return field.get(null)
            }
        }
    clazz.declaredFields
        .filter { field -> Modifier.isStatic(field.modifiers) && field.type != clazz }
        .forEach { holder ->
            runCatching {
                holder.isAccessible = true
                val holderValue = holder.get(null) ?: return@forEach
                holder.type.methods
                    .filter { method -> method.parameterCount == 0 && method.returnType == clazz }
                    .forEach { accessor ->
                        accessor.isAccessible = true
                        accessor.invoke(holderValue)?.let { return it }
                    }
            }
        }
    return null
}
