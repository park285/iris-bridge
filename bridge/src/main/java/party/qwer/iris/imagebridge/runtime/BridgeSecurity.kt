package party.qwer.iris.imagebridge.runtime

import android.os.Process
import java.io.File

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
        ): Set<Int> {
            val defaults =
                when (securityMode) {
                    BridgeSecurityMode.PRODUCTION -> linkedSetOf(Process.ROOT_UID)
                    BridgeSecurityMode.DEVELOPMENT ->
                        linkedSetOf(
                            Process.ROOT_UID,
                            Process.SHELL_UID,
                        )
                }
            val configured =
                extraUidsRaw
                    ?.split(',')
                    ?.mapNotNull { token -> token.trim().toIntOrNull() }
                    ?.toSet()
                    .orEmpty()
            return defaults + configured
        }

        internal fun defaultAllowedUids(raw: String?): Set<Int> = buildAllowedUids(BridgeSecurityMode.PRODUCTION, raw)
    }
}

internal class BridgeImagePathValidator(
    rootPaths: Collection<String> = DEFAULT_ALLOWED_IMAGE_ROOTS,
) {
    constructor(rootPath: String) : this(listOf(rootPath))

    private val allowedRoots = rootPaths.map { rootPath -> File(rootPath).canonicalFile }

    fun validate(imagePaths: List<String>): List<String> {
        require(imagePaths.isNotEmpty()) { "no image paths" }
        return imagePaths.map { path ->
            val imageFile = File(path).canonicalFile
            require(imageFile.isFile) { "image file not found: $path" }
            require(imageFile.isUnderAllowedRoot()) {
                "image path is outside allowed root: $path"
            }
            imageFile.path
        }
    }

    private fun File.isUnderAllowedRoot(): Boolean =
        allowedRoots.any { allowedRoot ->
            path.startsWith("${allowedRoot.path}${File.separator}")
        }

    companion object {
        internal const val LEGACY_OUTBOX_IMAGE_ROOT = "/sdcard/Android/data/com.kakao.talk/files/iris-outbox-images"
        internal const val RUNTIME_REPLY_IMAGE_ROOT = "/data/iris/reply-images"
        internal val DEFAULT_ALLOWED_IMAGE_ROOTS =
            listOf(
                LEGACY_OUTBOX_IMAGE_ROOT,
                RUNTIME_REPLY_IMAGE_ROOT,
            )
    }
}
