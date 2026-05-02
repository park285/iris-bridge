package party.qwer.iris.imagebridge.runtime.server

import android.content.Context
import android.net.LocalServerSocket
import android.util.Log
import party.qwer.iris.IrisRuntimePathPolicy
import party.qwer.iris.imagebridge.runtime.discovery.BridgeDiscovery
import party.qwer.iris.imagebridge.runtime.discovery.currentBridgeCapabilities
import party.qwer.iris.imagebridge.runtime.kakao.KakaoClassRegistry
import party.qwer.iris.imagebridge.runtime.room.ChatRoomIntentMetadataResolver
import party.qwer.iris.imagebridge.runtime.room.ChatRoomIntrospector
import party.qwer.iris.imagebridge.runtime.room.ChatRoomMemberExtractor
import party.qwer.iris.imagebridge.runtime.room.ChatRoomOpener
import party.qwer.iris.imagebridge.runtime.room.ChatRoomResolver
import party.qwer.iris.imagebridge.runtime.send.KakaoImageSender
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
        Thread({ runServerLoop() }, "iris-bridge-server").apply {
            isDaemon = true
            start()
        }
    }

    private fun runServerLoop() {
        var consecutiveFailures = 0
        try {
            while (running.get()) {
                try {
                    serve()
                    if (running.get()) {
                        val delayMs = nextBridgeRestartDelayMs(++consecutiveFailures)
                        restartCount.incrementAndGet()
                        lastCrashMessage.set("server loop exited unexpectedly")
                        Log.e(TAG, "bridge server stopped unexpectedly; restarting in ${delayMs}ms")
                        sleepBeforeRestart(delayMs)
                    }
                } catch (e: Exception) {
                    if (!running.get()) {
                        break
                    }
                    consecutiveFailures += 1
                    restartCount.incrementAndGet()
                    lastCrashMessage.set(e.message ?: e.javaClass.name)
                    val delayMs = nextBridgeRestartDelayMs(consecutiveFailures)
                    Log.e(TAG, "bridge server crashed; restarting in ${delayMs}ms", e)
                    sleepBeforeRestart(delayMs)
                }
            }
        } finally {
            clientExecutor?.shutdown()
            clientExecutor = null
        }
    }

    private fun serve() {
        val socketName = IrisRuntimePathPolicy.resolve().imageBridgeSocketName
        val serverSocket = LocalServerSocket(socketName)
        try {
            Log.i(TAG, "bridge server listening on @$socketName")
            while (running.get()) {
                val client = serverSocket.accept()
                clientDispatcher.dispatch(client)
            }
        } finally {
            runCatching { serverSocket.close() }
            Log.i(TAG, "bridge server socket closed")
        }
    }

    private fun healthSnapshot(): ImageBridgeHealthSnapshot =
        ImageBridgeHealthSnapshot(
            running = running.get(),
            specStatus = specStatus.get() ?: BridgeSpecStatus(ready = false, checkedAtEpochMs = 0L, checks = emptyList()),
            discoverySnapshot = BridgeDiscovery.snapshot(),
            capabilities = currentBridgeCapabilities(registryAvailable.get(), lastRegistryError.get(), specStatus.get()?.ready == true),
            metrics = bridgeMetrics.snapshot(),
            restartCount = restartCount.get(),
            lastCrashMessage = lastCrashMessage.get(),
        )

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
