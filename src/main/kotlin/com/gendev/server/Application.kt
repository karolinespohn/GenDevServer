package com.gendev.server

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.example.Address
import org.example.ConnectionType
import org.example.Country
import org.example.InternetOffer
import org.gendev25.project.dataRetrieval.*

// Request data classes
@Serializable
data class OffersRequest(
    val address: AddressRequest,
    val wantsFiber: Boolean = false,
    val installation: Boolean = false,
    val connectionType: String = "DSL"
)

@Serializable
data class AddressRequest(
    val street: String,
    val number: String,
    val city: String,
    val zip: String,
    val country: String
)

@Serializable
data class PingPerfectRequest(
    val address: AddressRequest,
    val wantsFiber: Boolean = false
)

@Serializable
data class WebWunderRequest(
    val address: AddressRequest,
    val installation: Boolean = false,
    val connectionType: String = "DSL"
)

@Serializable
data class BasicRequest(
    val address: AddressRequest
)

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        module()
    }.start(wait = true)
}

fun Application.module() {
    // Configure CORS
    install(CORS) {
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowHeader(HttpHeaders.ContentType)
        anyHost()
    }

    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }

    routing {
        // Health check endpoint
        get("/health") {
            call.respond(mapOf("status" to "UP"))
        }

        // Test endpoint to check if retrievers work quickly
        get("/test-retrievers") {
            try {
                val testAddress = Address(
                    street = "Teststraße",
                    number = "1",
                    city = "Wien",
                    zip = "1010",
                    country = Country.AUSTRIA
                )

                application.log.info("Testing individual retrievers...")

                val results = mutableMapOf<String, Any>()

                // Test each retriever with timeout
                results["servusSpeed"] = try {
                    val start = System.currentTimeMillis()
                    ServusSpeedRetriever().getOffers(testAddress)
                    mapOf("status" to "success", "time" to "${System.currentTimeMillis() - start}ms")
                } catch (e: Exception) {
                    mapOf("status" to "error", "message" to e.message)
                }

                results["byteMe"] = try {
                    val start = System.currentTimeMillis()
                    ByteMeRetriever().getOffers(testAddress)
                    mapOf("status" to "success", "time" to "${System.currentTimeMillis() - start}ms")
                } catch (e: Exception) {
                    mapOf("status" to "error", "message" to e.message)
                }

                call.respond(results)
            } catch (e: Exception) {
                call.respond(mapOf("error" to e.message))
            }
        }

        // API endpoints for each retriever
        route("/api") {
            // ServusSpeed endpoint
            post("/servusspeed/offers") {
                try {
                    val request = call.receive<BasicRequest>()

                    val country = try {
                        Country.valueOf(request.address.country.uppercase())
                    } catch (e: IllegalArgumentException) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf(
                                "error" to "Invalid country: ${request.address.country}. Valid values: ${
                                    Country.values().joinToString()
                                }"
                            )
                        )
                        return@post
                    }

                    val addressObj = Address(
                        street = request.address.street,
                        number = request.address.number,
                        city = request.address.city,
                        zip = request.address.zip,
                        country = country
                    )

                    val retriever = ServusSpeedRetriever()
                    val offers = retriever.getOffers(addressObj)
                    call.respond(offers)
                } catch (e: Exception) {
                    application.log.error("ServusSpeed error: ${e.message}", e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }

            // ByteMe endpoint
            post("/byteme/offers") {
                try {
                    val request = call.receive<BasicRequest>()

                    val country = try {
                        Country.valueOf(request.address.country.uppercase())
                    } catch (e: IllegalArgumentException) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf(
                                "error" to "Invalid country: ${request.address.country}. Valid values: ${
                                    Country.values().joinToString()
                                }"
                            )
                        )
                        return@post
                    }

                    val addressObj = Address(
                        street = request.address.street,
                        number = request.address.number,
                        city = request.address.city,
                        zip = request.address.zip,
                        country = country
                    )

                    val retriever = ByteMeRetriever()
                    val offers = retriever.getOffers(addressObj)
                    call.respond(offers)
                } catch (e: Exception) {
                    application.log.error("ByteMe error: ${e.message}", e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }

            // PingPerfect endpoint
            post("/pingperfect/offers") {
                try {
                    val request = call.receive<PingPerfectRequest>()

                    val country = try {
                        Country.valueOf(request.address.country.uppercase())
                    } catch (e: IllegalArgumentException) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf(
                                "error" to "Invalid country: ${request.address.country}. Valid values: ${
                                    Country.values().joinToString()
                                }"
                            )
                        )
                        return@post
                    }

                    val addressObj = Address(
                        street = request.address.street,
                        number = request.address.number,
                        city = request.address.city,
                        zip = request.address.zip,
                        country = country
                    )

                    val retriever = PingPerfectRetriever()
                    val offers = retriever.getOffers(addressObj, request.wantsFiber)
                    call.respond(offers)
                } catch (e: Exception) {
                    application.log.error("PingPerfect error: ${e.message}", e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }

            // VerbynDich endpoint
            post("/verbyndich/offers") {
                try {
                    val request = call.receive<BasicRequest>()

                    val country = try {
                        Country.valueOf(request.address.country.uppercase())
                    } catch (e: IllegalArgumentException) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf(
                                "error" to "Invalid country: ${request.address.country}. Valid values: ${
                                    Country.values().joinToString()
                                }"
                            )
                        )
                        return@post
                    }

                    val addressObj = Address(
                        street = request.address.street,
                        number = request.address.number,
                        city = request.address.city,
                        zip = request.address.zip,
                        country = country
                    )

                    val retriever = VerbynDichRetriever()
                    val offers = retriever.getOffers(addressObj)
                    call.respond(offers)
                } catch (e: Exception) {
                    application.log.error("VerbynDich error: ${e.message}", e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }

            // WebWunder endpoint
            post("/webwunder/offers") {
                try {
                    val request = call.receive<WebWunderRequest>()

                    val country = try {
                        Country.valueOf(request.address.country.uppercase())
                    } catch (e: IllegalArgumentException) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf(
                                "error" to "Invalid country: ${request.address.country}. Valid values: ${
                                    Country.values().joinToString()
                                }"
                            )
                        )
                        return@post
                    }

                    val addressObj = Address(
                        street = request.address.street,
                        number = request.address.number,
                        city = request.address.city,
                        zip = request.address.zip,
                        country = country
                    )

                    val connectionType = ConnectionType.fromString(request.connectionType)

                    val retriever = WebWunderRetriever()
                    val offers = retriever.getOffers(addressObj, request.installation, connectionType)
                    call.respond(offers)
                } catch (e: Exception) {
                    application.log.error("WebWunder error: ${e.message}", e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }

            // Combined endpoint to get all offers
            // Alternative simpler fix - replace the /all/offers endpoint with this:
            post("/all/offers") {
                try {
                    application.log.info("=== Processing all offers request ===")

                    val request = call.receive<OffersRequest>()
                    application.log.info("Received request: $request")

                    val country = try {
                        Country.valueOf(request.address.country.uppercase())
                    } catch (e: IllegalArgumentException) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf(
                                "error" to "Invalid country: ${request.address.country}. Valid values: ${
                                    Country.values().joinToString()
                                }"
                            )
                        )
                        return@post
                    }

                    val addressObj = Address(
                        street = request.address.street,
                        number = request.address.number,
                        city = request.address.city,
                        zip = request.address.zip,
                        country = country
                    )

                    val connectionType = ConnectionType.fromString(request.connectionType)

                    application.log.info("Parsed address: $addressObj")
                    application.log.info("Starting retrievals...")

                    // Create a JSON string manually to avoid serialization issues
                    val jsonBuilder = StringBuilder()
                    jsonBuilder.append("{\n")

                    // ServusSpeed
                    application.log.info("1. Starting ServusSpeed...")
                    val startTime1 = System.currentTimeMillis()
                    try {
                        val servusSpeedOffers = ServusSpeedRetriever().getOffers(addressObj)
                        val servusSpeedJson = Json.encodeToString(
                            ListSerializer(InternetOffer.ServusSpeedOffer.serializer()),
                            servusSpeedOffers
                        )
                        jsonBuilder.append("  \"servusSpeed\": $servusSpeedJson")
                        application.log.info("ServusSpeed completed in ${System.currentTimeMillis() - startTime1}ms")
                    } catch (e: Exception) {
                        application.log.error("ServusSpeed failed: ${e.message}")
                        jsonBuilder.append("  \"servusSpeed\": {\"error\": \"${e.message}\"}")
                    }

                    jsonBuilder.append(",\n")

                    // ByteMe
                    application.log.info("2. Starting ByteMe...")
                    val startTime2 = System.currentTimeMillis()
                    try {
                        val byteMeOffers = ByteMeRetriever().getOffers(addressObj)
                        val byteMeJson =
                            Json.encodeToString(ListSerializer(InternetOffer.ByteMeOffer.serializer()), byteMeOffers)
                        jsonBuilder.append("  \"byteMe\": $byteMeJson")
                        application.log.info("ByteMe completed in ${System.currentTimeMillis() - startTime2}ms")
                    } catch (e: Exception) {
                        application.log.error("ByteMe failed: ${e.message}")
                        jsonBuilder.append("  \"byteMe\": {\"error\": \"${e.message}\"}")
                    }

                    jsonBuilder.append(",\n")

                    // PingPerfect
                    application.log.info("3. Starting PingPerfect...")
                    val startTime3 = System.currentTimeMillis()
                    try {
                        val pingPerfectOffers = PingPerfectRetriever().getOffers(addressObj, request.wantsFiber)
                        val pingPerfectJson = Json.encodeToString(
                            ListSerializer(InternetOffer.PingPerfectOffer.serializer()),
                            pingPerfectOffers
                        )
                        jsonBuilder.append("  \"pingPerfect\": $pingPerfectJson")
                        application.log.info("PingPerfect completed in ${System.currentTimeMillis() - startTime3}ms")
                    } catch (e: Exception) {
                        application.log.error("PingPerfect failed: ${e.message}")
                        jsonBuilder.append("  \"pingPerfect\": {\"error\": \"${e.message}\"}")
                    }

                    jsonBuilder.append(",\n")

                    // VerbynDich
                    application.log.info("4. Starting VerbynDich...")
                    val startTime4 = System.currentTimeMillis()
                    try {
                        val verbynDichOffers = VerbynDichRetriever().getOffers(addressObj)
                        val verbynDichJson = Json.encodeToString(
                            ListSerializer(InternetOffer.VerbynDichOffer.serializer()),
                            verbynDichOffers
                        )
                        jsonBuilder.append("  \"verbynDich\": $verbynDichJson")
                        application.log.info("VerbynDich completed in ${System.currentTimeMillis() - startTime4}ms")
                    } catch (e: Exception) {
                        application.log.error("VerbynDich failed: ${e.message}")
                        jsonBuilder.append("  \"verbynDich\": {\"error\": \"${e.message}\"}")
                    }

                    jsonBuilder.append(",\n")

                    // WebWunder
                    application.log.info("5. Starting WebWunder...")
                    val startTime5 = System.currentTimeMillis()
                    try {
                        val webWunderOffers =
                            WebWunderRetriever().getOffers(addressObj, request.installation, connectionType)
                        val webWunderJson = Json.encodeToString(
                            ListSerializer(InternetOffer.WebWunderOffer.serializer()),
                            webWunderOffers
                        )
                        jsonBuilder.append("  \"webWunder\": $webWunderJson")
                        application.log.info("WebWunder completed in ${System.currentTimeMillis() - startTime5}ms")
                    } catch (e: Exception) {
                        application.log.error("WebWunder failed: ${e.message}")
                        jsonBuilder.append("  \"webWunder\": {\"error\": \"${e.message}\"}")
                    }

                    jsonBuilder.append("\n}")

                    application.log.info("All retrievals completed successfully")

                    // Respond with raw JSON string
                    call.respondText(
                        text = jsonBuilder.toString(),
                        contentType = ContentType.Application.Json
                    )

                } catch (e: Exception) {
                    application.log.error("Error processing all offers request: ${e.message}", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to "Internal server error: ${e.message}")
                    )
                }
            }
        }
    }
}