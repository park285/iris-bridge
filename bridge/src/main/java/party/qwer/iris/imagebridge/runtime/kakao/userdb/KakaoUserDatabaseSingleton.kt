@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime.kakao.userdb

import java.lang.reflect.Field
import java.lang.reflect.Modifier

internal fun resolveUserDatabaseSingleton(
    clazz: Class<*>,
    dependencies: List<Any> = emptyList(),
): Any? {
    val failures = mutableListOf<String>()
    resolveSelfSingleton(clazz, failures)?.let { return it }
    resolveStaticAccessorSingleton(clazz, failures)?.let { return it }
    resolveHolderSingleton(clazz, dependencies, failures)?.let { return it }
    logKnownSingletonFailure(clazz, failures)
    return null
}

private fun resolveSelfSingleton(
    clazz: Class<*>,
    failures: MutableList<String>,
): Any? {
    for (field in clazz.declaredFields) {
        if (!Modifier.isStatic(field.modifiers) || field.type != clazz) continue
        val result =
            runCatching {
                field.isAccessible = true
                field.get(null)
            }
        result.getOrNull()?.let { return it }
        failures +=
            if (result.isSuccess) {
                "self field ${field.name} was null"
            } else {
                "self field ${field.name} failed ${describeThrowable(checkNotNull(result.exceptionOrNull()))}"
            }
    }
    return null
}

private fun resolveStaticAccessorSingleton(
    clazz: Class<*>,
    failures: MutableList<String>,
): Any? {
    for (method in clazz.declaredMethods) {
        if (!Modifier.isStatic(method.modifiers) || method.parameterCount != 0 || method.returnType != clazz) continue
        val result =
            runCatching {
                method.isAccessible = true
                method.invoke(null)
            }
        result.getOrNull()?.let { return it }
        failures +=
            if (result.isSuccess) {
                "static accessor ${methodSignature(method)} returned null"
            } else {
                "static accessor ${methodSignature(method)} failed ${describeThrowable(checkNotNull(result.exceptionOrNull()))}"
            }
    }
    return null
}

private fun resolveHolderSingleton(
    clazz: Class<*>,
    dependencies: List<Any>,
    failures: MutableList<String>,
): Any? {
    for (holder in clazz.declaredFields) {
        if (!Modifier.isStatic(holder.modifiers) || holder.type == clazz) continue
        val holderValue = loadSingletonHolder(holder, failures) ?: continue
        findHolderSingleton(clazz, holder, holderValue, dependencies, failures)?.let { return it }
    }
    return null
}

private fun loadSingletonHolder(
    holder: Field,
    failures: MutableList<String>,
): Any? {
    val result =
        runCatching {
            holder.isAccessible = true
            holder.get(null)
        }
    val holderLabel = "holder field ${holder.name}:${holder.type.name}"
    val value = result.getOrNull()
    if (value != null) return value
    failures +=
        if (result.isSuccess) {
            "$holderLabel was null"
        } else {
            "$holderLabel failed ${describeThrowable(checkNotNull(result.exceptionOrNull()))}"
        }
    return null
}

private fun findHolderSingleton(
    clazz: Class<*>,
    holder: Field,
    holderValue: Any,
    dependencies: List<Any>,
    failures: MutableList<String>,
): Any? {
    val methods = holder.type.declaredMethods
    failures += "holder field ${holder.name}:${holder.type.name} methods=${methods.joinToString { methodSignature(it) }}"
    findExplicitFactorySingleton(clazz, holderValue, dependencies, methods, failures)?.let { return it }
    findHolderAccessorSingleton(clazz, holderValue, methods, failures)?.let { return it }
    findDefaultFactorySingleton(clazz, holderValue, methods, failures)?.let { return it }
    return null
}
