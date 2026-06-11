@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime.kakao.userdb

import android.content.Context
import android.util.Log
import party.qwer.iris.imagebridge.runtime.kakao.classregistry.KAKAO_CLASS_REGISTRY_TAG
import java.lang.reflect.Modifier

internal fun resolveUserDatabaseFactoryDependencies(
    context: Context?,
    classLoader: ClassLoader,
): List<Any> {
    if (context == null) return emptyList()
    val applicationContext = context.applicationContext ?: context
    val cryptoDatabase = resolveCryptoUserDatabase(applicationContext, classLoader)
    val dispatcher = resolveCoreDbDispatcher(classLoader)
    return listOfNotNull(cryptoDatabase, dispatcher)
}

private fun resolveCryptoUserDatabase(
    context: Context,
    classLoader: ClassLoader,
): Any? =
    runCatching {
        val userComponentProviderClass =
            USER_COMPONENT_PROVIDER_CLASS_NAMES.firstNotNullOfOrNull { className ->
                runCatching { Class.forName(className, false, classLoader) }.getOrNull()
            } ?: return null
        val userComponent =
            userComponentProviderClass.declaredMethods
                .firstOrNull { method ->
                    Modifier.isStatic(method.modifiers) &&
                        method.parameterTypes.contentEquals(arrayOf(Context::class.java))
                }?.apply { isAccessible = true }
                ?.invoke(null, context)
                ?: return null
        userComponent.javaClass.methods
            .firstOrNull { method ->
                method.parameterCount == 0 &&
                    method.returnType.name == CRYPTO_USER_DATABASE_CLASS_NAME
            }?.apply { isAccessible = true }
            ?.invoke(userComponent)
    }.getOrElse { error ->
        Log.w(KAKAO_CLASS_REGISTRY_TAG, "CryptoUserDatabase dependency discovery failed: ${error.javaClass.name}: ${error.message}")
        null
    }

private fun resolveCoreDbDispatcher(classLoader: ClassLoader): Any? =
    runCatching {
        val dispatcherProviderClass =
            CORE_DB_DISPATCHER_CLASS_NAMES.firstNotNullOfOrNull { className ->
                runCatching { Class.forName(className, false, classLoader) }.getOrNull()
            } ?: return null
        dispatcherProviderClass.declaredMethods
            .firstOrNull { method ->
                Modifier.isStatic(method.modifiers) &&
                    method.parameterCount == 0 &&
                    method.returnType.name == CORE_DB_DISPATCHER_CLASS_NAME
            }?.apply { isAccessible = true }
            ?.invoke(null)
    }.getOrElse { error ->
        Log.w(KAKAO_CLASS_REGISTRY_TAG, "CoreDbDispatcher dependency discovery failed: ${error.javaClass.name}: ${error.message}")
        null
    }

private const val CRYPTO_USER_DATABASE_CLASS_NAME = "com.kakao.talk.core.user.data.local.CryptoUserDatabase"
private const val CORE_DB_DISPATCHER_CLASS_NAME = "fD0.K"
private val USER_COMPONENT_PROVIDER_CLASS_NAMES = listOf("Ui.z", "p1293Ui.C32943z")
private val CORE_DB_DISPATCHER_CLASS_NAMES = listOf("Ht.a", "p494Ht.C11731a")
