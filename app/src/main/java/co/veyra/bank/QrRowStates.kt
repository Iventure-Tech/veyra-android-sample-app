package co.veyra.bank

/**
 * What the stored row behind a displayed QR tells its screen to show. The QR screens never ask the
 * gateway themselves: the SDK does, as often as the gateway tells it to, and writes each answer onto
 * the row. The screens re-read the row from the phone's own store and render one of these states.
 */
object QrRowStates {

    /** The merchant's QR (customer scans it), read from `softpos.getTransaction(txRef)`. */
    enum class MerchantQr {
        /** Nobody has claimed the QR: keep it on screen. */
        WAITING,
        /** A wallet has claimed the QR and its payment has no outcome yet: take the QR down, wait. */
        CLAIMED_PENDING,
        /** The customer was debited and the credit is queued. Not final: take the QR down, wait. */
        ACCEPTED,
        /** The payment ended (approved, declined, failed): show the result. */
        FINAL,
        /** The row is gone: the QR expired unpaid, so no payment was recorded. */
        GONE,
    }

    /** The customer's QR (merchant scans it), read from `wallet.getTransactions(...)` by its hash. */
    enum class ShownQr {
        /** No merchant has charged the QR yet: keep it on screen. */
        WAITING,
        /** A merchant charged the QR and the payment has no outcome yet: take the QR down, wait. */
        CHARGED_PENDING,
        /** Debited, credit queued. Not final: take the QR down, wait. */
        ACCEPTED,
        /** The payment ended: show the result. */
        FINAL,
    }

    private const val ACCEPTED = "ACCEPTED"
    private val MERCHANT_FINAL = setOf("APPROVED", "DECLINED", "FAILED", "RETRY")
    private val WALLET_FINAL = setOf("APPROVED", "DECLINED", "FAILED")

    /**
     * @param rowExists false when the SDK holds no row for the QR's reference
     * @param status the row's `status`
     * @param isQrClaimed the row's `isQrClaimed`: true once a wallet has claimed the QR, false when
     *   a claimed QR was released again, null while nobody has claimed it
     */
    fun merchantQr(rowExists: Boolean, status: String?, isQrClaimed: Boolean?): MerchantQr {
        if (!rowExists) return MerchantQr.GONE
        val stated = status?.trim()?.uppercase()
        return when {
            stated == ACCEPTED -> MerchantQr.ACCEPTED
            stated in MERCHANT_FINAL -> MerchantQr.FINAL
            // The status is still open: the flag is what tells a claimed QR from an unscanned one.
            isQrClaimed == true -> MerchantQr.CLAIMED_PENDING
            else -> MerchantQr.WAITING
        }
    }

    /**
     * @param status the row's `authorizationStatus`
     * @param isCharged the row's `isCharged`: true once the gateway holds the merchant's charge
     */
    fun shownQr(status: String?, isCharged: Boolean?): ShownQr {
        val stated = status?.trim()?.uppercase()
        return when {
            stated == ACCEPTED -> ShownQr.ACCEPTED
            stated in WALLET_FINAL -> ShownQr.FINAL
            // The status is still open: the flag is what tells a charged QR from an unscanned one.
            isCharged == true -> ShownQr.CHARGED_PENDING
            else -> ShownQr.WAITING
        }
    }
}
