package org.gendev25.project.dataRetrieval

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.Request
import java.io.IOException
import com.opencsv.CSVReader
import org.example.Address
import org.example.ConnectionType
import org.example.InternetOffer
import java.io.StringReader

internal class ByteMeRetriever() : DataRetriever() {

    suspend fun getOffers(
        address: Address,
    ): List<InternetOffer.ByteMeOffer> {
        return withContext(Dispatchers.IO) {
            val csv = sendRequest(address)
                ?.lineSequence()
                ?.distinct() // remove duplicates from CSV
                ?.filter { it.isNotBlank() }
                ?.drop(1) // remove header
                ?.joinToString("\n")
            if (csv.isNullOrBlank()) {
                return@withContext emptyList()
            }
            parseCSV(csv)
        }
    }

    private fun parseCSV(csv: String): List<InternetOffer.ByteMeOffer> {
        val reader = CSVReader(StringReader(csv))

        val offers = reader.map { csvValues ->
            try {
                val productId = csvValues[0].toIntOrNull() ?: throw IllegalArgumentException("Invalid product ID")
                val providerName = csvValues[1]
                val speed = csvValues[2].toIntOrNull() ?: throw IllegalArgumentException("Invalid speed")
                val monthlyCostInCent = csvValues[3].toIntOrNull() ?: throw IllegalArgumentException("Invalid price")
                val afterTwoYearsMonthlyCost =
                    csvValues[4].toIntOrNull() ?: throw IllegalArgumentException("Invalid monthly cost after 2 years")
                val contractDuration =
                    csvValues[5].toIntOrNull() ?: throw IllegalArgumentException("Invalid contract Duration")
                val connectionType = ConnectionType.fromString(csvValues[6])
                val installationService = csvValues[7].toBooleanStrictOrNull()
                    ?: throw IllegalArgumentException("Invalid installation services value")
                val tv = csvValues[8]
                val limitFrom = csvValues[9].toIntOrNull() ?: Int.MAX_VALUE
                val maxAge = csvValues[10].toIntOrNull()
                val voucherType = csvValues[11]
                val voucherValue = csvValues[12].toIntOrNull() ?: 0

                InternetOffer.ByteMeOffer(
                    productId = productId,
                    providerName = providerName,
                    speed = speed,
                    monthlyPrice = monthlyCostInCent / 100.0,
                    monthlyPriceAfter24Months = afterTwoYearsMonthlyCost / 100.0,
                    durationInMonths = contractDuration,
                    connectionType = connectionType,
                    installationService = installationService,
                    tv = tv,
                    limitFrom = limitFrom,
                    maxAge = maxAge,
                    voucherType = voucherType,
                    voucherValue = voucherValue
                )

            } catch (_: IllegalArgumentException) {
                println("Error parsing line: ${csvValues.joinToString(",")}")
                return emptyList()
            }
        }

        return offers
    }

    private fun sendRequest(address: Address): String? {
        val apiKey = System.getenv("BYTE_ME_KEY")

        if (apiKey.isNullOrBlank()) {
            println("BYTEME_API_KEY not found in .env file")
            return null
        }

        val urlBuilder = HttpUrl.Builder()
            .scheme("https")
            .host("byteme.gendev7.check24.fun")
            .addPathSegments("app/api/products/data")
            .addQueryParameter("street", address.street)
            .addQueryParameter("houseNumber", address.number)
            .addQueryParameter("city", address.city)
            .addQueryParameter("plz", address.zip)
            .build()

        val request = Request.Builder()
            .url(urlBuilder)
            .get() // GET request instead of POST
            .addHeader("X-Api-Key", apiKey)
            .build()

        return try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.string()
            } else {
                println("ByteMe API Error: ${response.code} - ${response.message}")
                null
            }
        } catch (e: IOException) {
            println("ByteMe API Network error: ${e.message}")
            null
        }
    }
}
