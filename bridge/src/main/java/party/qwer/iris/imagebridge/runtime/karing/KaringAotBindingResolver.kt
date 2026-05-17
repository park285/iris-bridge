package party.qwer.iris.imagebridge.runtime.karing

private const val SSO_HELPER_CLASS = "com.kakao.talk.widget.webview.SsoHelper"
private const val SSO_TYPE_CLASS = "com.kakao.talk.widget.webview.SsoType"
private const val KOTLIN_FUNCTION1_CLASS = "kotlin.jvm.functions.Function1"
private const val SSO_TYPE_KAKAO = "Kakao"
private const val OAUTH_HELPER_CLASS = "yP.d"
private const val HARDWARE_CLASS = "IZ.V"
private const val REFRESH_CALLER = "iris-karing"

internal class KaringAotBindingResolver(
    private val classLoader: ClassLoader,
    timeoutMs: Long,
) {
    private val tgtRequester = KaringSsoTgtRequester(classLoader, timeoutMs)

    fun resolve(): KaringAotBinding {
        val classes = loadClasses()
        val singletons = resolveSingletons(classes)
        val methods = resolveMethods(classes)
        return KaringAotBinding(
            oauthHelper = singletons.oauthHelper,
            hardware = singletons.hardware,
            refresh = { methods.refresh.invoke(singletons.oauthHelper, REFRESH_CALLER) },
            accessToken = { methods.accessToken.invoke(singletons.oauthHelper) as? String ?: "" },
            deviceId = { methods.deviceId.invoke(singletons.hardware) as? String ?: "" },
            kaTgt = {
                tgtRequester.request(
                    singletons.ssoHelper,
                    singletons.ssoType,
                    methods.getTgtIfNeed,
                    classes.function1,
                )
            },
        )
    }

    private fun loadClasses(): KaringAotClasses =
        KaringAotClasses(
            ssoHelper =
                bindingStep("load SsoHelper") { Class.forName(SSO_HELPER_CLASS, false, classLoader) },
            ssoType =
                bindingStep("load SsoType") { Class.forName(SSO_TYPE_CLASS, false, classLoader) },
            function1 =
                bindingStep("load Function1") { Class.forName(KOTLIN_FUNCTION1_CLASS, false, classLoader) },
            oauth =
                bindingStep("load OAuthHelper") { Class.forName(OAUTH_HELPER_CLASS, false, classLoader) },
            hardware =
                bindingStep("load Hardware") { Class.forName(HARDWARE_CLASS, false, classLoader) },
        )

    private fun resolveSingletons(classes: KaringAotClasses): KaringAotSingletons {
        val ssoHelper =
            bindingStep("resolve SsoHelper singleton") {
                singleton(classes.ssoHelper, "INSTANCE", "karing sso helper unavailable")
            }
        val ssoType =
            bindingStep("resolve SsoType") {
                checkNotNull(classes.ssoType.getMethod("valueOf", String::class.java).invoke(null, SSO_TYPE_KAKAO)) {
                    "karing sso type unavailable"
                }
            }
        val oauthHelper =
            bindingStep("resolve OAuthHelper singleton") {
                singleton(classes.oauth, "a", "karing oauth helper unavailable")
            }
        val hardware =
            bindingStep("resolve Hardware singleton") {
                singleton(classes.hardware, "a", "karing hardware helper unavailable")
            }
        return KaringAotSingletons(ssoHelper, ssoType, oauthHelper, hardware)
    }

    private fun resolveMethods(classes: KaringAotClasses): KaringAotMethods {
        val accessToken =
            bindingStep("resolve OAuthHelper access token") {
                classes.oauth.getMethod("e").apply { isAccessible = true }
            }
        val refresh =
            bindingStep("resolve OAuthHelper refresh") {
                classes.oauth.getMethod("u", String::class.java).apply { isAccessible = true }
            }
        val deviceId =
            bindingStep("resolve Hardware device id") {
                classes.hardware.getMethod("u").apply { isAccessible = true }
            }
        val getTgtIfNeed =
            bindingStep("resolve SsoHelper getTgtIfNeed") {
                classes.ssoHelper.getMethod(
                    "getTgtIfNeed",
                    classes.ssoType,
                    String::class.java,
                    classes.function1,
                )
            }
        return KaringAotMethods(accessToken, refresh, deviceId, getTgtIfNeed)
    }

    private fun <T> bindingStep(
        name: String,
        block: () -> T,
    ): T =
        runCatching(block).getOrElse { error ->
            throw IllegalStateException("$name failed: ${error::class.java.simpleName}: ${error.message}", error)
        }

    private fun singleton(
        type: Class<*>,
        fieldName: String,
        errorMessage: String,
    ): Any =
        checkNotNull(
            type
                .getDeclaredField(fieldName)
                .apply { isAccessible = true }
                .get(null),
        ) {
            errorMessage
        }
}
