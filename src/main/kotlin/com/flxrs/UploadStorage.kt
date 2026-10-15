package com.flxrs

import io.ktor.util.cio.writeChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.copyAndClose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists

class UploadStorage(
    private val directory: Path,
) {
    init {
        directory.createDirectories()
    }

    suspend fun save(
        originalFileName: String?,
        content: ByteReadChannel,
    ): StoredUpload =
        withContext(Dispatchers.IO) {
            val temp = Files.createTempFile(directory, ".upload-", ".tmp")
            try {
                val size = content.copyAndClose(temp.toFile().writeChannel())
                Files.setPosixFilePermissions(temp, FILE_PERMISSIONS)
                val target = claimName(extensionOf(originalFileName))
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                StoredUpload(name = target.fileName.toString(), size = size)
            } finally {
                temp.deleteIfExists()
            }
        }

    private fun claimName(extension: String?): Path =
        generateSequence(System.currentTimeMillis()) { it + 1 }.firstNotNullOf { millis ->
            val name = millis.toString(radix = 36)
            val target =
                when (extension) {
                    null -> directory.resolve(name)
                    else -> directory.resolve("$name.$extension")
                }
            try {
                Files.createFile(target)
            } catch (_: FileAlreadyExistsException) {
                null
            }
        }

    private fun extensionOf(fileName: String?): String? =
        fileName
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase()
            ?.takeIf { it.matches(EXTENSION) }

    private companion object {
        val EXTENSION = Regex("[a-z0-9]{1,10}")
        val FILE_PERMISSIONS = PosixFilePermissions.fromString("rw-r--r--")
    }
}

class StoredUpload(
    val name: String,
    val size: Long,
)
