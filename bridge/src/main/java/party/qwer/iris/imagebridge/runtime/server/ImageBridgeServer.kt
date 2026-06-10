package party.qwer.iris.imagebridge.runtime.server

import android.content.Context
import android.util.Log
import party.qwer.iris.imagebridge.runtime.BridgeHookInstaller
import party.qwer.iris.imagebridge.runtime.NoopBridgeHookInstaller
import party.qwer.iris.imagebridge.runtime.core.BridgeCore
import party.qwer.iris.imagebridge.runtime.core.BridgeCoreRuntime
import party.qwer.iris.imagebridge.runtime.core.serverRestartDelayMs
import party.qwer.iris.imagebridge.runtime.discovery.BridgeDiscoverySnapshot
import party.qwer.iris.imagebridge.runtime.discovery.defaultBridgeDiscovery
import party.qwer.iris.imagebridge.runtime.kakao.KakaoClassRegistry
import party.qwer.iris.imagebridge.runtime.reply.ReplyLeveragePendingContextStore
import party.qwer.iris.imagebridge.runtime.reply.ReplyMentionPendingContextStore
import party.qwer.iris.imagebridge.runtime.send.KakaoTextSendCapability
import java.util.concurrent.ExecutorService
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "IrisBridge"

internal class ImageBridgeServer(
    internal val running: AtomicBoolean = AtomicBoolean(false),
    internal val restartCount: AtomicInteger = AtomicInteger(0),
    internal val lastCrashMessage: AtomicReference<String?> = AtomicReference(null),
    internal val specStatus: AtomicReference<BridgeSpecStatus?> = AtomicReference(null),
    internal val registryAvailable: AtomicBoolean = AtomicBoolean(false),
    internal val lastRegistryError: AtomicReference<String?> = AtomicReference(null),
    internal val textSendCapability: AtomicReference<KakaoTextSendCapability?> = AtomicReference(null),
    internal val textBridgeSendTextEnabled: AtomicBoolean = AtomicBoolean(false),
    internal val textBridgeSendMarkdownEnabled: AtomicBoolean = AtomicBoolean(false),
    private val peerIdentityValidator: BridgePeerIdentityValidator = BridgePeerIdentityValidator(),
    internal val bridgeMetrics: BridgeMetrics = BridgeMetrics(),
    internal val bridgeCoreUnavailable: AtomicBoolean = AtomicBoolean(false),
    internal val discoverySnapshotProvider: () -> BridgeDiscoverySnapshot = defaultBridgeDiscovery::snapshot,
) {
    private val sessionAdmission: BridgeSessionAdmission = newBridgeSessionAdmission(bridgeMetrics)
    private val muxClientDispatcher =
        newBridgeMuxClientDispatcher(
            { clientExecutor },
            { requestHandler },
            { running.get() },
            peerIdentityValidator,
            bridgeMetrics,
            sessionAdmission,
        )

    @Volatile
    private var requestHandler: ImageBridgeRequestHandler? = null

    @Volatile
    private var clientExecutor: ExecutorService? = null

    @Volatile
    private var bridgeCore: BridgeCoreRuntime? = null

    fun start(
        context: Context,
        registry: KakaoClassRegistry?,
        registryError: String? = null,
        mentionPendingContexts: ReplyMentionPendingContextStore? = null,
        leveragePendingContexts: ReplyLeveragePendingContextStore? = null,
        leverageCommitPendingContexts: ReplyLeveragePendingContextStore? = null,
        hookInstaller: BridgeHookInstaller = NoopBridgeHookInstaller,
        bridgeCore: BridgeCoreRuntime?,
    ) {
        if (bridgeCore == null) {
            bridgeCoreUnavailable.set(true)
            Log.e(TAG, "bridge-core unavailable — refusing to start mux server (fail-closed)")
            return
        }
        if (!running.compareAndSet(false, true)) {
            Log.w(TAG, "bridge server already running")
            return
        }
        this.bridgeCore = bridgeCore
        bridgeCoreUnavailable.set(false)
        restartCount.set(0)
        lastCrashMessage.set(null)
        registryAvailable.set(registry != null)
        lastRegistryError.set(registryError)
        textBridgeSendTextEnabled.set(isTextBridgeSendTextEnabled())
        textBridgeSendMarkdownEnabled.set(isTextBridgeSendMarkdownEnabled())
        val components =
            buildBridgeRequestHandlerComponents(
                context,
                registry,
                registryError,
                mentionPendingContexts,
                leveragePendingContexts,
                leverageCommitPendingContexts,
                hookInstaller,
                this::healthSnapshot,
                bridgeMetrics,
                bridgeCore,
            )
        textSendCapability.set(components.textSendCapability)
        specStatus.set(components.initialSpecStatus)
        logBridgeSpecFailure(TAG, components.initialSpecStatus)
        requestHandler = components.requestHandler
        clientExecutor = newClientExecutor()
        startMuxServerThread()
    }

    private fun startMuxServerThread() {
        startMuxBridgeServerThread(
            dispatcher = muxClientDispatcher,
            isRunning = { running.get() },
            restartDelayMs = ::nextBridgeRestartDelayMs,
            recordFailure = ::recordServerFailure,
            sleepBeforeRestart = ::sleepBeforeRestart,
        )
    }

    private fun recordServerFailure(message: String) {
        recordBridgeServerFailure(restartCount, lastCrashMessage, message)
    }

    private fun sleepBeforeRestart(delayMs: Long) {
        sleepBeforeBridgeRestart(delayMs, running)
    }

    internal fun nextBridgeRestartDelayMs(failureCount: Int): Long = BridgeCore.serverRestartDelayMs(failureCount)

    internal fun newClientExecutorForTest(): ThreadPoolExecutor = newClientExecutor()

    internal fun recordServerFailureForTest(message: String) {
        recordServerFailure(message)
    }

    internal fun healthSnapshotForTest(): ImageBridgeHealthSnapshot = healthSnapshot()

    internal fun stopForTest() {
        running.set(false)
        bridgeCore?.close()
        bridgeCore = null
    }

    internal fun isTextBridgeSendTextEnabled(raw: String? = System.getenv("IRIS_TEXT_BRIDGE_SEND_TEXT_ENABLED")): Boolean = textBridgeSendTextEnabled(raw)

    internal fun isTextBridgeSendMarkdownEnabled(raw: String? = System.getenv("IRIS_TEXT_BRIDGE_SEND_MARKDOWN_ENABLED")): Boolean = textBridgeSendMarkdownEnabled(raw)

    private fun newClientExecutor(): ThreadPoolExecutor = newBridgeClientExecutor()
}

internal val defaultImageBridgeServer = ImageBridgeServer()
