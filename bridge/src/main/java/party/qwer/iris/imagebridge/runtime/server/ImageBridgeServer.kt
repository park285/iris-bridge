package party.qwer.iris.imagebridge.runtime.server

import android.content.Context
import android.util.Log
import party.qwer.iris.imagebridge.runtime.BridgeHookInstaller
import party.qwer.iris.imagebridge.runtime.NoopBridgeHookInstaller
import party.qwer.iris.imagebridge.runtime.kakao.KakaoClassRegistry
import party.qwer.iris.imagebridge.runtime.reply.ReplyLeveragePendingContextStore
import party.qwer.iris.imagebridge.runtime.reply.ReplyMentionPendingContextStore
import party.qwer.iris.imagebridge.runtime.send.KakaoTextSendCapability
import java.util.concurrent.ExecutorService
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal object ImageBridgeServer {
    private const val TAG = "IrisBridge"
    private const val INITIAL_RESTART_DELAY_MS = 1_000L
    private const val MAX_RESTART_DELAY_MS = 30_000L
    private val running = AtomicBoolean(false)
    private val restartCount = AtomicInteger(0)
    private val lastCrashMessage = AtomicReference<String?>(null)
    private val specStatus = AtomicReference<BridgeSpecStatus?>(null)
    private val registryAvailable = AtomicBoolean(false)
    private val lastRegistryError = AtomicReference<String?>(null)
    private val textSendCapability = AtomicReference<KakaoTextSendCapability?>(null)
    private val textBridgeSendTextEnabled = AtomicBoolean(false)
    private val textBridgeSendMarkdownEnabled = AtomicBoolean(false)
    private val karingAotAvailable = AtomicBoolean(false)
    private val karingAotReason = AtomicReference<String?>(null)
    private val peerIdentityValidator = BridgePeerIdentityValidator()
    private val bridgeMetrics = BridgeMetrics()
    private val muxClientDispatcher = newBridgeMuxClientDispatcher({ clientExecutor }, { requestHandler }, { running.get() }, peerIdentityValidator, bridgeMetrics)

    @Volatile
    private var requestHandler: ImageBridgeRequestHandler? = null

    @Volatile
    private var clientExecutor: ExecutorService? = null

    fun start(
        context: Context,
        registry: KakaoClassRegistry?,
        registryError: String? = null,
        mentionPendingContexts: ReplyMentionPendingContextStore? = null,
        leveragePendingContexts: ReplyLeveragePendingContextStore? = null,
        leverageCommitPendingContexts: ReplyLeveragePendingContextStore? = null,
        hookInstaller: BridgeHookInstaller = NoopBridgeHookInstaller,
    ) {
        if (!running.compareAndSet(false, true)) {
            Log.w(TAG, "bridge server already running")
            return
        }
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
                ::healthSnapshot,
                bridgeMetrics,
            )
        textSendCapability.set(components.textSendCapability)
        karingAotAvailable.set(components.karingAotAvailable)
        karingAotReason.set(components.karingAotReason)
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

    private fun healthSnapshot(): ImageBridgeHealthSnapshot =
        buildImageBridgeHealthSnapshot(
            running = running.get(),
            specStatus = specStatus.get(),
            registryAvailable = registryAvailable.get(),
            lastRegistryError = lastRegistryError.get(),
            textSendCapability = textSendCapability.get(),
            textBridgeSendTextEnabled = textBridgeSendTextEnabled.get(),
            textBridgeSendMarkdownEnabled = textBridgeSendMarkdownEnabled.get(),
            karingAotAvailable = karingAotAvailable.get(),
            karingAotReason = karingAotReason.get(),
            metrics = bridgeMetrics.snapshot(),
            restartCount = restartCount.get(),
            lastCrashMessage = lastCrashMessage.get(),
        )

    private fun recordServerFailure(message: String) {
        recordBridgeServerFailure(restartCount, lastCrashMessage, message)
    }

    private fun shutdownClientExecutor() {
        clientExecutor?.shutdown()
        clientExecutor = null
    }

    private fun sleepBeforeRestart(delayMs: Long) {
        sleepBeforeBridgeRestart(delayMs, running)
    }

    internal fun nextBridgeRestartDelayMs(failureCount: Int): Long = (INITIAL_RESTART_DELAY_MS shl (failureCount - 1).coerceAtLeast(0).coerceAtMost(5)).coerceAtMost(MAX_RESTART_DELAY_MS)

    internal fun newClientExecutorForTest(): ThreadPoolExecutor = newClientExecutor()

    internal fun isTextBridgeSendTextEnabled(raw: String? = System.getenv("IRIS_TEXT_BRIDGE_SEND_TEXT_ENABLED")): Boolean = textBridgeSendTextEnabled(raw)

    internal fun isTextBridgeSendMarkdownEnabled(raw: String? = System.getenv("IRIS_TEXT_BRIDGE_SEND_MARKDOWN_ENABLED")): Boolean = textBridgeSendMarkdownEnabled(raw)

    private fun newClientExecutor(): ThreadPoolExecutor = newBridgeClientExecutor()
}
