package co.veyra.bank

import android.util.Log
import co.veyra.sdk.VeyraSdk

/**
 * App-scope payment events: the SDKs report a payment that left PENDING (and, once before that, one
 * that became ACCEPTED) and a credit confirmation, possibly long after the screen that started the
 * payment is gone — or for a payment from an earlier session. Register once, where the app tells
 * the SDKs who is logged in, so the listeners outlive every screen.
 *
 * Each event is logged as one line under the tag [TAG] (`adb logcat -s VeyraEvents`). The lines
 * carry identifiers, status and amount only — never an account number or a person's name.
 *
 * Each observer holds one listener (last registration wins), and the SDKs drop them on sign-out and
 * on a customer switch, so [register] runs after every sign-in. A screen that needs one of these
 * events itself must not register the same observer: it would replace this one.
 */
object PaymentEventLog {

    const val TAG = "VeyraEvents"

    fun register(sdk: VeyraSdk) {
        // SoftPOS: a sale left PENDING (APPROVED / DECLINED / FAILED), or became ACCEPTED (not final).
        // Get paid registers the SoftPOS credit-confirmation observer itself, for its result page.
        sdk.softpos.observeTransactionResolved { event ->
            Log.i(
                TAG,
                "softpos transactionResolved reference=${event.merchantTransactionReference} " +
                    "status=${event.status} responseCode=${event.responseCode} reason=${event.reason}",
            )
        }
        // Wallet: a payment left PENDING, or became ACCEPTED (not final) — on every rail.
        sdk.wallet.observeTransactionResolved { event ->
            Log.i(
                TAG,
                "wallet transactionResolved transactionHash=${event.transactionHash} " +
                    "tokenUniqueReference=${event.tokenUniqueReference} status=${event.status} " +
                    "responseCode=${event.responseCode} reason=${event.reason} amountMinorUnits=${event.amountMinorUnits}",
            )
        }
        // Wallet: the merchant's bank received the funds (RECEIVED), or 30 days passed (UNABLE_TO_CONFIRM).
        sdk.wallet.observeCreditConfirmation { event ->
            Log.i(
                TAG,
                "wallet creditConfirmation transactionHash=${event.transactionHash} " +
                    "tokenUniqueReference=${event.tokenUniqueReference} creditTransactionId=${event.creditTransactionId} " +
                    "status=${event.status} amountMinorUnits=${event.amountMinorUnits} bankReference=${event.bankReference} " +
                    "creditedAt=${event.creditedAt}",
            )
        }
    }
}
