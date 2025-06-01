package org.gendev25.project.dataRetrieval

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.example.Address
import org.example.ConnectionType
import org.example.InternetOffer

internal class ServusSpeedRetriever : DataRetriever() {

    @Serializable
    data class InternetAngeboteRequestData(
        val address: RequestAddress
    )

    @Serializable
    data class RequestAddress(
        val strasse: String,
        val hausnummer: String,
        val postleitzahl: String,
        val stadt: String,
        val land: String
    )

    @Serializable
    data class InternetOffersResponseDataList(
        val availableProducts: List<String>
    )

    @Serializable
    data class DetailedResponseData(
        val servusSpeedProduct: ServusSpeedProduct
    )

    @Serializable
    data class ServusSpeedProduct(
        val providerName: String,
        val productInfo: OfferProductInfo,
        val pricingDetails: OfferPricingDetails,
        val discount: Int
    )

    @Serializable
    data class OfferProductInfo(
        val speed: Int,
        val contractDurationInMonths: Int,
        val connectionType: String,
        val tv: String? = null,
        val limitFrom: Int? = null,
        val maxAge: Int? = null
    )

    @Serializable
    data class OfferPricingDetails(
        val monthlyCostInCent: Int,
        val installationService: Boolean
    )

    private val baseUrl = "https://servus-speed.gendev7.check24.fun"

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun getOffers(address: Address): List<InternetOffer.ServusSpeedOffer> {
        val json = Json {
            isLenient = true
            ignoreUnknownKeys = true
        }

        val availableProducts = getAvailableProducts(address, json)
        if (availableProducts.isEmpty()) {
            return emptyList()
        }

        // Use coroutines to fetch product details in parallel
        return coroutineScope {
            val deferredOffers = availableProducts.map { productId ->
                async {
                    getProductDetails(address, productId, json)
                }
            }

            // Await all results and filter out nulls
            deferredOffers.mapNotNull { it.await() }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun getAvailableProducts(address: Address, json: Json): List<String> {
        val requestData = InternetAngeboteRequestData(
            address = RequestAddress(
                strasse = address.street,
                hausnummer = address.number,
                postleitzahl = address.zip,
                stadt = address.city,
                land = address.country.iso
            )
        )

        val jsonString = json.encodeToString(
            InternetAngeboteRequestData.serializer(),
            requestData
        )

        val mediaType = "application/json".toMediaType()
        val requestBody = jsonString.toRequestBody(mediaType)

        val username = System.getenv("SERVUS_SPEED_USERNAME")
        val password = System.getenv("SERVUS_SPEED_PASSWORD")
        val credentials = Credentials.basic(username, password)

        val request = Request.Builder()
            .url("$baseUrl/api/external/available-products")
            .post(requestBody)
            .addHeader("Authorization", credentials)
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (!responseBody.isNullOrEmpty()) {
                    return try {
                        val responseData: InternetOffersResponseDataList =
                            json.decodeFromString(responseBody)
                        responseData.availableProducts
                    } catch (e: SerializationException) {
                        println("Error parsing available products response: ${e.message}")
                        emptyList()
                    }
                }
            } else {
                println("Request for available products failed: ${response.code} ${response.message}")
            }
        }
        return emptyList()
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun getProductDetails(
        address: Address,
        productId: String,
        json: Json
    ): InternetOffer.ServusSpeedOffer? {
        val requestData = InternetAngeboteRequestData(
            address = RequestAddress(
                strasse = address.street,
                hausnummer = address.number,
                postleitzahl = address.zip,
                stadt = address.city,
                land = address.country.iso
            )
        )

        val jsonString = json.encodeToString(
            InternetAngeboteRequestData.serializer(),
            requestData
        )

        val mediaType = "application/json".toMediaType()
        val requestBody = jsonString.toRequestBody(mediaType)

        val username = System.getenv("SERVUS_SPEED_USERNAME")
        val password = System.getenv("SERVUS_SPEED_PASSWORD")
        val credentials = Credentials.basic(username, password)

        val request = Request.Builder()
            .url("$baseUrl/api/external/product-details/$productId")
            .post(requestBody)
            .addHeader("Authorization", credentials)
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (!responseBody.isNullOrEmpty()) {
                    return try {
                        val detailedResponse: DetailedResponseData =
                            json.decodeFromString(responseBody)

                        val product = detailedResponse.servusSpeedProduct

                        InternetOffer.ServusSpeedOffer(
                            providerName = product.providerName,
                            speed = product.productInfo.speed,
                            contractDurationInMonths = product.productInfo.contractDurationInMonths,
                            connectionType = ConnectionType.fromString(product.productInfo.connectionType),
                            tv = product.productInfo.tv,
                            limitFrom = product.productInfo.limitFrom,
                            maxAge = product.productInfo.maxAge,
                            monthlyCostInCent = product.pricingDetails.monthlyCostInCent,
                            installationService = product.pricingDetails.installationService,
                            discount = product.discount / 100.0 // Convert from cent to EUR
                        )
                    } catch (e: SerializationException) {
                        println("Error parsing product details response for $productId: ${e.message}")
                        null
                    }
                }
            } else {
                println("Request for product details failed for $productId: ${response.code} ${response.message}")
            }
        }
        return null
    }
}
