@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime.kakao.userdb

import android.util.Log
import party.qwer.iris.imagebridge.runtime.kakao.DexClassScanner
import party.qwer.iris.imagebridge.runtime.kakao.classregistry.KAKAO_CLASS_REGISTRY_TAG
import party.qwer.iris.imagebridge.runtime.kakao.classregistry.discoverClass
import party.qwer.iris.imagebridge.runtime.kakao.classregistry.isConcreteClass
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal fun discoverKakaoUserDatabaseAccess(classLoader: ClassLoader): KakaoUserDatabaseAccess? =
    runCatching {
        val scanner = DexClassScanner(classLoader)
        val dataSourceClass =
            discoverClass(
                classLoader = classLoader,
                scanner = scanner,
                lastKnownNames = arrayOf("com.kakao.talk.singleton.UserDatabaseDataSource"),
                label = "UserDatabaseDataSource",
                signatureMatcher = ::matchesUserDatabaseDataSource,
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
        Log.w(KAKAO_CLASS_REGISTRY_TAG, "Kakao UserDatabase discovery failed: ${error.message}")
        null
    }

private fun matchesUserDatabaseDataSource(clazz: Class<*>): Boolean {
    if (!isConcreteClass(clazz)) return false
    return findGetUserByIdV2Method(clazz) != null
}

private fun findGetUserByIdV2Method(clazz: Class<*>): Method? =
    clazz.methods.firstOrNull { method ->
        method.name == "getUserByIdV2" &&
            method.parameterCount == 2 &&
            (
                method.parameterTypes[0] == Long::class.javaPrimitiveType ||
                    method.parameterTypes[0] == Long::class.javaObjectType
            ) &&
            kotlin.coroutines.Continuation::class.java.isAssignableFrom(method.parameterTypes[1])
    }

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
