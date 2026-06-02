@file:Suppress("ClassName", "FunctionName")

package party.qwer.iris.imagebridge.runtime

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import party.qwer.iris.imagebridge.runtime.discovery.BridgeDiscovery
import party.qwer.iris.imagebridge.runtime.discovery.HOOK_SEND_MULTIPLE
import party.qwer.iris.imagebridge.runtime.discovery.HOOK_SEND_SINGLE
import java.lang.reflect.Method
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class BridgeDiscoveryHookFailureTest {
    @Test
    fun `install records failed hook and keeps installing later hooks`() {
        val discovery = BridgeDiscovery()
        val registry = buildFakeRegistry()
        val singleSend = assertNotNull(registry.singleSendMethod)
        val installer = ThrowingBridgeHookInstaller(singleSend)

        discovery.install(registry, installer)

        val hooks = discovery.snapshot().hooks.associateBy { it.name }
        val failedHook = assertNotNull(hooks[HOOK_SEND_SINGLE])
        val laterHook = assertNotNull(hooks[HOOK_SEND_MULTIPLE])
        assertFalse(failedHook.installed)
        assertTrue(failedHook.installError?.contains("boom:${singleSend.name}") == true)
        assertTrue(laterHook.installed)
        assertEquals(null, laterHook.installError)
    }

    @Test
    fun `install attempts all hooks even when first hook throws`() {
        val discovery = BridgeDiscovery()
        val registry = buildFakeRegistry()
        val singleSend = assertNotNull(registry.singleSendMethod)
        val installer = ThrowingBridgeHookInstaller(registry.roomDaoMethod)

        discovery.install(registry, installer)

        val hooks = discovery.snapshot().hooks.associateBy { it.name }
        assertFalse(hooks["MasterDatabase#roomDao"]!!.installed)
        assertTrue(hooks["ChatRoomManager#directResolve"]!!.installed)
        assertTrue(hooks["ChatRoomManager#broadResolve"]!!.installed)
        assertTrue(hooks[HOOK_SEND_SINGLE]!!.installed)
        assertTrue(hooks[HOOK_SEND_MULTIPLE]!!.installed)
        assertEquals(
            listOf(
                registry.roomDaoMethod,
                registry.directRoomResolverMethod,
                registry.broadRoomResolverMethod,
                singleSend,
                registry.multiSendMethod,
            ),
            installer.attemptedMethods,
        )
    }

    @Test
    fun `install reports optional single send hook as unavailable without claiming installed`() {
        val discovery = BridgeDiscovery()
        val registry = buildMultiOnlyRegistry()
        val installer = RecordingBridgeHookInstaller()

        discovery.install(registry, installer)

        val hooks = discovery.snapshot().hooks.associateBy { it.name }
        val singleHook = assertNotNull(hooks[HOOK_SEND_SINGLE])
        val multiHook = assertNotNull(hooks[HOOK_SEND_MULTIPLE])
        assertFalse(singleHook.installed)
        assertTrue(singleHook.installError?.contains("optional path unavailable") == true)
        assertTrue(multiHook.installed)
        assertFalse(installer.attemptedMethods.any { it.name == "n" })
        assertTrue(installer.attemptedMethods.any { it.name == "p" })
    }

    @Test
    fun `install records all hooks as failed when all throw`() {
        val discovery = BridgeDiscovery()
        val registry = buildFakeRegistry()
        val installer = AllFailingBridgeHookInstaller()

        discovery.install(registry, installer)

        val snapshot = discovery.snapshot()
        assertTrue(snapshot.installAttempted)
        val hooks = snapshot.hooks.associateBy { it.name }
        val discoveryHookNames =
            listOf(
                "MasterDatabase#roomDao",
                "ChatRoomManager#directResolve",
                "ChatRoomManager#broadResolve",
                HOOK_SEND_SINGLE,
                HOOK_SEND_MULTIPLE,
            )
        discoveryHookNames.forEach { name ->
            assertFalse(hooks[name]!!.installed, "$name should not be installed")
            assertNotNull(hooks[name]!!.installError, "$name should have an error")
        }
    }
}

class ReplyMarkdownSelectorPartialMissTest {
    @Test
    fun `selector falls back to method named u when writeType is null`() {
        val selected =
            selectReplyMarkdownRequestHookMethodsForTest(
                companionClass = SingleUMethodCompanion::class.java,
                chatRoomClass = PartialMissChatRoom::class.java,
                writeTypeClass = null,
                listenerClass = PartialMissListener::class.java,
            )

        assertEquals(listOf("u"), selected.map { it.name })
    }

    @Test
    fun `selector falls back to method named u when chatRoom is null`() {
        val selected =
            selectReplyMarkdownRequestHookMethodsForTest(
                companionClass = SingleUMethodCompanion::class.java,
                chatRoomClass = null,
                writeTypeClass = PartialMissWriteType::class.java,
                listenerClass = PartialMissListener::class.java,
            )

        assertEquals(listOf("u"), selected.map { it.name })
    }

    @Test
    fun `selector falls back to method named u when listener is null`() {
        val selected =
            selectReplyMarkdownRequestHookMethodsForTest(
                companionClass = SingleUMethodCompanion::class.java,
                chatRoomClass = PartialMissChatRoom::class.java,
                writeTypeClass = PartialMissWriteType::class.java,
                listenerClass = null,
            )

        assertEquals(listOf("u"), selected.map { it.name })
    }

    @Test
    fun `selector returns empty when no method named u exists with partial miss`() {
        val selected =
            selectReplyMarkdownRequestHookMethodsForTest(
                companionClass = NoUMethodCompanion::class.java,
                chatRoomClass = null,
                writeTypeClass = null,
                listenerClass = null,
            )

        assertTrue(selected.isEmpty())
    }

    @Test
    fun `selector matches by type when all registry classes are present`() {
        val selected =
            selectReplyMarkdownRequestHookMethodsForTest(
                companionClass = TypedRequestCompanion::class.java,
                chatRoomClass = PartialMissChatRoom::class.java,
                writeTypeClass = PartialMissWriteType::class.java,
                listenerClass = PartialMissListener::class.java,
            )

        assertEquals(2, selected.size)
        assertEquals("u", selected[0].name)
        assertEquals("v", selected[1].name)
    }

    @Test
    fun `selector excludes methods whose types do not match when all classes present`() {
        val selected =
            selectReplyMarkdownRequestHookMethodsForTest(
                companionClass = TypedRequestCompanion::class.java,
                chatRoomClass = PartialMissChatRoom::class.java,
                writeTypeClass = PartialMissWriteType::class.java,
                listenerClass = PartialMissListener::class.java,
            )

        assertTrue(selected.none { it.parameterTypes[0] == UnrelatedChatRoom::class.java })
    }
}

class ProtectiveExceptionModeContractTest {
    @Test
    fun `ModernBridgeHookInstaller sets PROTECTIVE exception mode on hookBefore`() {
        val framework = RecordingXposedInterface()
        val module = TestXposedModule(framework)
        val installer = createModernInstaller(module)

        installer.hookBefore(SampleHookTarget::class.java.getDeclaredMethod("target")) {}

        assertEquals(
            io.github.libxposed.api.XposedInterface.ExceptionMode.PROTECTIVE,
            framework.lastHookBuilder?.exceptionMode,
        )
    }

    @Test
    fun `ModernBridgeHookInstaller sets PROTECTIVE exception mode on hookAfter`() {
        val framework = RecordingXposedInterface()
        val module = TestXposedModule(framework)
        val installer = createModernInstaller(module)

        installer.hookAfter(SampleHookTarget::class.java.getDeclaredMethod("target")) {}

        assertEquals(
            io.github.libxposed.api.XposedInterface.ExceptionMode.PROTECTIVE,
            framework.lastHookBuilder?.exceptionMode,
        )
    }

    @Test
    fun `hookBefore callback failure is swallowed under PROTECTIVE mode`() {
        val framework = RecordingXposedInterface()
        val module = TestXposedModule(framework)
        val installer = createModernInstaller(module)

        installer.hookBefore(SampleHookTarget::class.java.getDeclaredMethod("target")) {
            throw IllegalStateException("callback failed")
        }

        val builder = framework.lastHookBuilder!!
        val result = builder.dispatchProtective()
        assertEquals(null, result)
    }

    @Test
    fun `hookAfter callback failure is swallowed under PROTECTIVE mode`() {
        val framework = RecordingXposedInterface()
        val module = TestXposedModule(framework)
        val installer = createModernInstaller(module)

        installer.hookAfter(SampleHookTarget::class.java.getDeclaredMethod("target")) {
            throw IllegalStateException("callback failed")
        }

        val builder = framework.lastHookBuilder!!
        val result = builder.dispatchProtective()
        assertEquals(null, result)
    }

    private fun createModernInstaller(module: io.github.libxposed.api.XposedModule): BridgeHookInstaller {
        val clazz = Class.forName("party.qwer.iris.imagebridge.runtime.ModernBridgeHookInstaller")
        val ctor =
            clazz
                .getDeclaredConstructor(io.github.libxposed.api.XposedModule::class.java)
                .apply { isAccessible = true }
        return ctor.newInstance(module) as BridgeHookInstaller
    }
}

private class ThrowingBridgeHookInstaller(
    private val methodToFail: Method,
) : BridgeHookInstaller {
    val attemptedMethods = mutableListOf<Method>()

    override fun hookBefore(
        method: Method,
        callback: (BridgeHookInvocation) -> Unit,
    ) {
        attemptedMethods += method
        if (method == methodToFail) error("boom:${method.name}")
    }

    override fun hookAfter(
        method: Method,
        callback: (BridgeHookInvocation) -> Unit,
    ) {
        attemptedMethods += method
        if (method == methodToFail) error("boom:${method.name}")
    }
}

private class AllFailingBridgeHookInstaller : BridgeHookInstaller {
    override fun hookBefore(
        method: Method,
        callback: (BridgeHookInvocation) -> Unit,
    ) {
        error("always fails: ${method.name}")
    }

    override fun hookAfter(
        method: Method,
        callback: (BridgeHookInvocation) -> Unit,
    ) {
        error("always fails: ${method.name}")
    }
}

private class RecordingBridgeHookInstaller : BridgeHookInstaller {
    val attemptedMethods = mutableListOf<Method>()

    override fun hookBefore(
        method: Method,
        callback: (BridgeHookInvocation) -> Unit,
    ) {
        attemptedMethods += method
    }

    override fun hookAfter(
        method: Method,
        callback: (BridgeHookInvocation) -> Unit,
    ) {
        attemptedMethods += method
    }
}

private class TestXposedModule(
    framework: io.github.libxposed.api.XposedInterface,
) : io.github.libxposed.api.XposedModule() {
    init {
        attachFramework(framework)
    }
}

private class RecordingXposedInterface : io.github.libxposed.api.XposedInterface {
    var lastHookBuilder: RecordingHookBuilder? = null

    override fun hook(executable: java.lang.reflect.Executable): io.github.libxposed.api.XposedInterface.HookBuilder {
        val builder = RecordingHookBuilder(executable)
        lastHookBuilder = builder
        return builder
    }

    override fun hookClassInitializer(clazz: Class<*>): io.github.libxposed.api.XposedInterface.HookBuilder = unsupported()

    override fun deoptimize(executable: java.lang.reflect.Executable): Boolean = unsupported()

    override fun getInvoker(method: java.lang.reflect.Method): io.github.libxposed.api.XposedInterface.Invoker<*, java.lang.reflect.Method> = unsupported()

    override fun <T> getInvoker(constructor: java.lang.reflect.Constructor<T>): io.github.libxposed.api.XposedInterface.CtorInvoker<T> = unsupported()

    override fun log(
        priority: Int,
        tag: String?,
        msg: String,
    ) = Unit

    override fun log(
        priority: Int,
        tag: String?,
        msg: String,
        throwable: Throwable?,
    ) = Unit

    override fun getFrameworkName(): String = "test"

    override fun getFrameworkVersion(): String = "test"

    override fun getFrameworkVersionCode(): Long = 1L

    override fun getFrameworkProperties(): Long = 0L

    override fun getModuleApplicationInfo(): android.content.pm.ApplicationInfo = unsupported()

    override fun getRemotePreferences(group: String): android.content.SharedPreferences = unsupported()

    override fun listRemoteFiles(): Array<String> = unsupported()

    @Throws(java.io.FileNotFoundException::class)
    override fun openRemoteFile(name: String): android.os.ParcelFileDescriptor = unsupported()
}

internal class RecordingHookBuilder(
    private val executable: java.lang.reflect.Executable,
) : io.github.libxposed.api.XposedInterface.HookBuilder {
    var exceptionMode: io.github.libxposed.api.XposedInterface.ExceptionMode? = null
    private var hooker: io.github.libxposed.api.XposedInterface.Hooker? = null

    override fun setPriority(priority: Int): io.github.libxposed.api.XposedInterface.HookBuilder = this

    override fun setExceptionMode(mode: io.github.libxposed.api.XposedInterface.ExceptionMode): io.github.libxposed.api.XposedInterface.HookBuilder {
        this.exceptionMode = mode
        return this
    }

    override fun intercept(hooker: io.github.libxposed.api.XposedInterface.Hooker): io.github.libxposed.api.XposedInterface.HookHandle {
        this.hooker = hooker
        return object : io.github.libxposed.api.XposedInterface.HookHandle {
            override fun getExecutable(): java.lang.reflect.Executable = executable

            override fun unhook() = Unit
        }
    }

    fun dispatchProtective(): Any? {
        val chain = StubChain(executable)
        return try {
            hooker?.intercept(chain)
        } catch (error: Throwable) {
            if (exceptionMode == io.github.libxposed.api.XposedInterface.ExceptionMode.PROTECTIVE) {
                null
            } else {
                throw error
            }
        }
    }
}

@Suppress("WRONG_NULLABILITY_FOR_JAVA_OVERRIDE")
private class StubChain(
    private val executable: java.lang.reflect.Executable,
) : io.github.libxposed.api.XposedInterface.Chain {
    override fun getExecutable(): java.lang.reflect.Executable = executable

    override fun getThisObject(): Any? = null

    override fun getArgs(): MutableList<Any> = mutableListOf()

    override fun getArg(index: Int): Any? = null

    @Throws(Throwable::class)
    override fun proceed(): Any? = null

    @Throws(Throwable::class)
    override fun proceed(args: Array<out Any>): Any? = null

    @Throws(Throwable::class)
    override fun proceedWith(result: Any): Any = result

    @Throws(Throwable::class)
    override fun proceedWith(
        result: Any,
        args: Array<out Any>,
    ): Any = result
}

private class SampleHookTarget {
    fun target() = Unit
}

private class PartialMissChatRoom

private class PartialMissWriteType

private interface PartialMissListener

private class UnrelatedChatRoom

private class SingleUMethodCompanion {
    fun u(
        chatRoom: PartialMissChatRoom,
        sendingLog: Any,
        writeType: PartialMissWriteType,
        listener: PartialMissListener,
        shouldRetry: Boolean,
    ) = Unit

    fun v(
        chatRoom: PartialMissChatRoom,
        sendingLog: Any,
        writeType: PartialMissWriteType,
        listener: PartialMissListener,
        shouldRetry: Boolean,
    ) = Unit
}

private class TypedRequestCompanion {
    fun u(
        chatRoom: PartialMissChatRoom,
        sendingLog: Any,
        writeType: PartialMissWriteType,
        listener: PartialMissListener,
        shouldRetry: Boolean,
    ) = Unit

    fun v(
        chatRoom: PartialMissChatRoom,
        sendingLog: Any,
        writeType: PartialMissWriteType,
        listener: PartialMissListener,
        shouldRetry: Boolean,
    ) = Unit

    fun w(
        chatRoom: UnrelatedChatRoom,
        sendingLog: Any,
        writeType: PartialMissWriteType,
        listener: PartialMissListener,
        shouldRetry: Boolean,
    ) = Unit
}

private class NoUMethodCompanion {
    fun x(
        a: Any,
        b: Any,
        c: Any,
        d: Any,
        e: Boolean,
    ) = Unit
}

private fun <T> unsupported(): T = throw UnsupportedOperationException("not used in this test")
