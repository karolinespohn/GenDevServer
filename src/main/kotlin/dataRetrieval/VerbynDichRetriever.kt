package org.gendev25.project.dataRetrieval

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.example.Address
import org.example.ConnectionType
import org.example.InternetOffer

internal class VerbynDichRetriever : DataRetriever() {


    @Serializable
    data class VerbynDichRequest(
        val product: String,
        val description: String,
        val last: Boolean,
        val valid: Boolean
    )

    @Serializable
    data class VerbynDichResponse(
        val product: String,
        val description: String,
        val last: Boolean,
        val valid: Boolean
    )

    private val baseUrl = "https://verbyndich.gendev7.check24.fun"
    private val endpoint = "/check24/data"

    suspend fun getOffers(address: Address): List<InternetOffer.VerbynDichOffer> {
        val responses = getResponses(address)
        return responsesToOffers(responses)


    }


    @OptIn(ExperimentalSerializationApi::class)
    suspend fun getResponses(address: Address): List<VerbynDichResponse> {
        val json = Json {
            isLenient = true
            ignoreUnknownKeys = true
        }

        val offers = mutableListOf<VerbynDichResponse>()
        var currentPage = 0
        var isLastPage = false

        while (!isLastPage) {
            val addressString = "${address.street};${address.number};${address.city};${address.zip}"
            val apiKey = System.getenv("VERBYN_DICH_KEY")
            val url = "$baseUrl$endpoint?apiKey=$apiKey&page=$currentPage"

            val requestBody = addressString.toRequestBody("text/plain".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (responseBody != null) {
                        try {
                            val apiResponse = json.decodeFromString<VerbynDichRequest>(responseBody)

                            val response = VerbynDichResponse(
                                product = apiResponse.product,
                                description = apiResponse.description,
                                last = apiResponse.last,
                                valid = apiResponse.valid
                            )

                            if (apiResponse.valid) {
                                offers.add(response)
                            }

                            isLastPage = apiResponse.last
                            currentPage++

                        } catch (e: Exception) {
                            println("Error parsing JSON response: ${e.message}")
                        }
                    } else {
                        println("Empty response body")
                    }
                } else {
                    println("HTTP Error: ${response.code} - ${response.message}")
                }
            }
        }
        return offers
    }

    fun responsesToOffers(responses: List<VerbynDichResponse>): List<InternetOffer.VerbynDichOffer> {


        return responses.mapNotNull { response ->
            if (!response.valid) return@mapNotNull null

            val product = response.product

            val priceConnectionTypeSpeed = priceConnectionTypeSpeedRegex.find(response.description)
            if (priceConnectionTypeSpeed == null || priceConnectionTypeSpeed.groupValues.size < 3) return@mapNotNull null // this information is absolutely necessary to show an offer. even if other info were there, we would not have enough info to show the necessary info

            val priceInEur = priceConnectionTypeSpeed.groupValues[1].toDoubleOrNull() ?: return@mapNotNull null

            val connectionType =
                ConnectionType.fromString(priceConnectionTypeSpeed.groupValues[2]) ?: return@mapNotNull null

            val speed = priceConnectionTypeSpeed.groupValues[3].toIntOrNull() ?: return@mapNotNull null

            val limitFrom = limitFromRegex.find(response.description)?.groupValues?.get(1)?.toIntOrNull()

            val priceAfter2YearsInEur =
                priceAfter2YearsRegex.find(response.description)?.groupValues?.get(1)?.toDoubleOrNull()
            val minOrderValueInEur = minOrderValRegex.find(response.description)?.groupValues?.get(1)?.toDoubleOrNull()
            val maxDiscountInEur = maxDiscountRegex.find(response.description)?.groupValues?.get(1)?.toDoubleOrNull()
            val oneTimeDiscountInEur =
                oneTimeDiscountValue.find(response.description)?.groupValues?.get(1)?.toDoubleOrNull()

            val maxAge = maxAgeRegex.find(response.description)?.groupValues?.get(1)?.toIntOrNull()

            val discountUntilYear2 =
                discountUntil24thMonthRegex.find(response.description)?.groupValues?.get(1)?.toIntOrNull()

            val tv = tvRegex.find(response.description)?.groupValues?.get(1)

            val minContractDurationInMonths =
                minContractDurationRegex.find(response.description)?.groupValues?.get(1)?.toIntOrNull()

            InternetOffer.VerbynDichOffer(
                product = product,
                price = priceInEur,
                connectionType = connectionType,
                speed = speed,
                limitFrom = limitFrom,
                monthlyPriceAfter24Months = priceAfter2YearsInEur,
                minOrderValue = minOrderValueInEur,
                maxDiscount = maxDiscountInEur,
                discountUntil24thMonth = discountUntilYear2,
                oneTimeDiscountValue = oneTimeDiscountInEur,
                maxAge = maxAge,
                tv = tv,
                minContactDuration = minContractDurationInMonths,
            )
        }
    }

    // regexes
    val limitFromRegex = """Ab (\d+)GB pro Monat wird die Geschwindigkeit gedrosselt""".toRegex()
    val priceAfter2YearsRegex = """ Ab dem 24. Monat beträgt der monatliche Preis (\d+)€""".toRegex()
    val minOrderValRegex = """Der Mindestbestellwert beträgt (\d+)€""".toRegex()
    val maxDiscountRegex = """Der maximale Rabatt beträgt (\d+)€""".toRegex()
    val maxAgeRegex = """Dieses Angebot ist nur für Personen unter (\d+) Jahren verfügbar""".toRegex()
    val priceConnectionTypeSpeedRegex =
        """Für nur (\d+)€ im Monat erhalten Sie eine ([A-Za-z]+)-Verbindung mit einer Geschwindigkeit von (\d+) Mbit/s""".toRegex()
    val discountUntil24thMonthRegex =
        """Mit diesem Angebot erhalten Sie einen Rabatt von (\d+)% auf Ihre monatliche Rechnung bis zum 24. Monat""".toRegex()
    val oneTimeDiscountValue =
        """Mit diesem Angebot erhalten Sie einen einmaligen Rabatt von (\d+)€ auf Ihre monatliche Rechnung""".toRegex()
    val tvRegex = """Zusätzlich sind folgende Fernsehsender enthalten ([^.]+)""".toRegex()
    val minContractDurationRegex =
        """Bitte beachten Sie, dass die Mindestvertragslaufzeit (\d+) ,Monate beträgt""".toRegex()


}