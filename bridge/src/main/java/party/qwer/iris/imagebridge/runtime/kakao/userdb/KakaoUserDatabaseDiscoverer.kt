@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime.kakao.userdb

import android.content.Context
import android.util.Log
import party.qwer.iris.imagebridge.runtime.kakao.DexClassScanner
import party.qwer.iris.imagebridge.runtime.kakao.classregistry.KAKAO_CLASS_REGISTRY_TAG
import party.qwer.iris.imagebridge.runtime.kakao.classregistry.discoverClass
import party.qwer.iris.imagebridge.runtime.kakao.classregistry.isConcreteClass
import party.qwer.iris.imagebridge.runtime.kakao.isKotlinContinuationType
import java.lang.reflect.Method

internal fun discoverKakaoUserDatabaseAccess(
    classLoader: ClassLoader,
    scanFallback: Boolean = true,
    context: Context? = null,
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
            resolveUserDatabaseSingleton(
                dataSourceClass,
                dependencies = resolveUserDatabaseFactoryDependencies(context, classLoader),
            )
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

private val USER_DATABASE_DATA_SOURCE_NAMES =
    arrayOf(
        USER_DATABASE_DATA_SOURCE_CLASS_NAME,
        "C40.C2865n2",
        "C40.n2",
        "X20.C36045r2",
        "X20.r2",
    )

private val KNOWN_OBFUSCATED_USER_DATABASE_DATA_SOURCE_NAMES =
    setOf(
        "C40.C2865n2",
        "C40.n2",
        "X20.C36045r2",
        "X20.r2",
    )
private val OBFUSCATED_GET_USER_BY_ID_V2_METHOD_NAMES = setOf("t", "m9598t", "m113412t")

private fun discoverUserDatabaseDataSourceClass(
    classLoader: ClassLoader,
    scanner: DexClassScanner,
    scanFallback: Boolean,
): Class<*> {
    if (scanFallback) {
        logKnownUserDatabaseCandidateDiagnostics(
            classLoader = classLoader,
            names = USER_DATABASE_DATA_SOURCE_NAMES,
            signatureMatcher = ::matchesUserDatabaseDataSource,
        )
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
    val obfuscatedMethod =
        signatureMatches.singleOrNull { method ->
            method.declaringClass == clazz &&
                method.name in OBFUSCATED_GET_USER_BY_ID_V2_METHOD_NAMES
        } ?: return null
    if (clazz.name in KNOWN_OBFUSCATED_USER_DATABASE_DATA_SOURCE_NAMES) {
        return obfuscatedMethod
    }
    if (!hasSingleGetUserByIdV2DebugMetadata(clazz)) return null
    return obfuscatedMethod
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
            .firstOrNull { method ->
                method.parameterCount == 0 &&
                    (
                        method.name == name ||
                            method.name.endsWith(name, ignoreCase = true)
                    )
            }?.invoke(this) as? String
    }.getOrNull()
