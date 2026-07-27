package co.veyra.bank.wallet

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import co.veyra.bank.R
import co.veyra.bank.databinding.ActivityAccountEligibilityBinding
import co.veyra.wallet.sdk.AccountNumberSource
import co.veyra.wallet.sdk.ActivationMethods
import co.veyra.wallet.sdk.VeyraWalletSdk
import co.veyra.wallet.sdk.TokenisationResponse
import co.veyra.wallet.sdk.TokenizationRecommendation
import co.veyra.wallet.sdk.TokenizationRecommendationReason
import co.veyra.wallet.sdk.TokenizationRequestParams
import co.veyra.wallet.sdk.TrustScore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class AccountEligibilityActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAccountEligibilityBinding
    private lateinit var accountNumber: String
    private lateinit var institutionCode: String
    private lateinit var bankName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAccountEligibilityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        accountNumber = intent.getStringExtra(EXTRA_ACCOUNT_NUMBER) ?: run { finish(); return }
        institutionCode = intent.getStringExtra(EXTRA_INSTITUTION_CODE) ?: run { finish(); return }
        bankName = intent.getStringExtra(EXTRA_BANK_NAME) ?: ""

        bindAccountDetails()

        binding.addToWalletButton.setOnClickListener { startTokenization() }
        binding.cancelTextView.setOnClickListener { finish() }
    }

    private fun bindAccountDetails() {
        val masked = "•••• •••• " + accountNumber.takeLast(4)
        binding.accountNumberTextView.text = masked
        binding.bankNameTextView.text = bankName
    }

    private fun startTokenization() {
        val sdk = VeyraWalletSdk.getInstance() ?: run {
            showError("SDK not initialized")
            return
        }
        binding.addToWalletButton.isEnabled = false
        binding.addToWalletButton.text = getString(R.string.adding_to_wallet)
        binding.errorTextView.visibility = View.GONE

        val params = buildTokenizationParams()
        sdk.tokenisationService.digitizeAccount(params) { response ->
            lifecycleScope.launch(Dispatchers.Main) {
                handleDigitizeResponse(response)
            }
        }
    }

    private fun handleDigitizeResponse(response: TokenisationResponse) {
        if (response.isSuccess) {
            when (response.responseCode?.uppercase()) {
                ActivationMethods.ResponseCode.APPROVED -> {
                    Toast.makeText(this, getString(R.string.tokenization_request_success), Toast.LENGTH_SHORT).show()
                    finish()
                }
                ActivationMethods.ResponseCode.APPROVE_REQUIRE_AUTH -> {
                    val methods = response.activationMethods.orEmpty()
                    val tokenUniqueRef = response.tokenUniqueReference
                    if (tokenUniqueRef != null && methods.isNotEmpty()) {
                        startActivity(
                            ActivationMethodSelectionActivity.intent(this, tokenUniqueRef, methods, tokenUniqueRef)
                        )
                        finish()
                    } else {
                        Toast.makeText(this, getString(R.string.tokenization_request_success), Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
                else -> {
                    Toast.makeText(
                        this,
                        response.message ?: getString(R.string.tokenization_request_success),
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }
            }
        } else {
            val errorMessage = response.error?.message
                ?: response.message
                ?: getString(R.string.error_occurred)
            showError(errorMessage)
            binding.addToWalletButton.isEnabled = true
            binding.addToWalletButton.text = getString(R.string.add_to_wallet)
        }
    }

    private fun buildTokenizationParams(): TokenizationRequestParams {
        // All account-holder data comes from the shared sample source (res/values/sample_data.xml)
        // — the same identity the SoftPOS merchant flow uses. accountNumber/institutionCode are
        // passed in from the previous screen (already sourced from SampleData on entry).
        val sample = co.veyra.bank.SampleData.active(this)
        return TokenizationRequestParams.builder(
            accountNumber = accountNumber,
            institutionCode = institutionCode,
            accountHolderName = sample.accountName,
            walletProviderTokenizationRecommendation = TokenizationRecommendation.APPROVE,
            consumerIdentifier = UUID.randomUUID().toString(),
            bvn = sample.bvn,
            accountHolderAddress = sample.fullAddress,
            mobileNumber = sample.mobileNumber,
            walletAccountId = sample.walletAccountId,
            emailAddress = sample.emailAddress,
        )
            .clientRequestId(UUID.randomUUID().toString())
            .accountNumberSource(AccountNumberSource.MANUAL)
            .walletProviderDeviceScore(TrustScore.TRUSTED)
            .walletProviderAccountScore(TrustScore.HIGHLY_TRUSTED)
            .walletProviderTokenizationRecommendationReasons(listOf(TokenizationRecommendationReason.GOOD_ACTIVITY_HISTORY))
            .build()
    }

    private fun showError(message: String) {
        binding.errorTextView.text = message
        binding.errorTextView.visibility = View.VISIBLE
    }

    companion object {
        private const val EXTRA_ACCOUNT_NUMBER = "extra_account_number"
        private const val EXTRA_INSTITUTION_CODE = "extra_institution_code"
        private const val EXTRA_BANK_NAME = "extra_bank_name"

        fun intent(context: Context, accountNumber: String, institutionCode: String, bankName: String): Intent =
            Intent(context, AccountEligibilityActivity::class.java).apply {
                putExtra(EXTRA_ACCOUNT_NUMBER, accountNumber)
                putExtra(EXTRA_INSTITUTION_CODE, institutionCode)
                putExtra(EXTRA_BANK_NAME, bankName)
            }
    }
}
