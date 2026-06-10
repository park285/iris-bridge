@file:Suppress("ClassName", "FunctionName", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime

import party.qwer.iris.imagebridge.runtime.discovery.BridgeDiscoverySnapshot
import party.qwer.iris.imagebridge.runtime.discovery.DiscoveryHookStatus
import party.qwer.iris.imagebridge.runtime.discovery.HOOK_SEND_MULTIPLE
import party.qwer.iris.imagebridge.runtime.discovery.HOOK_SEND_SINGLE
import party.qwer.iris.imagebridge.runtime.discovery.HOOK_SEND_THREADED_ENTRY
import party.qwer.iris.imagebridge.runtime.discovery.HOOK_SEND_THREADED_INJECT
import party.qwer.iris.imagebridge.runtime.discovery.sendBlockReason
import party.qwer.iris.imagebridge.runtime.server.BridgeImagePathValidator
import party.qwer.iris.imagebridge.runtime.server.BridgeSpecStatus
import party.qwer.iris.imagebridge.runtime.server.ImageBridgeHealthSnapshot
import party.qwer.iris.imagebridge.runtime.server.ImageBridgeRequestHandler
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ImageBridgeRequestHandlerDiscoveryTest {
    @Test
    fun `discovery send block reason fails closed when native policy is unavailable`() {
        val snapshot =
            BridgeDiscoverySnapshot(
                installAttempted = true,
                hooks = listOf(DiscoveryHookStatus(name = HOOK_SEND_MULTIPLE, installed = true, invocationCount = 0)),
            )

        val reason =
            snapshot.sendBlockReason(
                imageCount = 1,
                threadId = null,
                threadScope = null,
                nativeSendBlockReason = { _, _, _, _, _, _ -> null },
            )

        assertEquals("bridge core unavailable to evaluate bridge discovery hooks", reason)
    }

    @Test
    fun `discovery send block reason passes hook snapshot arrays to native policy`() {
        val snapshot =
            BridgeDiscoverySnapshot(
                installAttempted = true,
                hooks =
                    listOf(
                        DiscoveryHookStatus(name = HOOK_SEND_MULTIPLE, installed = true, invocationCount = 3),
                        DiscoveryHookStatus(name = HOOK_SEND_THREADED_INJECT, installed = false, invocationCount = 0),
                    ),
            )

        val reason =
            snapshot.sendBlockReason(
                imageCount = 2,
                threadId = 55L,
                threadScope = 2,
                nativeSendBlockReason = {
                    installAttempted,
                    hookNames,
                    hookInstalled,
                    imageCount,
                    threadId,
                    threadScope,
                    ->
                    assertEquals(true, installAttempted)
                    assertEquals(listOf(HOOK_SEND_MULTIPLE, HOOK_SEND_THREADED_INJECT), hookNames.toList())
                    assertEquals(listOf(true, false), hookInstalled.toList())
                    assertEquals(2, imageCount)
                    assertEquals(55L, threadId)
                    assertEquals(2, threadScope)
                    ""
                },
            )

        assertNull(reason)
    }

    @Test
    fun `single image request only requires multi send discovery hook`() {
        var sendCalls = 0
        val file = Files.createTempFile("iris-bridge", ".png").toFile().apply { writeText("x") }
        val rootDir = file.parentFile ?: error("temp file parent missing")
        val handler =
            ImageBridgeRequestHandler(
                imageSender = { sendCalls += 1 },
                healthProvider = {
                    ImageBridgeHealthSnapshot(
                        running = true,
                        specStatus = BridgeSpecStatus(ready = true, checkedAtEpochMs = 1L, checks = emptyList()),
                        discoverySnapshot =
                            BridgeDiscoverySnapshot(
                                installAttempted = true,
                                hooks =
                                    listOf(
                                        DiscoveryHookStatus(name = HOOK_SEND_SINGLE, installed = false, invocationCount = 0),
                                        DiscoveryHookStatus(name = HOOK_SEND_MULTIPLE, installed = true, invocationCount = 0),
                                    ),
                            ),
                        restartCount = 0,
                        lastCrashMessage = null,
                    )
                },
                handshakeValidator = developmentHandshakeValidator(),
                pathValidator = BridgeImagePathValidator(rootDir.absolutePath),
                leaseVerifier = testImageLeaseVerifier(),
                logError = { _, _, _ -> },
            )

        val response =
            handler.handle(
                sendImageRequest(
                    roomId = 1L,
                    imagePaths = listOf(file.absolutePath),
                    imageLeases =
                        listOf(
                            signedTestImageLease(
                                requestId = "image-request",
                                roomId = 1L,
                                canonicalPath = file.canonicalPath,
                            ),
                        ),
                ),
            )

        assertEquals("sent", response.status)
        assertEquals(1, sendCalls)
        file.delete()
    }

    @Test
    fun `send image request fails closed when required discovery hook is not installed`() {
        val file = Files.createTempFile("iris-bridge", ".png").toFile().apply { writeText("x") }
        val rootDir = file.parentFile ?: error("temp file parent missing")
        val handler =
            ImageBridgeRequestHandler(
                imageSender = { error("should not be called") },
                healthProvider = {
                    ImageBridgeHealthSnapshot(
                        running = true,
                        specStatus = BridgeSpecStatus(ready = true, checkedAtEpochMs = 1L, checks = emptyList()),
                        discoverySnapshot =
                            BridgeDiscoverySnapshot(
                                installAttempted = true,
                                hooks = listOf(DiscoveryHookStatus(name = HOOK_SEND_MULTIPLE, installed = false, invocationCount = 0)),
                            ),
                        restartCount = 0,
                        lastCrashMessage = null,
                    )
                },
                handshakeValidator = developmentHandshakeValidator(),
                pathValidator = BridgeImagePathValidator(rootDir.absolutePath),
                leaseVerifier = testImageLeaseVerifier(),
                logError = { _, _, _ -> },
            )

        val response =
            handler.handle(
                sendImageRequest(
                    roomId = 1L,
                    imagePaths = listOf(file.absolutePath),
                    imageLeases =
                        listOf(
                            signedTestImageLease(
                                requestId = "image-request",
                                roomId = 1L,
                                canonicalPath = file.canonicalPath,
                            ),
                        ),
                ),
            )

        assertEquals("failed", response.status)
        assertEquals("bridge discovery hook not ready: ChatMediaSender#sendMultiple", response.error)
        file.delete()
    }

    @Test
    fun `send image request fails when discovery installation never ran`() {
        val file = Files.createTempFile("iris-bridge", ".png").toFile().apply { writeText("x") }
        val rootDir = file.parentFile ?: error("temp file parent missing")
        val handler =
            ImageBridgeRequestHandler(
                imageSender = { error("should not be called") },
                healthProvider = {
                    ImageBridgeHealthSnapshot(
                        running = true,
                        specStatus = BridgeSpecStatus(ready = true, checkedAtEpochMs = 1L, checks = emptyList()),
                        discoverySnapshot = BridgeDiscoverySnapshot(installAttempted = false, hooks = emptyList()),
                        restartCount = 0,
                        lastCrashMessage = null,
                    )
                },
                handshakeValidator = developmentHandshakeValidator(),
                pathValidator = BridgeImagePathValidator(rootDir.absolutePath),
                leaseVerifier = testImageLeaseVerifier(),
                logError = { _, _, _ -> },
            )

        val response =
            handler.handle(
                sendImageRequest(
                    roomId = 1L,
                    imagePaths = listOf(file.absolutePath),
                    imageLeases =
                        listOf(
                            signedTestImageLease(
                                requestId = "image-request",
                                roomId = 1L,
                                canonicalPath = file.canonicalPath,
                            ),
                        ),
                ),
            )

        assertEquals("failed", response.status)
        assertEquals("bridge discovery hooks not installed", response.error)
        file.delete()
    }

    @Test
    fun `threaded send image request fails closed when threaded discovery hook is not installed`() {
        val file = Files.createTempFile("iris-bridge", ".png").toFile().apply { writeText("x") }
        val rootDir = file.parentFile ?: error("temp file parent missing")
        val handler =
            ImageBridgeRequestHandler(
                imageSender = { error("should not be called") },
                healthProvider = {
                    ImageBridgeHealthSnapshot(
                        running = true,
                        specStatus = BridgeSpecStatus(ready = true, checkedAtEpochMs = 1L, checks = emptyList()),
                        discoverySnapshot =
                            BridgeDiscoverySnapshot(
                                installAttempted = true,
                                hooks =
                                    listOf(
                                        DiscoveryHookStatus(name = HOOK_SEND_SINGLE, installed = true, invocationCount = 0),
                                        DiscoveryHookStatus(name = HOOK_SEND_THREADED_ENTRY, installed = false, invocationCount = 0),
                                    ),
                            ),
                        restartCount = 0,
                        lastCrashMessage = null,
                    )
                },
                handshakeValidator = developmentHandshakeValidator(),
                pathValidator = BridgeImagePathValidator(rootDir.absolutePath),
                leaseVerifier = testImageLeaseVerifier(),
                logError = { _, _, _ -> },
            )

        val response =
            handler.handle(
                sendImageRequest(
                    roomId = 1L,
                    imagePaths = listOf(file.absolutePath),
                    threadId = 55L,
                    threadScope = 2,
                    imageLeases =
                        listOf(
                            signedTestImageLease(
                                requestId = "image-request",
                                roomId = 1L,
                                canonicalPath = file.canonicalPath,
                            ),
                        ),
                ),
            )

        assertEquals("failed", response.status)
        assertEquals("bridge discovery hook not ready: ChatMediaSender#threadedEntry", response.error)
        file.delete()
    }

    @Test
    fun `threaded send image request requires threaded inject discovery hook`() {
        val file = Files.createTempFile("iris-bridge", ".png").toFile().apply { writeText("x") }
        val rootDir = file.parentFile ?: error("temp file parent missing")
        val handler =
            ImageBridgeRequestHandler(
                imageSender = { error("should not be called") },
                healthProvider = {
                    ImageBridgeHealthSnapshot(
                        running = true,
                        specStatus = BridgeSpecStatus(ready = true, checkedAtEpochMs = 1L, checks = emptyList()),
                        discoverySnapshot =
                            BridgeDiscoverySnapshot(
                                installAttempted = true,
                                hooks =
                                    listOf(
                                        DiscoveryHookStatus(name = HOOK_SEND_THREADED_ENTRY, installed = true, invocationCount = 0),
                                        DiscoveryHookStatus(name = HOOK_SEND_THREADED_INJECT, installed = false, invocationCount = 0),
                                    ),
                            ),
                        restartCount = 0,
                        lastCrashMessage = null,
                    )
                },
                handshakeValidator = developmentHandshakeValidator(),
                pathValidator = BridgeImagePathValidator(rootDir.absolutePath),
                leaseVerifier = testImageLeaseVerifier(),
                logError = { _, _, _ -> },
            )

        val response =
            handler.handle(
                sendImageRequest(
                    roomId = 1L,
                    imagePaths = listOf(file.absolutePath),
                    threadId = 55L,
                    threadScope = 2,
                    imageLeases =
                        listOf(
                            signedTestImageLease(
                                requestId = "image-request",
                                roomId = 1L,
                                canonicalPath = file.canonicalPath,
                            ),
                        ),
                ),
            )

        assertEquals("failed", response.status)
        assertEquals("bridge discovery hook not ready: ChatMediaSender#threadedInject", response.error)
        file.delete()
    }
}
