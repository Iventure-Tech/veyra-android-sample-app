package co.veyra.bank

import android.content.Context

/**
 * App-local record of the merchant registration outcome.
 *
 * Home must stay NFC-inert, so it cannot initialise the SoftPOS SDK just to ask whether a
 * merchant is registered (initialising binds the SDK's reader lifecycle to the initialising
 * screen). Instead the app keeps its own note of the registration state: written when a
 * registration succeeds and re-synced from `MerchantService` every time the Get-paid screen
 * opens — the SDK remains the source of truth.
 */
object MerchantPrefs {
    private const val PREFS = "merchant_registration"
    private const val KEY_REGISTERED = "registered"
    private const val KEY_BUSINESS = "business"

    fun isRegistered(context: Context): Boolean =
        prefs(context).getBoolean(KEY_REGISTERED, false)

    /** Whether the registered merchant is a business (has a CAC number). */
    fun isBusiness(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BUSINESS, false)

    fun update(context: Context, registered: Boolean, business: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_REGISTERED, registered)
            .putBoolean(KEY_BUSINESS, business)
            .apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
