package com.flxrs

import java.nio.file.Path
import kotlin.io.path.Path

class UploadConfig(
    val user: String,
    val password: String,
    val publicUrl: String,
    val directory: Path,
    val port: Int = DEFAULT_PORT,
    val maxFileSize: Long = DEFAULT_MAX_FILE_SIZE,
) {
    companion object {
        private const val DEFAULT_PORT = 8080
        private const val DEFAULT_MAX_FILE_SIZE = 100L * 1024 * 1024

        fun fromEnvironment(env: Map<String, String>): UploadConfig {
            val password = env["UPLOAD_PASSWORD"]?.takeIf { it.isNotBlank() }
            requireNotNull(password) { "UPLOAD_PASSWORD is not set" }
            val publicUrl = env["UPLOAD_URL"] ?: "http://localhost:$DEFAULT_PORT/"
            return UploadConfig(
                user = env["UPLOAD_USER"] ?: "flxrs",
                password = password,
                publicUrl = publicUrl.removeSuffix("/") + "/",
                directory = Path(env["UPLOAD_DIRECTORY"] ?: "uploads"),
                port = env["PORT"]?.toInt() ?: DEFAULT_PORT,
            )
        }
    }
}
