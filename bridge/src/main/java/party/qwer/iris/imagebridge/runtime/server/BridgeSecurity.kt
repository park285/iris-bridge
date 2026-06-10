package party.qwer.iris.imagebridge.runtime.server

import party.qwer.iris.resolveBridgeReplyImageDir
import party.qwer.iris.imagebridge.runtime.core.BridgeCore
import party.qwer.iris.imagebridge.runtime.core.BridgeCoreImagePathSnapshot
import party.qwer.iris.imagebridge.runtime.core.BridgeCoreRuntime
import party.qwer.iris.imagebridge.runtime.core.allowedPeerUids
import party.qwer.iris.imagebridge.runtime.core.materializeImagePath
import party.qwer.iris.imagebridge.runtime.core.revalidateImagePathSnapshot
import java.io.File

private typealias ImagePathMaterializer = (String, Collection<String>) -> BridgeCoreImagePathSnapshot?
private typealias ImagePathRevalidator = (String, Collection<String>, Long, Long) -> BridgeCoreImagePathSnapshot?

internal data class ValidatedBridgeImagePath(
    val canonicalPath: String,
    private val allowedRootPaths: List<String>,
    private val sizeBytes: Long,
    private val lastModifiedEpochMs: Long,
    private val revalidateImagePathSnapshot: ImagePathRevalidator = { path, roots, size, lastModified ->
        BridgeCore.revalidateImagePathSnapshot(path, roots, size, lastModified)
    },
) {
    fun revalidate(): String {
        val snapshot =
            revalidateImagePathSnapshot(
                canonicalPath,
                allowedRootPaths,
                sizeBytes,
                lastModifiedEpochMs,
            ) ?: error("bridge core unavailable to revalidate image path")
        return snapshot.canonicalPath
    }
}

internal class BridgePeerIdentityValidator(
    private val allowedUids: Set<Int>,
) {
    constructor(
        securityMode: BridgeSecurityMode = BridgeSecurityMode.fromEnv(),
        extraUidsRaw: String? = System.getenv("IRIS_BRIDGE_ALLOWED_UIDS"),
    ) : this(buildAllowedUids(securityMode, extraUidsRaw))

    fun validate(peerUid: Int?) {
        require(peerUid != null && peerUid in allowedUids) {
            "unauthorized bridge client uid=${peerUid ?: -1}"
        }
    }

    companion object {
        private fun buildAllowedUids(
            securityMode: BridgeSecurityMode,
            extraUidsRaw: String?,
        ): Set<Int> = BridgeCore.allowedPeerUids(securityMode.coreRawValue(), extraUidsRaw).toSet()

        internal fun defaultAllowedUids(raw: String?): Set<Int> = buildAllowedUids(BridgeSecurityMode.PRODUCTION, raw)
    }
}

internal class BridgeImagePathValidator(
    rootPaths: Collection<String> = DEFAULT_ALLOWED_IMAGE_ROOTS,
    private val maxPathCount: Int = MAX_IMAGE_PATH_COUNT,
    private val maxPathLength: Int = MAX_IMAGE_PATH_LENGTH,
    private val staticValidator: BridgeImagePathStaticValidator = BridgeImagePathStaticValidator(),
    private val materializeImagePath: ImagePathMaterializer = { path, roots ->
        BridgeCore.materializeImagePath(path, roots)
    },
    private val revalidateImagePathSnapshot: ImagePathRevalidator = { path, roots, size, lastModified ->
        BridgeCore.revalidateImagePathSnapshot(path, roots, size, lastModified)
    },
) {
    constructor(rootPath: String) : this(listOf(rootPath), MAX_IMAGE_PATH_COUNT, MAX_IMAGE_PATH_LENGTH)

    constructor(
        bridgeCore: BridgeCoreRuntime,
        rootPaths: Collection<String> = DEFAULT_ALLOWED_IMAGE_ROOTS,
    ) : this(rootPaths, MAX_IMAGE_PATH_COUNT, MAX_IMAGE_PATH_LENGTH, BridgeImagePathStaticValidator(bridgeCore))

    private val allowedRootPaths = rootPaths.map { rootPath -> File(rootPath).canonicalFile.path }

    fun validate(imagePaths: List<String>): List<ValidatedBridgeImagePath> {
        staticValidator.validate(imagePaths, maxPathCount, maxPathLength)
        return imagePaths.map { path ->
            val snapshot =
                materializeImagePath(path, allowedRootPaths)
                    ?: error("bridge core unavailable to materialize image path")
            snapshot.toValidatedPath(
                allowedRootPaths = allowedRootPaths,
                revalidateImagePathSnapshot = revalidateImagePathSnapshot,
            )
        }
    }

    companion object {
        internal const val LEGACY_OUTBOX_IMAGE_ROOT = "/sdcard/Android/data/com.kakao.talk/files/iris-outbox-images"
        internal const val LEGACY_OUTBOX_IMAGE_ROOT_REVANCED =
            "/sdcard/Android/data/com.kakao.talk.revanced/files/iris-outbox-images"
        internal const val RUNTIME_REPLY_IMAGE_ROOT = "/data/iris-tmp/reply-images"
        internal const val MAX_IMAGE_PATH_COUNT = 8
        internal const val MAX_IMAGE_PATH_LENGTH = 4096
        internal val DEFAULT_ALLOWED_IMAGE_ROOTS: List<String>
            get() = defaultAllowedImageRoots(System.getenv())

        internal fun defaultAllowedImageRoots(
            env: Map<String, String>,
            fileReader: (String) -> String? = { path ->
                runCatching {
                    File(path)
                        .takeIf { it.isFile }
                        ?.readText()
                }.getOrNull()
            },
        ): List<String> = listOf(resolveBridgeReplyImageDir(env = env, fileReader = fileReader))
    }
}

private fun BridgeCoreImagePathSnapshot.toValidatedPath(
    allowedRootPaths: List<String>,
    revalidateImagePathSnapshot: ImagePathRevalidator,
): ValidatedBridgeImagePath =
    ValidatedBridgeImagePath(
        canonicalPath = canonicalPath,
        allowedRootPaths = allowedRootPaths,
        sizeBytes = sizeBytes,
        lastModifiedEpochMs = lastModifiedEpochMs,
        revalidateImagePathSnapshot = revalidateImagePathSnapshot,
    )
