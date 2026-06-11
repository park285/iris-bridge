@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime.kakao.userdb

import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal fun findExplicitFactorySingleton(
    clazz: Class<*>,
    holderValue: Any,
    dependencies: List<Any>,
    methods: Array<Method>,
    failures: MutableList<String>,
): Any? {
    for (accessor in methods) {
        if (accessor.parameterCount == 0 || accessor.returnType != clazz || Modifier.isStatic(accessor.modifiers)) continue
        val args = explicitFactoryArguments(dependencies, accessor)
        if (args == null) {
            failures += "explicit factory ${methodSignature(accessor)} argument shape rejected"
            continue
        }
        invokeSingletonFactory("explicit factory", holderValue, accessor, args, failures)?.let { return it }
    }
    return null
}

internal fun findHolderAccessorSingleton(
    clazz: Class<*>,
    holderValue: Any,
    methods: Array<Method>,
    failures: MutableList<String>,
): Any? {
    for (accessor in methods) {
        if (accessor.parameterCount != 0 || accessor.returnType != clazz) continue
        invokeSingletonFactory("holder accessor", holderValue, accessor, emptyArray<Any?>(), failures)?.let { return it }
    }
    return null
}

internal fun findDefaultFactorySingleton(
    clazz: Class<*>,
    holderValue: Any,
    methods: Array<Method>,
    failures: MutableList<String>,
): Any? {
    for (accessor in methods) {
        if (accessor.returnType != clazz || !Modifier.isStatic(accessor.modifiers)) continue
        val args = defaultFactoryArguments(holderValue, accessor)
        if (args == null) {
            failures += "default factory ${methodSignature(accessor)} argument shape rejected"
            continue
        }
        invokeSingletonFactory("default factory", null, accessor, args, failures)?.let { return it }
    }
    return null
}

private fun invokeSingletonFactory(
    label: String,
    target: Any?,
    method: Method,
    args: Array<Any?>,
    failures: MutableList<String>,
): Any? {
    val result =
        runCatching {
            method.isAccessible = true
            method.invoke(target, *args)
        }
    val value = result.getOrNull()
    if (value != null) return value
    failures +=
        if (result.isSuccess) {
            "$label ${methodSignature(method)} returned null"
        } else {
            "$label ${methodSignature(method)} failed ${describeThrowable(checkNotNull(result.exceptionOrNull()))}"
        }
    return null
}

private fun defaultFactoryArguments(
    holderValue: Any,
    method: Method,
): Array<Any?>? {
    val parameterTypes = method.parameterTypes
    if (parameterTypes.size < 3 || parameterTypes[0] != holderValue.javaClass) {
        return null
    }
    val maskIndex = parameterTypes.lastIndex - 1
    if (maskIndex <= 0 || parameterTypes[maskIndex] != Int::class.javaPrimitiveType) {
        return null
    }
    val originalParameterCount = parameterTypes.size - 3
    if (originalParameterCount !in 1..30) {
        return null
    }
    val args = arrayOfNulls<Any?>(parameterTypes.size)
    args[0] = holderValue
    for (index in 1..originalParameterCount) {
        args[index] = defaultPlaceholderValue(parameterTypes[index])
    }
    args[maskIndex] = (1 shl originalParameterCount) - 1
    args[parameterTypes.lastIndex] = null
    return args
}

private fun explicitFactoryArguments(
    dependencies: List<Any>,
    method: Method,
): Array<Any?>? {
    if (dependencies.isEmpty() || method.parameterTypes.isEmpty()) return null
    return method.parameterTypes
        .map { parameterType ->
            dependencies.firstOrNull { dependency -> parameterType.isInstance(dependency) } ?: return null
        }.toTypedArray()
}

private fun defaultPlaceholderValue(type: Class<*>): Any? =
    when (type) {
        java.lang.Boolean.TYPE -> false
        java.lang.Byte.TYPE -> 0.toByte()
        java.lang.Short.TYPE -> 0.toShort()
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Float.TYPE -> 0F
        java.lang.Double.TYPE -> 0.0
        java.lang.Character.TYPE -> 0.toChar()
        else -> null
    }
