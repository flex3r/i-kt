package com.flxrs

import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.basic
import io.ktor.server.engine.embeddedServer
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.request.path
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import java.security.MessageDigest

fun main() {
    val config = UploadConfig.fromEnvironment(System.getenv())
    embeddedServer(Netty, port = config.port) {
        uploadModule(config)
    }.start(wait = true)
}

fun Application.uploadModule(config: UploadConfig) {
    val storage = UploadStorage(config.directory)
    val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    val uploadSizes =
        DistributionSummary
            .builder("upload.size")
            .baseUnit("bytes")
            .register(registry)

    install(CallLogging) {
        filter { call -> call.request.path() != "/metrics" }
    }
    install(MicrometerMetrics) {
        this.registry = registry
    }
    install(Authentication) {
        basic {
            realm = "i-kt"
            validate { credentials ->
                when {
                    credentials.name == config.user && credentials.password.constantTimeEquals(config.password) -> UserIdPrincipal(credentials.name)
                    else -> null
                }
            }
        }
    }

    routing {
        get("/") {
            call.respondText("FeelsDankMan")
        }
        get("/metrics") {
            call.respondText(registry.scrape())
        }
        authenticate {
            post("/upload") {
                when (val upload = receiveUpload(storage, config.maxFileSize)) {
                    null -> {
                        call.respondText("No file in upload", status = HttpStatusCode.BadRequest)
                    }

                    else -> {
                        uploadSizes.record(upload.size.toDouble())
                        call.respondText("${config.publicUrl}${upload.name}")
                    }
                }
            }
        }
    }
}

private suspend fun RoutingContext.receiveUpload(
    storage: UploadStorage,
    maxFileSize: Long,
): StoredUpload? {
    var upload: StoredUpload? = null
    call.receiveMultipart(formFieldLimit = maxFileSize).forEachPart { part ->
        when {
            part is PartData.FileItem && upload == null -> upload = storage.save(part.originalFileName, part.provider())
        }
        part.release()
    }
    return upload
}

private fun String.constantTimeEquals(other: String): Boolean = MessageDigest.isEqual(toByteArray(), other.toByteArray())
