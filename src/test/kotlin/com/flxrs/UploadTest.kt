package com.flxrs

import io.ktor.client.request.basicAuth
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.createTempDirectory
import kotlin.io.path.getPosixFilePermissions
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readBytes
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class UploadTest {
    private val directory = createTempDirectory("i-kt-test")
    private val config =
        UploadConfig(
            user = "flxrs",
            password = "secret",
            publicUrl = "https://i.example.com/",
            directory = directory,
        )

    @AfterTest
    fun cleanup() {
        directory.toFile().deleteRecursively()
    }

    @Test
    fun `upload stores the file and responds with its url`() =
        uploadTest {
            val response = upload(fileName = "cat.PNG")

            assertEquals(HttpStatusCode.OK, response.status)
            val url = response.bodyAsText()
            assertTrue(url.startsWith("https://i.example.com/"), url)
            val name = url.removePrefix("https://i.example.com/")
            assertTrue(name.matches(Regex("[0-9a-z]{8}\\.png")), name)
            val stored = directory.resolve(name)
            assertContentEquals(CONTENT, stored.readBytes())
            assertEquals(PosixFilePermissions.fromString("rw-r--r--"), stored.getPosixFilePermissions())
            assertEquals(listOf(stored), directory.listDirectoryEntries())
        }

    @Test
    fun `uploads get distinct names`() =
        uploadTest {
            val names = List(5) { upload(fileName = "cat.png").bodyAsText() }

            assertEquals(5, names.toSet().size)
            assertEquals(5, directory.listDirectoryEntries().size)
        }

    @Test
    fun `file name without a usable extension is stored without one`() =
        uploadTest {
            val response = upload(fileName = "cat.p<h>p")

            val name = response.bodyAsText().removePrefix("https://i.example.com/")
            assertTrue(name.matches(Regex("[0-9a-z]{8}")), name)
        }

    @Test
    fun `wrong password is rejected`() =
        uploadTest {
            val response = upload(fileName = "cat.png", password = "wrong")

            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertEquals(emptyList(), directory.listDirectoryEntries())
        }

    @Test
    fun `upload without a file is rejected`() =
        uploadTest {
            val response =
                client.submitFormWithBinaryData("/upload", formData { append("description", "no file") }) {
                    basicAuth("flxrs", "secret")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `metrics count uploads and their bytes`() =
        uploadTest {
            upload(fileName = "cat.png")
            upload(fileName = "cat.png")

            val metrics = client.get("/metrics").bodyAsText()

            assertTrue(metrics.contains("upload_size_bytes_count 2\n"), metrics)
            assertTrue(metrics.contains("upload_size_bytes_sum 10.0"), metrics)
            assertTrue(metrics.contains("ktor_http_server_requests_seconds_count{"), metrics)
            assertTrue(metrics.contains("jvm_memory_used_bytes"), metrics)
        }

    @Test
    fun `missing password fails the configuration`() {
        assertFailsWith<IllegalArgumentException> {
            UploadConfig.fromEnvironment(mapOf("UPLOAD_URL" to "https://i.example.com"))
        }
    }

    @Test
    fun `public url always ends with a slash`() {
        val config = UploadConfig.fromEnvironment(mapOf("UPLOAD_PASSWORD" to "secret", "UPLOAD_URL" to "https://i.example.com"))

        assertEquals("https://i.example.com/", config.publicUrl)
    }

    private fun uploadTest(block: suspend ApplicationTestBuilder.() -> Unit) =
        testApplication {
            application { uploadModule(config) }
            block()
        }

    private suspend fun ApplicationTestBuilder.upload(
        fileName: String,
        password: String = "secret",
    ) = client.submitFormWithBinaryData(
        "/upload",
        formData {
            append(
                "file",
                CONTENT,
                Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                    append(HttpHeaders.ContentType, "image/png")
                },
            )
        },
    ) {
        basicAuth("flxrs", password)
    }

    private companion object {
        val CONTENT = byteArrayOf(1, 2, 3, 4, 5)
    }
}
