package co.veyra.bank

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import co.veyra.sdk.VeyraSdk
import co.veyra.sdk.VeyraSdkConfig
import co.veyra.softpos.payment.sdk.VeyraSoftPosSdkConfig
import co.veyra.wallet.sdk.VeyraWalletSdkConfig

/**
 * App-level SDK bootstrap: builds both SDK configs from res/values/config.xml and
 * initialises the [VeyraSdk] facade (idempotent — call from any entry activity).
 */
object VeyraBank {

    fun ensureInitialized(activity: AppCompatActivity): VeyraSdk =
        VeyraSdk.initialize(activity, VeyraSdkConfig(softposConfig(activity), walletConfig(activity)))

    /**
     * Whether a merchant is registered — read from the app's own [MerchantPrefs] record so
     * Home can gate the "Get paid" card without initialising the SoftPOS SDK (initialising
     * would bind the SDK's reader lifecycle to Home; only payment screens should do that).
     * The record is re-synced from `MerchantService.isRegistered()` whenever the Get-paid
     * screen opens.
     */
    fun isMerchantRegistered(context: Context): Boolean = MerchantPrefs.isRegistered(context)

    fun softposConfig(context: Context): VeyraSoftPosSdkConfig =
        VeyraSoftPosSdkConfig.builder(
            co.veyra.common.Environment.TEST,
            clientId = context.getString(R.string.client_id),
            clientSecret = context.getString(R.string.client_secret)
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
            allowedCountryCodes = resources.getStringArray(R.array.allowed_country_codes).filter { it.isNotBlank() },
            clientId = context.getString(R.string.client_id).takeIf { it.isNotBlank() },
            clientSecret = context.getString(R.string.client_secret).takeIf { it.isNotBlank() }
        )
            .appVersion(context.getString(R.string.app_version).takeIf { it.isNotBlank() })
            .walletProviderTokenizationRecommendationStandardVersion(
                context.getString(R.string.wallet_provider_tokenization_recommendation_standard_version).takeIf { it.isNotBlank() }
            )
            .allowedAcquirerIds(resources.getStringArray(R.array.allowed_acquirer_ids).filter { it.isNotBlank() })
            .allowedMerchantIds(resources.getStringArray(R.array.allowed_merchant_ids).filter { it.isNotBlank() })
            .allowedMccs(resources.getStringArray(R.array.allowed_mccs).filter { it.isNotBlank() })
            .enableNfc(true)
            .build()
    }
}
