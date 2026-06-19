@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime.kakao.userdb

import android.util.Log
import party.qwer.iris.imagebridge.runtime.kakao.classregistry.KAKAO_CLASS_REGISTRY_TAG
import party.qwer.iris.imagebridge.runtime.kakao.classregistry.isConcreteClass
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

internal fun logKnownUserDatabaseCandidateDiagnostics(
    classLoader: ClassLoader,
    names: Array<String>,
    signatureMatcher: (Class<*>) -> Boolean,
) {
    val failureMessages = mutableListOf<String>()
    names.forEach { name ->
        val clazz =
            runCatching {
                Class.forName(name, false, classLoader)
            }.getOrElse { error ->
                failureMessages += "UserDatabaseDataSource candidate load failed: $name: ${error.javaClass.name}"
                return@forEach
            }
        val matches =
            runCatching {
                signatureMatcher(clazz)
            }.getOrElse { error ->
                Log.w(
                    KAKAO_CLASS_REGISTRY_TAG,
                    "UserDatabaseDataSource candidate matcher threw: ${clazz.name}: ${error.javaClass.name}: ${error.message}",
                )
                false
            }
        if (matches) return
        failureMessages +=
            "UserDatabaseDataSource candidate rejected: name=${clazz.name} " +
            "concrete=${isConcreteClass(clazz)} methods=${userDatabaseMethodDiagnostics(clazz)} " +
            "metadata=${userDatabaseMetadataDiagnostics(clazz)}"
    }
    failureMessages.forEach { message ->
        Log.w(KAKAO_CLASS_REGISTRY_TAG, message)
    }
}

private fun userDatabaseMethodDiagnostics(clazz: Class<*>): String =
    clazz.methods
        .filter { method ->
            method.name in METHOD_DIAGNOSTIC_NAMES ||
                method.parameterTypes.any { parameterType -> parameterType.name == "kotlin.coroutines.Continuation" }
        }.take(24)
        .joinToString(prefix = "[", postfix = "]") { method ->
            method.parameterTypes.joinToString(
                prefix = "${method.name}(",
                postfix = "):${method.returnType.name}",
            ) { parameterType -> parameterType.name }
        }

private fun userDatabaseMetadataDiagnostics(clazz: Class<*>): String =
    clazz.declaredClasses
        .flatMap { nestedClass ->
            nestedClass.annotations.mapNotNull { annotation ->
                val values = annotationStringValues(annotation)
                if (values.values.none(::isUserDatabaseMetadataValue)) {
                    return@mapNotNull null
                }
                "${nestedClass.simpleName}:${values.entries.joinToString { entry -> "${entry.key}=${entry.value}" }}"
            }
        }.take(24)
        .joinToString(prefix = "[", postfix = "]")

private fun annotationStringValues(annotation: Annotation): Map<String, String> =
    annotation.javaClass.methods
        .filter { method -> method.parameterCount == 0 && method.returnType == String::class.java }
        .mapNotNull { method ->
            runCatching {
                method.name to (method.invoke(annotation) as? String ?: return@mapNotNull null)
            }.getOrNull()
        }.toMap()

private fun isUserDatabaseMetadataValue(value: String): Boolean = value.contains("UserDatabaseDataSource") || value.startsWith("getUser")

internal fun logKnownSingletonFailure(
    clazz: Class<*>,
    failures: List<String>,
) {
    if (clazz.name !in KNOWN_SINGLETON_DIAGNOSTIC_CLASS_NAMES) return
    Log.w(KAKAO_CLASS_REGISTRY_TAG, "UserDatabaseDataSource singleton diagnostics for ${clazz.name}: ${failures.joinToString()}")
}

internal fun methodSignature(method: Method): String =
    method.parameterTypes.joinToString(
        prefix = "${method.name}(",
        postfix = "):${method.returnType.name}",
    ) { parameterType -> parameterType.name }

internal fun describeThrowable(error: Throwable): String {
    val target = (error as? InvocationTargetException)?.targetException
    return if (target == null) {
        "${error.javaClass.name}: ${error.message}"
    } else {
        "${error.javaClass.name} target=${target.javaClass.name}: ${target.message}"
    }
}

private val METHOD_DIAGNOSTIC_NAMES = setOf("getUserByIdV2", "getUserByIdV2-gIAlu-s", "t", "m113412t", "s", "m113411s")
private val KNOWN_SINGLETON_DIAGNOSTIC_CLASS_NAMES =
    setOf(
        "C40.n2",
        "C40.C2865n2",
        "X20.r2",
        "X20.C36045r2",
    )
