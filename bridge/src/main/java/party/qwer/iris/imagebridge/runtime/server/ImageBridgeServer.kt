package party.qwer.iris.imagebridge.runtime.server

import android.content.Context
import android.util.Log
import party.qwer.iris.imagebridge.runtime.discovery.BridgeDiscovery
import party.qwer.iris.imagebridge.runtime.discovery.currentBridgeCapabilities
import party.qwer.iris.imagebridge.runtime.kakao.KakaoClassRegistry
import party.qwer.iris.imagebridge.runtime.room.ChatRoomIntentMetadataResolver
import party.qwer.iris.imagebridge.runtime.room.ChatRoomIntrospector
import party.qwer.iris.imagebridge.runtime.room.ChatRoomMemberExtractor
import party.qwer.iris.imagebridge.runtime.room.ChatRoomOpener
import party.qwer.iris.imagebridge.runtime.room.ChatRoomResolver
import party.qwer.iris.imagebridge.runtime.send.KakaoImageSender
import party.qwer.iris.imagebridge.runtime.send.KakaoTextSendCapability
import party.qwer.iris.imagebridge.runtime.send.KakaoTextSender
import party.qwer.iris.resolveBridgeMuxServerEnabled
import party.qwer.iris.resolveBridgeTextSendMarkdownEnabled
import party.qwer.iris.resolveBridgeTextSendTextEnabled
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal object ImageBridgeServer {
    private const val TAG = "IrisBridge"
    private const val INITIAL_RESTART_DELAY_MS = 1_000L
    private const val MAX_RESTART_DELAY_MS = 30_000L
    private const val CLIENT_EXECUTOR_CORE_THREADS = 2
    private const val CLIENT_EXECUTOR_MAX_THREADS = 8
    private const val CLIENT_EXECUTOR_QUEUE_CAPACITY = 64
    private const val CLIENT_EXECUTOR_KEEP_ALIVE_MS = 60_000L

    private val running = AtomicBoolean(false)
    private val restartCount = AtomicInteger(0)
    private val lastCrashMessage = AtomicReference<String?>(null)
    private val specStatus = AtomicReference<BridgeSpecStatus?>(null)
    private val registryAvailable = AtomicBoolean(false)
    private val lastRegistryError = AtomicReference<String?>(null)
    private val textSendCapability = AtomicReference<KakaoTextSendCapability?>(null)
    private val textBridgeSendTextEnabled = AtomicBoolean(false)
    private val textBridgeSendMarkdownEnabled = AtomicBoolean(false)
    private val peerIdentityValidator = BridgePeerIdentityValidator()
    private val bridgeMetrics = BridgeMetrics()
    private val clientDispatcher =
        BridgeClientDispatcher(
            executorProvider = { clientExecutor },
            handlerProvider = { requestHandler },
            isRunning = { running.get() },
            peerIdentityValidator = peerIdentityValidator,
            metrics = bridgeMetrics,
        )
    private val muxClientDispatcher =
        BridgeMuxClientDispatcher(
            executorProvider = { clientExecutor },
            handlerProvider = { requestHandler },
            isRunning = { running.get() },
            peerIdentityValidator = peerIdentityValidator,
            metrics = bridgeMetrics,
        )

    @Volatile
    private var requestHandler: ImageBridgeRequestHandler? = null

    @Volatile
    private var clientExecutor: ExecutorService? = null

    fun start(
        context: Context,
        registry: KakaoClassRegistry?,
        registryError: String? = null,
    ) {
        if (!running.compareAndSet(false, true)) {
            Log.w(TAG, "bridge server already running")
            return
        }
        restartCount.set(0)
        lastCrashMessage.set(null)
        registryAvailable.set(registry != null)
        lastRegistryError.set(registryError)
        val imageSender = registry?.let { KakaoImageSender(it) }
        val textSender = registry?.let { KakaoTextSender(it) }
        textSendCapability.set(textSender?.capability())
        textBridgeSendTextEnabled.set(isTextBridgeSendTextEnabled())
        textBridgeSendMarkdownEnabled.set(isTextBridgeSendMarkdownEnabled())
        val chatRoomResolver = registry?.let { ChatRoomResolver(it) }
        val chatRoomIntentMetadataResolver =
            ChatRoomIntentMetadataResolver { roomId ->
                chatRoomResolver?.resolve(roomId)
            }
        val chatRoomOpener =
            ChatRoomOpener(
                context = context,
                chatRoomTypeResolver = chatRoomIntentMetadataResolver::resolveChatRoomType,
            )
        val chatRoomMemberExtractor = ChatRoomMemberExtractor()
        val verifier =
            BridgeHookSpecVerifier(
                registry = registry,
                registryError = registryError,
            )
        val initialSpecStatus = verifier.verify()
        specStatus.set(initialSpecStatus)
        if (!initialSpecStatus.ready) {
            Log.e(TAG, "bridge hook spec verification failed: ${initialSpecStatus.checks.filterNot { it.ok }.joinToString { it.name }}")
        }
        requestHandler =
            ImageBridgeRequestHandler(
                imageSender = { request ->
                    val sender =
                        imageSender
                            ?: error("KakaoClassRegistry not available: ${registryError ?: "unknown error"}")
                    sender.send(request)
                },
                textSender = { request ->
                    val sender =
                        textSender
                            ?: error("Kakao text sender not available: ${registryError ?: "unknown error"}")
                    sender.send(request)
                },
                healthProvider = ::healthSnapshot,
                chatRoomInspector = { roomId ->
                    val resolver = chatRoomResolver ?: error("chatroom resolver unavailable: ${registryError ?: "unknown error"}")
                    val room = resolver.resolve(roomId) ?: error("chatroom not found: $roomId")
                    ChatRoomIntrospector.scanJson(room, maxDepth = 2)
                },
                chatRoomOpener = chatRoomOpener::open,
                chatRoomMemberSnapshotProvider = { roomId, expectedMemberHints, preferredPlan ->
                    val resolver = chatRoomResolver ?: error("chatroom resolver unavailable: ${registryError ?: "unknown error"}")
                    val room = resolver.resolve(roomId) ?: error("chatroom not found: $roomId")
                    chatRoomMemberExtractor.snapshot(roomId, room, expectedMemberHints, preferredPlan)
                },
                metrics = bridgeMetrics,
            )
        clientExecutor = newClientExecutor()
        Thread(
            {
                BridgeOneShotServerLoop(
                    dispatcher = clientDispatcher,
                    isRunning = { running.get() },
                    restartDelayMs = ::nextBridgeRestartDelayMs,
                    recordFailure = ::recordServerFailure,
                    sleepBeforeRestart = ::sleepBeforeRestart,
                    shutdownExecutor = ::shutdownClientExecutor,
                ).run()
            },
            "iris-bridge-server",
        ).apply {
            isDaemon = true
            start()
        }
        if (isMuxServerEnabled()) {
            Thread(
                {
                    BridgeMuxServerLoop(
                        dispatcher = muxClientDispatcher,
                        isRunning = { running.get() },
                        restartDelayMs = ::nextBridgeRestartDelayMs,
                        recordFailure = ::recordServerFailure,
                        sleepBeforeRestart = ::sleepBeforeRestart,
                    ).run()
                },
                "iris-bridge-mux-server",
            ).apply {
                isDaemon = true
                start()
            }
        }
    }

    private fun healthSnapshot(): ImageBridgeHealthSnapshot =
        ImageBridgeHealthSnapshot(
            running = running.get(),
            specStatus = specStatus.get() ?: BridgeSpecStatus(ready = false, checkedAtEpochMs = 0L, checks = emptyList()),
            discoverySnapshot = BridgeDiscovery.snapshot(),
            capabilities =
                currentBridgeCapabilities(
                    registryAvailable.get(),
                    lastRegistryError.get(),
                    specStatus.get()?.ready == true,
                    textSendCapability.get(),
                    textBridgeSendTextEnabled.get(),
                    textBridgeSendMarkdownEnabled.get(),
                ),
            metrics = bridgeMetrics.snapshot(),
            restartCount = restartCount.get(),
            lastCrashMessage = lastCrashMessage.get(),
        )

    private fun recordServerFailure(message: String) {
        restartCount.incrementAndGet()
        lastCrashMessage.set(message)
    }

    private fun shutdownClientExecutor() {
        clientExecutor?.shutdown()
        clientExecutor = null
    }

    private fun sleepBeforeRestart(delayMs: Long) {
        runCatching {
            Thread.sleep(delayMs)
        }.onFailure { error ->
            if (error is InterruptedException) {
                Thread.currentThread().interrupt()
                running.set(false)
            }
        }
    }

    internal fun nextBridgeRestartDelayMs(failureCount: Int): Long = (INITIAL_RESTART_DELAY_MS shl (failureCount - 1).coerceAtLeast(0).coerceAtMost(5)).coerceAtMost(MAX_RESTART_DELAY_MS)

    internal fun newClientExecutorForTest(): ThreadPoolExecutor = newClientExecutor()

    internal fun isTextBridgeSendTextEnabled(raw: String? = System.getenv("IRIS_TEXT_BRIDGE_SEND_TEXT_ENABLED")): Boolean = raw?.let(::isTruthy) ?: resolveBridgeTextSendTextEnabled()

    internal fun isTextBridgeSendMarkdownEnabled(raw: String? = System.getenv("IRIS_TEXT_BRIDGE_SEND_MARKDOWN_ENABLED")): Boolean = raw?.let(::isTruthy) ?: resolveBridgeTextSendMarkdownEnabled()

    internal fun isMuxServerEnabled(raw: String? = System.getenv("IRIS_BRIDGE_MUX_SERVER_ENABLED")): Boolean = raw?.let(::isTruthy) ?: resolveBridgeMuxServerEnabled()

    private fun isTruthy(raw: String): Boolean =
        when (raw.trim().lowercase()) {
            "true", "1", "on", "yes" -> true
            else -> false
        }

    private fun newClientExecutor(): ThreadPoolExecutor =
        ThreadPoolExecutor(
            CLIENT_EXECUTOR_CORE_THREADS,
            CLIENT_EXECUTOR_MAX_THREADS,
            CLIENT_EXECUTOR_KEEP_ALIVE_MS,
            TimeUnit.MILLISECONDS,
            LinkedBlockingQueue(CLIENT_EXECUTOR_QUEUE_CAPACITY),
            { runnable ->
                Thread(runnable, "iris-bridge-client").apply {
                    isDaemon = true
                }
            },
            ThreadPoolExecutor.AbortPolicy(),
        ).apply {
            allowCoreThreadTimeOut(true)
        }
}
