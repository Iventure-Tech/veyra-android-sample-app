package co.veyra.bank.wallet

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import co.veyra.bank.CancelAction
import co.veyra.bank.CardRequestIds
import co.veyra.bank.R
import co.veyra.bank.cancelAction
import co.veyra.bank.cancelRefusedTooLate
import co.veyra.bank.outcomeText
import co.veyra.bank.shouldDeactivateLostDigitise
import co.veyra.common.outcome.OutcomeReason
import co.veyra.common.outcome.ResponseStatus
import co.veyra.bank.databinding.ActivityAccountEligibilityBinding
import co.veyra.wallet.sdk.AccountNumberSource
import co.veyra.wallet.sdk.ActivationMethods
import co.veyra.wallet.sdk.VeyraWalletSdk
import co.veyra.wallet.sdk.DigitiseParams
import co.veyra.wallet.sdk.DigitiseResult
import co.veyra.wallet.sdk.TokenizationRecommendation
import co.veyra.wallet.sdk.TokenizationRecommendationReason
import co.veyra.wallet.sdk.ActivationMethodInfo
import co.veyra.wallet.sdk.TrustScore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class AccountEligibilityActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAccountEligibilityBinding
    private lateinit var accountNumber: String
    private lateinit var institutionCode: String
    private lateinit var bankName: String
    private lateinit var customerId: String
    private lateinit var accountName: String
    /** Null when the user left the optional BVN field empty: the digitise then carries no BVN. */
    private var bvn: String? = null
    private lateinit var email: String

    /**
     * The `clientRequestId` of the add-card attempt in flight, or null when none is. A fresh one per
     * attempt; it is also how Cancel names the digitisation to the SDK.
     */
    private var digitiseRequestId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAccountEligibilityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        accountNumber = intent.getStringExtra(EXTRA_ACCOUNT_NUMBER) ?: run { finish(); return }
        institutionCode = intent.getStringExtra(EXTRA_INSTITUTION_CODE) ?: run { finish(); return }
        bankName = intent.getStringExtra(EXTRA_BANK_NAME) ?: ""
        customerId = intent.getStringExtra(EXTRA_CUSTOMER_ID) ?: run { finish(); return }
        accountName = intent.getStringExtra(EXTRA_ACCOUNT_NAME) ?: run { finish(); return }
        bvn = intent.getStringExtra(EXTRA_BVN)?.takeIf { it.isNotBlank() }
        email = intent.getStringExtra(EXTRA_EMAIL) ?: run { finish(); return }

        bindAccountDetails()

        binding.addToWalletButton.setOnClickListener { startTokenization() }
        binding.cancelTextView.setOnClickListener { cancel() }
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

        val requestId = UUID.randomUUID().toString()
        digitiseRequestId = requestId
        val params = buildTokenizationParams(requestId)
        sdk.digitise(params) { response ->
            lifecycleScope.launch(Dispatchers.Main) {
                digitiseRequestId = null
                handleDigitizeResponse(response)
            }
        }
    }

    /**
     * Cancel: with no add-card in flight, just leave. Otherwise cancel the digitisation by its
     * `clientRequestId` — possible only while the request is still on the phone:
     *  - `CANCELLED`: the digitise callback receives `NOT_SENT` / `CANCELLED` and leaves the screen.
     *  - `TOO_LATE_TO_CANCEL_REQUEST_ALREADY_SENT`: the server may be creating the token, so say it
     *    can no longer be cancelled and keep waiting for the result (a token it creates is stored).
     *  - nothing in progress for that ID: leave.
     */
    private fun cancel() {
        val requestId = digitiseRequestId ?: return finish()
        val sdk = VeyraWalletSdk.getInstance() ?: return finish()
        val answer = sdk.cancelDigitise(requestId)
        when (cancelAction(answer)) {
            CancelAction.AWAIT_CANCELLED_OUTCOME -> Unit
            CancelAction.KEEP_WAITING -> if (cancelRefusedTooLate(answer)) {
                Toast.makeText(this, getString(R.string.cancel_too_late), Toast.LENGTH_SHORT).show()
            }
            CancelAction.LEAVE -> finish()
        }
    }

    private fun handleDigitizeResponse(response: DigitiseResult) {
        // Cancelled before the request left the phone: nothing was created — leave.
        if (response.responseStatusReason == OutcomeReason.CANCELLED) {
            finish()
            return
        }
        // Digitise is never PENDING: APPROVED (the response code says whether the card still needs
        // activating), or DECLINED / FAILED / NOT_SENT with the reason — a new attempt starts a new
        // digitisation.
        if (response.responseStatus == ResponseStatus.APPROVED) {
            // "Remove card" later names this add-card attempt by its id.
            CardRequestIds.record(this, response)
            when (response.responseCode?.uppercase()) {
                ActivationMethods.ResponseCode.APPROVED -> {
                    Toast.makeText(this, getString(R.string.tokenization_request_success), Toast.LENGTH_SHORT).show()
                    finish()
                }
                ActivationMethods.ResponseCode.APPROVE_REQUIRE_AUTH -> {
                    val methods = response.activationMethods.map { ActivationMethodInfo(it.medium, it.contact.orEmpty()) }
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
            val outcome = outcomeText(response.responseStatusReason, response.message, getString(R.string.error_occurred))
            showError(outcome)
            binding.addToWalletButton.isEnabled = true
            binding.addToWalletButton.text = getString(R.string.add_to_wallet)
            // Sent, but no usable answer: the server may have created a card this phone never
            // received. Switch it off by this attempt's id; the next "Add" is a new attempt.
            if (shouldDeactivateLostDigitise(response)) deactivateLostDigitise(response.clientRequestId!!, outcome)
        }
    }

    private fun deactivateLostDigitise(clientRequestId: String, outcome: String) {
        val sdk = VeyraWalletSdk.getInstance() ?: return
        sdk.deactivateDigitise(clientRequestId) { r ->
            val answer = listOfNotNull(r.responseStatus, r.responseStatusReason).joinToString(" / ")
            showError(outcome + "\n" + getString(R.string.digitise_cleanup_result, answer))
        }
    }

    private fun buildTokenizationParams(clientRequestId: String): DigitiseParams {
        // Account number, bank, customer id, account name, BVN and email are what the user
        // entered on the previous screen. The customer id is also the consumer identifier: the
        // one id this app knows the customer by; the email is also the wallet account id. The
        // remaining contact details come from the shared sample source (res/values/sample_data.xml).
        val sample = co.veyra.bank.SampleData.active(this)
        return DigitiseParams(
            accountNumber = accountNumber,
            institutionCode = institutionCode,
            accountHolderName = accountName,
            walletAccountId = email,
            emailAddress = email,
            recommendation = TokenizationRecommendation.APPROVE,
            consumerIdentifier = customerId,
            bvn = bvn,
            accountHolderAddress = sample.fullAddress,
            mobileNumber = sample.mobileNumber,
            clientRequestId = clientRequestId,
            accountNumberSource = AccountNumberSource.MANUAL,
            deviceScore = TrustScore.TRUSTED,
            accountScore = TrustScore.HIGHLY_TRUSTED,
            recommendationReasons = listOf(TokenizationRecommendationReason.GOOD_ACTIVITY_HISTORY),
            bankName = bankName.takeIf { it.isNotBlank() },
        )
    }

    private fun showError(message: String) {
        binding.errorTextView.text = message
        binding.errorTextView.visibility = View.VISIBLE
    }

    companion object {
        private const val EXTRA_ACCOUNT_NUMBER = "extra_account_number"
        private const val EXTRA_INSTITUTION_CODE = "extra_institution_code"
        private const val EXTRA_BANK_NAME = "extra_bank_name"
        private const val EXTRA_CUSTOMER_ID = "extra_customer_id"
        private const val EXTRA_ACCOUNT_NAME = "extra_account_name"
        private const val EXTRA_BVN = "extra_bvn"
        private const val EXTRA_EMAIL = "extra_email"

        fun intent(
            context: Context,
            accountNumber: String,
            institutionCode: String,
            bankName: String,
            customerId: String,
            accountName: String,
            bvn: String?,
            email: String,
        ): Intent =
            Intent(context, AccountEligibilityActivity::class.java).apply {
                putExtra(EXTRA_ACCOUNT_NUMBER, accountNumber)
                putExtra(EXTRA_INSTITUTION_CODE, institutionCode)
                putExtra(EXTRA_BANK_NAME, bankName)
                putExtra(EXTRA_CUSTOMER_ID, customerId)
                putExtra(EXTRA_ACCOUNT_NAME, accountName)
                bvn?.let { putExtra(EXTRA_BVN, it) }
                putExtra(EXTRA_EMAIL, email)
            }
    }
}
