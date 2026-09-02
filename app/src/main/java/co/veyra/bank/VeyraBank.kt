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
        VeyraSdk.initialize(activity, VeyraSdkConfig(softposConfig(activity), walletConfig(activity)))

    /**
     * Whether a merchant is registered — the SDK's init-free read, so Home can gate the
     * "Get paid" card without initialising the SoftPOS SDK (initialising binds the SDK's
     * reader lifecycle to the initialising screen; only payment screens should do that).
     */
    fun isMerchantRegistered(context: Context): Boolean = VeyraSoftPOSSdk.isMerchantRegistered(context)

    fun softposConfig(context: Context): VeyraSoftPosSdkConfig =
        VeyraSoftPosSdkConfig.builder(
            co.veyra.common.Environment.TEST,
            // The provider credential the gateway resolves the acquirer id and MCC from —
            // the same identifier the wallet config carries.
            paymentAppProviderId = requireNotNull(context.getString(R.string.payment_app_provider_id).takeIf { it.isNotBlank() }) {
                "veyra.paymentAppProviderId must be set in veyra.properties (copy veyra.properties.example)"
            },
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
            clientId = context.getString(R.string.client_id).takeIf { it.isNotBlank() },
            clientSecret = context.getString(R.string.client_secret).takeIf { it.isNotBlank() }
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
