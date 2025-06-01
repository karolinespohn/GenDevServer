package org.gendev25.project.dataRetrieval

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.example.Address
import org.example.ConnectionType
import org.example.InternetOffer
import java.io.IOException

internal class WebWunderRetriever() : DataRetriever() {
    private val serviceUrl = "https://webwunder.gendev7.check24.fun:443/endpunkte/soap/ws"


    suspend fun getOffers(
        address: Address,
        installation: Boolean,
        connectionType: ConnectionType
    ): List<InternetOffer.WebWunderOffer> {
        return withContext(Dispatchers.IO) {
            val response = sendRequest(
                installation = installation,
                connectionType = connectionType,
                address = address
            )
            response?.let { parseResponse(it, installation) } ?: emptyList()
        }
    }

    private fun parseResponse(xmlResponse: String, installationService: Boolean): List<InternetOffer.WebWunderOffer> {
        return try {
            val products = extractProducts(xmlResponse)
            products.mapNotNull { product ->
                parseProduct(productXml = product, installationService = installationService)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun extractValue(xml: String, tagName: String): String? {
        val regex = Regex("<$tagName>(.*?)</$tagName>")
        return regex.find(xml)?.groupValues?.get(1)
    }

    private fun sendRequest(
        installation: Boolean,
        connectionType: ConnectionType,
        address: Address,
    ): String? {
        val apiKey = System.getenv("WEB_WUNDER_KEY")

        if (apiKey.isNullOrBlank()) {
            return null
        }

        val request = Request.Builder()
            .url(serviceUrl)
            .post(
                createSoapRequest(installation, connectionType, address)
                    .toRequestBody("text/xml".toMediaType())
            )
            .addHeader("X-Api-Key", apiKey)
            .addHeader("Content-Type", "text/xml; charset=utf-8")
            .addHeader("SOAPAction", "")
            .build()

        return try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.string()
            } else {
                println("Error: ${response.code} - ${response.message}")
                null
            }
        } catch (e: IOException) {
            println("Network error: ${e.message}")
            null
        }
    }

    private fun extractProducts(xml: String): List<String> {
        val products = mutableListOf<String>()
        val regex = Regex("<ns2:products>(.*?)</ns2:products>", RegexOption.DOT_MATCHES_ALL)

        regex.findAll(xml).forEach { match ->
            products.add(match.groupValues[1])
        }

        return products
    }

    private fun parseProduct(productXml: String, installationService: Boolean): InternetOffer.WebWunderOffer? {
        return try {
            val productId = extractValue(productXml, "ns2:productId")?.toIntOrNull() ?: return null
            val speed = extractValue(productXml, "ns2:speed")?.toIntOrNull() ?: return null
            val monthlyCostInCent = extractValue(productXml, "ns2:monthlyCostInCent")?.toIntOrNull() ?: return null
            val monthlyCostFrom25th =
                extractValue(productXml, "ns2:monthlyCostInCentFrom25thMonth")?.toIntOrNull() ?: return null
            val contractDuration =
                extractValue(productXml, "ns2:contractDurationInMonths")?.toIntOrNull() ?: return null
            val connectionType = extractValue(productXml, "ns2:connectionType") ?: return null

            val discountInCent = extractValue(productXml, "ns2:discountInCent")?.toIntOrNull() ?: 0

            InternetOffer.WebWunderOffer(
                id = productId.toString(),
                providerName = "WebWunder",
                speed = speed,
                monthlyPrice = monthlyCostInCent / 100.0,
                monthlyPriceAfter24Months = monthlyCostFrom25th / 100.0,
                contractDuration = contractDuration,
                connectionType = ConnectionType.fromString(connectionType),
                discountAvailable = discountInCent > 0,
                discountAmount = discountInCent / 100.0,
                installationService = installationService,
            )
        } catch (e: Exception) {
            println("Error parsing product: ${e.message}")
            null
        }
    }


    fun createSoapRequest(
        installation: Boolean,
        connectionType: ConnectionType,
        address: Address,
    ): String {
        val connectionEnum = connectionType.name

        return """
            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                              xmlns:gs="http://webwunder.gendev7.check24.fun/offerservice">
               <soapenv:Header/>
               <soapenv:Body>
                  <gs:legacyGetInternetOffers>
                     <gs:input>
                        <gs:installation>$installation</gs:installation>
                        <gs:connectionEnum>$connectionEnum</gs:connectionEnum>
                        <gs:address>
                           <gs:street>${address.street.escapeXml()}</gs:street>
                           <gs:houseNumber>${address.number.escapeXml()}</gs:houseNumber>
                           <gs:city>${address.city.escapeXml()}</gs:city>
                           <gs:plz>${address.zip.escapeXml()}</gs:plz>
                           <gs:countryCode>${address.country.iso}</gs:countryCode>
                        </gs:address>
                     </gs:input>
                  </gs:legacyGetInternetOffers>
               </soapenv:Body>
            </soapenv:Envelope>
        """.trimIndent()
    }

    private fun String.escapeXml(): String = this
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
