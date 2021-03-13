package com.flxrs

import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File
import java.io.InputStream
import java.io.OutputStream

fun main(args: Array<String>): Unit =
    io.ktor.server.netty.EngineMain.main(args)

fun Application.dank() {
    val basicAuthUser = environment.config.propertyOrNull("ktor.deployment.basicAuthUser")?.getString() ?: "flxrs"
    val basicAuthPassword = environment.config.propertyOrNull("ktor.deployment.basicAuthPassword")?.getString() ?: "dank"
    val validCredentials = UserPasswordCredential(basicAuthUser, basicAuthPassword)

    val uploadUrl = environment.config.propertyOrNull("ktor.deployment.uploadUrl")?.getString() ?: "http://i.localhost:8080/"
    val uploadDirectory = File(environment.config.propertyOrNull("ktor.deployment.uploadDirectory")?.getString() ?: "i").also { dir ->
        if (!dir.exists())
            dir.mkdir()
    }

    install(DefaultHeaders) {
        header("User-Agent", "i-kt/1.0")
    }
    install(CallLogging)
    install(Authentication) {
        basic {
            realm = "i-kt"
            validate { credentials ->
                credentials.validateCredentials(validCredentials)
            }
        }
    }

    routing {
        get("/") {
            call.respondText("FeelsDankMan")
        }

        authenticate {
            post("upload") {
                var partData: PartData.FileItem? = null
                call.receiveMultipart().forEachPart { part ->
                    when (part) {
                        is PartData.FileItem -> partData = part
                        else -> part.dispose()
                    }
                }

                val fileName = partData?.copyImage(uploadDirectory) ?: call.respond(HttpStatusCode.BadRequest)
                call.respondText("$uploadUrl$fileName")
            }
        }

        host(Regex("i\\..+")) {
            static {
                files(uploadDirectory)
            }
        }
    }
}

private fun UserPasswordCredential.validateCredentials(valid: UserPasswordCredential): UserIdPrincipal? = when {
    name == valid.name && password == valid.password -> UserIdPrincipal(name)
    else -> null
}

private suspend fun PartData.FileItem.copyImage(uploadDir: File): String? {
    val requestFileName = originalFileName ?: return null
    val extension = File(requestFileName).extension

    val fileName = "${generateFileName()}.$extension"
    val file = File(uploadDir, fileName)

    streamProvider().use { input ->
        file.outputStream().buffered().use { output ->
            input.copyToSuspend(output)
        }
    }
    dispose()
    return fileName
}

private fun generateFileName(): String = System.currentTimeMillis().toString(radix = 36)

@Suppress("BlockingMethodInNonBlockingContext")
suspend fun InputStream.copyToSuspend(
    out: OutputStream,
    bufferSize: Int = DEFAULT_BUFFER_SIZE,
    yieldSize: Int = 4 * 1024 * 1024,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
): Long {
    return withContext(dispatcher) {
        val buffer = ByteArray(bufferSize)
        var bytesCopied = 0L
        var bytesAfterYield = 0L
        while (true) {
            val bytes = read(buffer).takeIf { it >= 0 } ?: break
            out.write(buffer, 0, bytes)
            if (bytesAfterYield >= yieldSize) {
                yield()
                bytesAfterYield %= yieldSize
            }
            bytesCopied += bytes
            bytesAfterYield += bytes
        }

        bytesCopied
    }
}
