package co.veyra.bank

import android.content.Context
import co.veyra.common.outcome.ResponseStatus
import co.veyra.wallet.sdk.DigitiseResult

/**
 * The `clientRequestId` of the add-card (digitise) that created each card, by the card's token
 * unique reference. A card does not carry it, so the app keeps it: "Remove card" names the add-card
 * attempt with `deactivateDigitise(clientRequestId)`, and the SDK removes the card from this phone
 * when the server says `APPROVED`.
 *
 * A card with no recorded id (added before the app kept them) is removed with
 * `deactivateToken(tokenUniqueReference)` instead.
 */
object CardRequestIds {

    private const val PREFS = "VeyraBankCardRequestIds"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Remembers the attempt's id when the digitise added a card. */
    fun record(context: Context, result: DigitiseResult) {
        val (tokenUniqueReference, clientRequestId) = cardRequestId(result) ?: return
        prefs(context).edit().putString(tokenUniqueReference, clientRequestId).apply()
    }

    /** The id of the add-card that created [tokenUniqueReference], or null when none was recorded. */
    fun get(context: Context, tokenUniqueReference: String): String? =
        prefs(context).getString(tokenUniqueReference, null)

    /** Forgets the card's id once the card is removed. */
    fun forget(context: Context, tokenUniqueReference: String) {
        prefs(context).edit().remove(tokenUniqueReference).apply()
    }
}

/**
 * The card a digitise added and the id it was sent with, or null when it added no card. `APPROVED`
 * covers both response codes: a card that still needs activating is on the phone too.
 */
fun cardRequestId(result: DigitiseResult): Pair<String, String>? {
    if (result.responseStatus != ResponseStatus.APPROVED) return null
    val tokenUniqueReference = result.tokenUniqueReference?.takeIf { it.isNotBlank() } ?: return null
    val clientRequestId = result.clientRequestId?.takeIf { it.isNotBlank() } ?: return null
    return tokenUniqueReference to clientRequestId
}
