@file:Suppress("ClassName", "FunctionName", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime

import party.qwer.iris.ImageBridgeProtocol
import party.qwer.iris.ImageLease
import party.qwer.iris.ImageLeasePayload
import party.qwer.iris.SignedImageLease
import party.qwer.iris.imagebridge.runtime.discovery.BridgeDiscoverySnapshot
import party.qwer.iris.imagebridge.runtime.discovery.DiscoveryHookStatus
import party.qwer.iris.imagebridge.runtime.discovery.HOOK_SEND_MULTIPLE
import party.qwer.iris.imagebridge.runtime.discovery.HOOK_SEND_SINGLE
import party.qwer.iris.imagebridge.runtime.discovery.HOOK_SEND_THREADED_ENTRY
import party.qwer.iris.imagebridge.runtime.discovery.HOOK_SEND_THREADED_INJECT
import party.qwer.iris.imagebridge.runtime.server.BridgeHandshakeValidator
import party.qwer.iris.imagebridge.runtime.server.BridgeSecurityMode
import party.qwer.iris.imagebridge.runtime.server.BridgeSpecStatus
import party.qwer.iris.imagebridge.runtime.server.ImageBridgeCapabilitiesSnapshot
import party.qwer.iris.imagebridge.runtime.server.ImageBridgeCapabilitySnapshot
import party.qwer.iris.imagebridge.runtime.server.ImageBridgeHealthSnapshot

internal fun sendImageRequest(
    roomId: Long,
    imagePaths: List<String>,
    threadId: Long? = null,
    threadScope: Int? = null,
    requestId: String? = "image-request",
    token: String? = null,
    imageLeases: List<SignedImageLease> = emptyList(),
): ImageBridgeProtocol.ImageBridgeRequest =
    ImageBridgeProtocol.buildSendImageRequest(
        roomId = roomId,
        imagePaths = imagePaths,
        threadId = threadId,
        threadScope = threadScope,
        requestId = requestId,
        token = token,
        imageLeases = imageLeases,
    )

internal fun signedImageLease(
    secret: String,
    requestId: String,
    roomId: Long,
    canonicalPath: String,
    imageIndex: Int = 0,
    expiresAtEpochMs: Long = Long.MAX_VALUE,
): SignedImageLease =
    ImageLease.issue(
        secret,
        ImageLeasePayload(
            version = ImageLease.VERSION,
            requestId = requestId,
            roomId = roomId,
            imageIndex = imageIndex,
            canonicalPath = canonicalPath,
            sha256Hex = "deadbeef",
            byteLength = 1L,
            contentType = "image/png",
            lastModifiedEpochMs = 1L,
            expiresAtEpochMs = expiresAtEpochMs,
            nonce = "$requestId:$imageIndex",
        ),
    )

internal fun sendTextRequest(
    roomId: Long,
    message: String,
    threadId: Long? = null,
    threadScope: Int? = null,
    mentionsJson: String? = null,
    attachmentJson: String? = null,
    requestId: String? = "text-request",
    token: String? = null,
): ImageBridgeProtocol.ImageBridgeRequest =
    ImageBridgeProtocol.buildSendTextRequest(
        roomId = roomId,
        message = message,
        threadId = threadId,
        threadScope = threadScope,
        mentionsJson = mentionsJson,
        attachmentJson = attachmentJson,
        requestId = requestId,
        token = token,
    )

internal fun sendMarkdownRequest(
    roomId: Long,
    message: String,
    threadId: Long? = null,
    threadScope: Int? = null,
    mentionsJson: String? = null,
    attachmentJson: String? = null,
    requestId: String? = "markdown-request",
    token: String? = null,
): ImageBridgeProtocol.ImageBridgeRequest {
    val request =
        ImageBridgeProtocol.buildSendMarkdownRequest(
            roomId = roomId,
            message = message,
            threadId = threadId,
            threadScope = threadScope,
            mentionsJson = mentionsJson,
            requestId = requestId,
            token = token,
        )
    return request.copy(attachmentJson = attachmentJson)
}

internal fun healthRequest(token: String? = null): ImageBridgeProtocol.ImageBridgeRequest = ImageBridgeProtocol.buildHealthRequest(token = token)

internal fun inspectChatRoomRequest(
    roomId: Long,
    token: String? = null,
): ImageBridgeProtocol.ImageBridgeRequest =
    ImageBridgeProtocol.buildInspectChatRoomRequest(
        roomId = roomId,
        token = token,
    )

internal fun openChatRoomRequest(
    roomId: Long,
    requestId: String? = "open-request",
    token: String? = null,
): ImageBridgeProtocol.ImageBridgeRequest =
    ImageBridgeProtocol.buildOpenChatRoomRequest(
        roomId = roomId,
        requestId = requestId,
        token = token,
    )

internal fun snapshotChatRoomMembersRequest(
    roomId: Long,
    memberIds: List<Long> = emptyList(),
    memberHints: List<ImageBridgeProtocol.ChatRoomMemberHint> = emptyList(),
    preferredMemberPlan: ImageBridgeProtocol.ChatRoomMemberExtractionPlan? = null,
    token: String? = null,
): ImageBridgeProtocol.ImageBridgeRequest =
    ImageBridgeProtocol.buildSnapshotChatRoomMembersRequest(
        roomId = roomId,
        memberIds = memberIds,
        memberHints = memberHints,
        preferredMemberPlan = preferredMemberPlan,
        token = token,
    )

internal fun developmentHandshakeValidator(): BridgeHandshakeValidator =
    BridgeHandshakeValidator(
        expectedToken = "",
        securityMode = BridgeSecurityMode.DEVELOPMENT,
    )

internal fun readyHealthSnapshot(): ImageBridgeHealthSnapshot =
    ImageBridgeHealthSnapshot(
        running = true,
        specStatus = BridgeSpecStatus(ready = true, checkedAtEpochMs = 1L, checks = emptyList()),
        discoverySnapshot =
            BridgeDiscoverySnapshot(
                installAttempted = true,
                hooks =
                    listOf(
                        DiscoveryHookStatus(name = HOOK_SEND_SINGLE, installed = true, invocationCount = 0),
                        DiscoveryHookStatus(name = HOOK_SEND_MULTIPLE, installed = true, invocationCount = 0),
                        DiscoveryHookStatus(name = HOOK_SEND_THREADED_ENTRY, installed = true, invocationCount = 0),
                        DiscoveryHookStatus(name = HOOK_SEND_THREADED_INJECT, installed = true, invocationCount = 0),
                    ),
            ),
        restartCount = 0,
        lastCrashMessage = null,
    )

internal fun readyTextHealthSnapshot(): ImageBridgeHealthSnapshot =
    readyHealthSnapshot().copy(
        capabilities =
            ImageBridgeCapabilitiesSnapshot(
                sendText = ImageBridgeCapabilitySnapshot(supported = true, ready = true),
                sendMarkdown = ImageBridgeCapabilitySnapshot(supported = true, ready = true),
            ),
    )
