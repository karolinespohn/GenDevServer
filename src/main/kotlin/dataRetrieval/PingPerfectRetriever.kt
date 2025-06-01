package org.gendev25.project.dataRetrieval

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.example.Address
import org.example.ConnectionType
import org.example.InternetOffer

internal class PingPerfectRetriever() : DataRetriever() {

    @Serializable
    data class CompareProductsRequestData(
        val street: String,
        val plz: String,
        val houseNumber: String,
        val city: String,
        val wantsFiber: Boolean
    )


    @Serializable
    data class ProductInfo(
        val speed: Int,
        val contractDurationInMonths: Int,
        val connectionType: ConnectionType,
        val tv: String? = null,
        val limitFrom: Int? = null,
        val maxAge: Int? = null
    )

    @Serializable
    data class PricingDetails(
        val monthlyCostInCent: Int,
        val installationService: String
    )


    @OptIn(ExperimentalSerializationApi::class)
    internal suspend fun getOffers(address: Address, wantsFiber: Boolean): List<InternetOffer.PingPerfectOffer> {
        val json = Json {
            isLenient = true
        }
        val timestampSeconds = System.currentTimeMillis() / 1000
        val payload = CompareProductsRequestData(address.street, address.zip, address.number, address.city, wantsFiber)
        val jsonString = json.encodeToString(value = payload, serializer = CompareProductsRequestData.serializer())
        val signedPayload = getSignature(jsonString, timestampSeconds)

        val mediaType = "application/json".toMediaType()
        val requestBody = jsonString.toRequestBody(mediaType)
        val clientId = System.getenv("PING_PERFECT_CLI_ID")

        val request = Request.Builder()
            .url("https://pingperfect.gendev7.check24.fun/internet/angebote/data")
            .post(requestBody)
            .addHeader("X-Signature", signedPayload)
            .addHeader("X-Timestamp", timestampSeconds.toString())
            .addHeader("X-Client-Id", clientId)
            .build()

        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (!responseBody.isNullOrEmpty()) {

                    val products: List<InternetOffer.PingPerfectOffer> = try {
                        json.decodeFromString(responseBody)
                    } catch (e: SerializationException) {
                        println("Error parsing response: ${e.message}")
                        emptyList()
                    }
                    return products
                }
            } else {
                println("Request failed: ${response.code} ${response.message}")
                return emptyList()
            }
        }
        return emptyList()
    }


    private fun getSignature(payload: String, timestampSeconds: Long): String {
        val toBeEncoded = "$timestampSeconds:$payload"
        val signatureBytes = hmacSha256(toBeEncoded)
        return signatureBytes.joinToString("") { "%02x".format(it) }

    }

    fun hmacSha256(message: String): ByteArray {
        val key = System.getenv("PING_PERFECT_SIG_SECRET")
        val mac = Mac.getInstance("HmacSHA256")
        val secretKeySpec = SecretKeySpec(key.toByteArray(), "HmacSHA256")
        mac.init(secretKeySpec)
        return mac.doFinal(message.toByteArray())
    }


}