package co.veyra.bank.wallet

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import co.veyra.bank.R
import co.veyra.bank.databinding.ActivityActivationCodeRequestBinding
import co.veyra.wallet.sdk.ActivationCodeReason
import co.veyra.wallet.sdk.ActivationMethods
import co.veyra.wallet.sdk.VeyraWalletSdk

class ActivationCodeRequestActivity : AppCompatActivity() {

    private lateinit var binding: ActivityActivationCodeRequestBinding
    private var tokenId: String = ""
    private var method: String = ""
    private var tokenUniqueReference: String? = null
    private var contact: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityActivationCodeRequestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tokenId = intent.getStringExtra(EXTRA_TOKEN_ID) ?: ""
        method = intent.getStringExtra(EXTRA_METHOD) ?: ""
        tokenUniqueReference = intent.getStringExtra(EXTRA_TOKEN_UNIQUE_REFERENCE)
        contact = intent.getStringExtra(EXTRA_CONTACT)

        val contactValue = contact?.takeIf { it.isNotBlank() }
        binding.messageTextView.text = when {
            contactValue != null && method == ActivationMethods.MASKED_EMAIL ->
                getString(R.string.activation_request_code_email_message, contactValue)
            contactValue != null ->
                getString(R.string.activation_request_code_phone_message, contactValue)
            method == ActivationMethods.MASKED_EMAIL ->
                getString(R.string.activation_request_code_email_fallback)
            else ->
                getString(R.string.activation_request_code_phone_fallback)
        }

        binding.sendCodeButton.setOnClickListener { requestCode() }
        binding.backButton.setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        setLoading(false)
    }

    private fun requestCode() {
        val ref = tokenUniqueReference
        val sdk = VeyraWalletSdk.getInstance()
        if (ref.isNullOrBlank() || sdk == null) {
            navigateToOtp(expirationDateTime = null)
            return
        }

        setLoading(true)

        sdk.tokenisationService.requestActivationCode(
            tokenUniqueReference = ref,
            selectedActivationMethod = method,
            preferredActivationChannel = method,
            reasonCode = ActivationCodeReason.ADD_CARD
        ) { result ->
            result.fold(
                onSuccess = { response ->
                    if (response.status?.uppercase() == "FAILURE") {
                        setLoading(false)
                        showError(response.message ?: getString(R.string.error_occurred))
                    } else {
                        navigateToOtp(expirationDateTime = response.expirationDateTime)
                    }
                },
                onFailure = { error ->
                    setLoading(false)
                    showError(error.message ?: getString(R.string.error_occurred))
                }
            )
        }
    }

    private fun navigateToOtp(expirationDateTime: String?) {
        startActivity(
            ActivationOtpActivity.intent(
                context = this,
                tokenId = tokenId,
                method = method,
                tokenUniqueReference = tokenUniqueReference,
                codeAlreadyRequested = true,
                expirationDateTime = expirationDateTime,
                activationMethodContact = contact
            )
        )
    }

    private fun setLoading(loading: Boolean) {
        binding.loadingView.visibility = if (loading) View.VISIBLE else View.GONE
        binding.sendCodeButton.isEnabled = !loading
        binding.backButton.isEnabled = !loading
        binding.errorTextView.visibility = View.GONE
    }

    private fun showError(message: String) {
        binding.errorTextView.text = message
        binding.errorTextView.visibility = View.VISIBLE
    }

    companion object {
        private const val EXTRA_TOKEN_ID = "token_id"
        private const val EXTRA_METHOD = "method"
        private const val EXTRA_TOKEN_UNIQUE_REFERENCE = "token_unique_reference"
        private const val EXTRA_CONTACT = "contact"

        fun intent(
            context: Context,
            tokenId: String,
            method: String,
            tokenUniqueReference: String? = null,
            contact: String? = null
        ): Intent {
            return Intent(context, ActivationCodeRequestActivity::class.java).apply {
                putExtra(EXTRA_TOKEN_ID, tokenId)
                putExtra(EXTRA_METHOD, method)
                putExtra(EXTRA_TOKEN_UNIQUE_REFERENCE, tokenUniqueReference)
                putExtra(EXTRA_CONTACT, contact)
            }
        }
    }
}
