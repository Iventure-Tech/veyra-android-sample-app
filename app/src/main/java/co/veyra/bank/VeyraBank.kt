package co.veyra.bank

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import co.veyra.sdk.VeyraSdk
import co.veyra.sdk.VeyraSdkConfig
import co.veyra.softpos.payment.sdk.VeyraSoftPOSSdk
import co.veyra.softpos.payment.sdk.VeyraSoftPosSdkConfig
import co.veyra.wallet.sdk.VeyraWalletSdkConfig

/**
 * App-level SDK bootstrap: builds both SDK configs from res/values/config.xml and
 * initialises the [VeyraSdk] facade (idempotent — call from any entry activity).
 */
object VeyraBank {

    fun ensureInitialized(activity: AppCompatActivity): VeyraSdk =
        VeyraSdk.initialize(activity, customerId(activity), VeyraSdkConfig(softposConfig(activity), walletConfig(activity)), provider())

    /**
     * How both SDKs reach Veyra: one provider, chosen in the git-ignored veyra.properties. Pass the
     * same one to every SDK `initialize`.
     */
    fun provider(): co.veyra.common.providers.VeyraProvider = co.veyra.bank.provider.AppProvider.provider()

    // ── The app's own login session ───────────────────────────────────────────────
    // Who is logged in is the banking app's to remember, never the SDK's: the SDKs are told on
    // every launch (initialize) and forget on signOut. This demo "logs in" one of two demo
    // customers from res/values/sample_data.xml.

    private const val SESSION_PREFS = "VeyraBankDemoSession"
    private const val KEY_CUSTOMER = "customer"
    private const val KEY_SIGNED_IN = "signed_in"

    private fun session(context: Context) =
        context.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)

    private fun demoCustomers(context: Context): List<String> =
        context.resources.getStringArray(R.array.sample_customer_ids).toList()

    /** The customer the app has logged in (the last one, while signed out). */
    fun customerId(context: Context): String =
        session(context).getString(KEY_CUSTOMER, null) ?: demoCustomers(context).first()

    fun isSignedIn(context: Context): Boolean = session(context).getBoolean(KEY_SIGNED_IN, true)

    /** Log [customerId] in and tell the SDKs: they open that customer's cards and merchant. */
    fun signIn(activity: AppCompatActivity, customerId: String = customerId(activity)): VeyraSdk {
        session(activity).edit().putString(KEY_CUSTOMER, customerId).putBoolean(KEY_SIGNED_IN, true).apply()
        return ensureInitialized(activity)
    }

    /** Log in the other demo customer: the SDKs stop the first customer's work and switch. */
    fun switchCustomer(activity: AppCompatActivity): VeyraSdk {
        val customers = demoCustomers(activity)
        val next = customers[(customers.indexOf(customerId(activity)) + 1) % customers.size]
        return signIn(activity, next)
    }

    /** Log out: the SDKs stop everything for this customer; their data stays on the device. */
    fun signOut(context: Context) {
        VeyraSdk.signOut()
        session(context).edit().putBoolean(KEY_SIGNED_IN, false).apply()
    }

    /**
     * Whether a merchant is registered — the SDK's init-free read, so Home can gate the
     * "Get paid" card without initialising the SoftPOS SDK (initialising binds the SDK's
     * reader lifecycle to the initialising screen; only payment screens should do that).
     */
    fun isMerchantRegistered(context: Context): Boolean =
        VeyraSoftPOSSdk.isMerchantRegistered(context, customerId(context))

    fun softposConfig(context: Context): VeyraSoftPosSdkConfig =
        VeyraSoftPosSdkConfig.builder(
            co.veyra.common.Environment.TEST,
            // The provider credential the gateway resolves the acquirer id and MCC from —
            // the same identifier the wallet config carries.
            paymentAppProviderId = requireNotNull(context.getString(R.string.payment_app_provider_id).takeIf { it.isNotBlank() }) {
                "veyra.paymentAppProviderId must be set in veyra.properties (copy veyra.properties.example)"
            }
        )
            .enableNfc(true)
            .build()

    fun walletConfig(context: Context): VeyraWalletSdkConfig {
        val paymentAppProviderId = requireNotNull(context.getString(R.string.payment_app_provider_id).takeIf { it.isNotBlank() }) {
            "veyra.paymentAppProviderId must be set in veyra.properties (copy veyra.properties.example)"
        }
        val tokenRequestorId = requireNotNull(context.getString(R.string.token_requestor_id).takeIf { it.isNotBlank() }) {
            "veyra.tokenRequestorId must be set in veyra.properties (copy veyra.properties.example)"
        }
        val resources = context.resources
        return VeyraWalletSdkConfig.builder(
            co.veyra.common.Environment.TEST,
            paymentAppProviderId,
            tokenRequestorId,
        )
            .appVersion(context.getString(R.string.app_version).takeIf { it.isNotBlank() })
            .walletProviderTokenizationRecommendationStandardVersion(
                context.getString(R.string.wallet_provider_tokenization_recommendation_standard_version).takeIf { it.isNotBlank() }
            )
            .allowedAcquirerIds(resources.getStringArray(R.array.allowed_acquirer_ids).filter { it.isNotBlank() })
            .allowedMerchantIds(resources.getStringArray(R.array.allowed_merchant_ids).filter { it.isNotBlank() })
            .enableNfc(true)
            .build()
    }
}
