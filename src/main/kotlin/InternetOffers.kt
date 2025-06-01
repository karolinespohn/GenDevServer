package org.example

import kotlinx.serialization.Serializable
import org.gendev25.project.dataRetrieval.PingPerfectRetriever.PricingDetails
import org.gendev25.project.dataRetrieval.PingPerfectRetriever.ProductInfo


@Serializable
internal sealed class InternetOffer(
) {
    @Serializable
    internal data class WebWunderOffer(
        val id: String,
        val providerName: String,
        val speed: Int,
        val monthlyPrice: Double,
        val monthlyPriceAfter24Months: Double,
        val contractDuration: Int,
        val connectionType: ConnectionType,
        val discountAvailable: Boolean,
        val discountAmount: Double,
        val installationService: Boolean,
    ) : InternetOffer()

    @Serializable
    internal data class ByteMeOffer(
        val productId: Int,
        val providerName: String,
        val speed: Int,
        val monthlyPrice: Double,
        val monthlyPriceAfter24Months: Double,
        val durationInMonths: Int,
        val connectionType: ConnectionType,
        val installationService: Boolean,
        val tv: String,
        val limitFrom: Int,
        val maxAge: Int?,
        val voucherType: String,
        val voucherValue: Int,
    ) : InternetOffer()

    @Serializable
    data class PingPerfectOffer(
        val providerName: String,
        val productInfo: ProductInfo,
        val pricingDetails: PricingDetails
    ) : InternetOffer()


    @Serializable
    data class ServusSpeedOffer(
        val providerName: String,
        val speed: Int,
        val contractDurationInMonths: Int,
        val connectionType: ConnectionType,
        val tv: String?,
        val limitFrom: Int?,
        val maxAge: Int?,
        val monthlyCostInCent: Int,
        val installationService: Boolean,
        val discount: Double? = null,

        ) : InternetOffer()

    @Serializable
    data class VerbynDichOffer(
        val product: String,
        val price: Double,
        val connectionType: ConnectionType,
        val speed: Int,
        val limitFrom: Int?,
        val monthlyPriceAfter24Months: Double?,
        val minOrderValue: Double?,
        val maxDiscount: Double?,
        val discountUntil24thMonth: Int?,
        val oneTimeDiscountValue: Double?,
        val maxAge: Int?,
        val tv: String?,
        val minContactDuration: Int??
    ) : InternetOffer()
}


internal fun turnToPresentableOffer(offer: InternetOffer): PresentableOffer {
    return when (offer) {
        is InternetOffer.ByteMeOffer -> {
            val discountInfo = if (offer.voucherType.isEmpty() || offer.voucherValue == 0) {
                null
            } else {
                val absoluteDiscount = if (offer.voucherType == "absolute") (offer.voucherValue / 100.0) else null
                val relativeDiscount = if (offer.voucherType == "relative") offer.voucherValue else null
                DiscountInfo(
                    absoluteDiscount = absoluteDiscount,
                    relativeDiscount = relativeDiscount,
                    maxAmount = null
                )
            }


            PresentableOffer(
                nameInfo = NameInfo(company = Company.BYTEME, offerName = offer.providerName),
                speed = offer.speed,
                priceInfo = PriceInfo(
                    price = offer.monthlyPrice,
                    monthlyPriceAfter2Years = offer.monthlyPriceAfter24Months
                ),
                connectionType = offer.connectionType,
                durationInMonths = offer.durationInMonths,
                tv = offer.tv,
                installationService = offer.installationService,
                disclaimerInfo = DisclaimerInfo(limitFrom = offer.limitFrom, maxAge = offer.maxAge),
                discountInfo = discountInfo,
            )
        }

        is InternetOffer.ServusSpeedOffer -> {
            val discountInfo = DiscountInfo(
                absoluteDiscount = offer.discount,
                relativeDiscount = null,
                maxAmount = null
            )

            PresentableOffer(
                nameInfo = NameInfo(company = Company.SERVUSSPEED, offerName = offer.providerName, id = null),
                speed = offer.speed,
                priceInfo = PriceInfo(
                    price = offer.monthlyCostInCent / 100.0,
                    monthlyPriceAfter2Years = null
                ),
                connectionType = offer.connectionType,
                durationInMonths = offer.contractDurationInMonths,
                tv = offer.tv,
                installationService = offer.installationService,
                disclaimerInfo = DisclaimerInfo(limitFrom = offer.limitFrom, maxAge = offer.maxAge),
                discountInfo = discountInfo,
            )
        }

        is InternetOffer.WebWunderOffer -> {

            val discountInfo = if (offer.discountAvailable) {
                val info = DiscountInfo(
                    absoluteDiscount = offer.discountAmount,
                    relativeDiscount = null
                )
                info
            } else {
                null
            }

            PresentableOffer(
                nameInfo = NameInfo(company = Company.WEBWUNDER, offerName = offer.providerName, id = offer.id),
                speed = offer.speed,
                priceInfo = PriceInfo(
                    price = offer.monthlyPrice,
                    monthlyPriceAfter2Years = offer.monthlyPriceAfter24Months
                ),
                connectionType = offer.connectionType,
                durationInMonths = offer.contractDuration,
                installationService = offer.installationService,
                discountInfo = discountInfo
            )
        }

        is InternetOffer.VerbynDichOffer -> {

            val discountInfo = DiscountInfo(
                absoluteDiscount = offer.oneTimeDiscountValue,
                relativeDiscount = offer.discountUntil24thMonth,
                maxAmount = offer.maxDiscount,
                howOften = if (offer.discountUntil24thMonth != null) 24 else 1,
            )

            PresentableOffer(
                nameInfo = NameInfo(company = Company.VERBYNDICH, offerName = offer.product),
                speed = offer.speed,
                priceInfo = PriceInfo(
                    price = offer.price,
                    monthlyPriceAfter2Years = offer.monthlyPriceAfter24Months
                ),
                connectionType = offer.connectionType,
                durationInMonths = offer.minContactDuration,
                tv = offer.tv,
                installationService = null,
                disclaimerInfo = DisclaimerInfo(
                    limitFrom = offer.limitFrom,
                    maxAge = offer.maxAge
                ),
                discountInfo = discountInfo
            )
        }

        // Keep PingPerfect as-is since it has discountInfo = null
        is InternetOffer.PingPerfectOffer -> {
            PresentableOffer(
                nameInfo = NameInfo(company = Company.PINGPERFECT, offerName = offer.providerName),
                speed = offer.productInfo.speed,
                priceInfo = PriceInfo(
                    price = offer.pricingDetails.monthlyCostInCent / 100.0,
                    monthlyPriceAfter2Years = null
                ),
                connectionType = offer.productInfo.connectionType,
                durationInMonths = offer.productInfo.contractDurationInMonths,
                tv = offer.productInfo.tv,
                installationService = null,
                disclaimerInfo = DisclaimerInfo(
                    limitFrom = offer.productInfo.limitFrom,
                    maxAge = offer.productInfo.maxAge
                ),
                discountInfo = null,
            )
        }
    }
}

data class PresentableOffer(
    val nameInfo: NameInfo,
    val speed: Int,
    val priceInfo: PriceInfo,
    val connectionType: ConnectionType,

    val durationInMonths: Int? = null,
    val tv: String? = null,
    val installationService: Boolean? = null,
    val disclaimerInfo: DisclaimerInfo? = null,
    val discountInfo: DiscountInfo? = null,
)

data class PriceInfo(
    val price: Double,
    val monthlyPriceAfter2Years: Double? = null,
)

data class NameInfo(
    val company: Company,
    val offerName: String,
    val id: String? = null,
)

data class DiscountInfo(
    val absoluteDiscount: Double?,
    val relativeDiscount: Int?,
    val maxAmount: Double? = null,
    val howOften: Int = 1,
)


data class DisclaimerInfo(
    val limitFrom: Int? = null,
    val maxAge: Int? = null,
    val minOrderValue: Double? = null,
)

data class Address(
    val street: String,
    val number: String, // string, not int for addresses such as "Musterstrasse 2a"
    val city: String,
    val zip: String,
    val country: Country
)

enum class Country(val iso: String, val presentableName : String) {
    GERMANY(iso = "DE", presentableName = "Germany"),
    AUSTRIA(iso = "AT", presentableName = "Austria"),
    SWITZERLAND(iso = "CH", presentableName = "Switzerland")
}

enum class Company {
    BYTEME, PINGPERFECT, SERVUSSPEED, VERBYNDICH, WEBWUNDER
}

enum class ConnectionType {
    DSL, CABLE, FIBER, MOBILE, UNKNOWN;

    companion object {
        fun fromString(name: String): ConnectionType =
            entries.firstOrNull { it.name.lowercase() == name.lowercase().trim() } ?: UNKNOWN
    }
}
