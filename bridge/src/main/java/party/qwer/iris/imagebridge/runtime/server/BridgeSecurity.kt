package party.qwer.iris.imagebridge.runtime.server

import android.os.Process
import party.qwer.iris.resolveBridgeReplyImageDir
import java.io.File
import java.nio.file.Files

internal data class ValidatedBridgeImagePath(
    val canonicalPath: String,
    private val allowedRoots: List<File>,
    private val sizeBytes: Long,
    private val lastModifiedEpochMs: Long,
) {
    fun revalidate(): String {
        val file = File(canonicalPath)
        require(!Files.isSymbolicLink(file.toPath())) { "image path must not be a symbolic link: $canonicalPath" }
        val current = file.canonicalFile
        require(current.isFile) { "image file not found: $canonicalPath" }
        require(current.isUnderAllowedRoot(allowedRoots)) { "image path is outside allowed root: $canonicalPath" }
        require(current.length() == sizeBytes && current.lastModified() == lastModifiedEpochMs) {
            "image file changed before send: $canonicalPath"
        }
        return current.path
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
    private val maxPathCount: Int = MAX_IMAGE_PATH_COUNT,
    private val maxPathLength: Int = MAX_IMAGE_PATH_LENGTH,
) {
    constructor(rootPath: String) : this(listOf(rootPath), MAX_IMAGE_PATH_COUNT, MAX_IMAGE_PATH_LENGTH)

    private val allowedRoots = rootPaths.map { rootPath -> File(rootPath).canonicalFile }

    fun validate(imagePaths: List<String>): List<ValidatedBridgeImagePath> {
        require(imagePaths.isNotEmpty()) { "no image paths" }
        require(imagePaths.size <= maxPathCount) { "too many image paths: ${imagePaths.size}" }
        return imagePaths.map { path ->
            require(path.isNotBlank()) { "blank image path" }
            require(path.length <= maxPathLength) { "image path is too long: ${path.length}" }
            require('\u0000' !in path) { "image path contains null byte" }
            val rawFile = File(path)
            require(!Files.isSymbolicLink(rawFile.toPath())) { "image path must not be a symbolic link: $path" }
            val imageFile = rawFile.canonicalFile
            require(imageFile.isFile) { "image file not found: $path" }
            require(imageFile.isUnderAllowedRoot(allowedRoots)) {
                "image path is outside allowed root: $path"
            }
            ValidatedBridgeImagePath(
                canonicalPath = imageFile.path,
                allowedRoots = allowedRoots,
                sizeBytes = imageFile.length(),
                lastModifiedEpochMs = imageFile.lastModified(),
            )
        }
    }

    companion object {
        internal const val LEGACY_OUTBOX_IMAGE_ROOT = "/sdcard/Android/data/com.kakao.talk/files/iris-outbox-images"
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

private fun File.isUnderAllowedRoot(allowedRoots: List<File>): Boolean =
    allowedRoots.any { allowedRoot ->
        path.startsWith("${allowedRoot.path}${File.separator}")
    }
