package co.veyra.bank.wallet

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import co.veyra.bank.R
import co.veyra.bank.databinding.ActivityActivationOtpBinding
import co.veyra.wallet.sdk.ActivationCodeReason
import co.veyra.wallet.sdk.ActivationMethods
import co.veyra.wallet.sdk.VeyraWalletSdk
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class ActivationOtpActivity : AppCompatActivity() {

    private lateinit var binding: ActivityActivationOtpBinding
    private var tokenId: String = ""
    private var method: String = ActivationMethods.MASKED_MOBILE_PHONE
    private var tokenUniqueReference: String? = null
    private var activationMethodContact: String? = null
    private var countDownTimer: CountDownTimer? = null
    private var expirationTimeMs: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityActivationOtpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tokenId = intent.getStringExtra(EXTRA_TOKEN_ID) ?: ""
        method = intent.getStringExtra(EXTRA_METHOD) ?: ActivationMethods.MASKED_MOBILE_PHONE
        tokenUniqueReference = intent.getStringExtra(EXTRA_TOKEN_UNIQUE_REFERENCE)
        activationMethodContact = intent.getStringExtra(EXTRA_ACTIVATION_METHOD_CONTACT)
        val codeAlreadyRequested = intent.getBooleanExtra(EXTRA_CODE_ALREADY_REQUESTED, false)
        val preloadedExpiry = intent.getStringExtra(EXTRA_EXPIRATION_DATE_TIME)
        val serverMessage = intent.getStringExtra(EXTRA_SERVER_MESSAGE)

        val contact = activationMethodContact?.takeIf { it.isNotBlank() }
        val defaultMessage = when {
            contact != null && method == ActivationMethods.MASKED_EMAIL ->
                getString(R.string.activation_otp_message_email_contact, contact)
            contact != null ->
                getString(R.string.activation_otp_message_phone_contact, contact)
            method == ActivationMethods.MASKED_EMAIL ->
                getString(R.string.activation_otp_message_email)
            else ->
                getString(R.string.activation_otp_message_phone)
        }
        binding.messageTextView.text = defaultMessage
        binding.otpEditText.setTextColor(ContextCompat.getColor(this, android.R.color.white))

        ViewCompat.setOnApplyWindowInsetsListener(binding.scrollView) { v, insets ->
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            v.setPadding(0, 0, 0, maxOf(imeBottom, navBottom))
            insets
        }

        binding.submitButton.setOnClickListener { submitOtp() }
        binding.backButton.setOnClickListener { finish() }
        binding.resendButton.setOnClickListener { resendOtp() }

        if (codeAlreadyRequested) {
            binding.resendButton.visibility = View.GONE
            preloadedExpiry?.let { startCountdown(it) }
        } else {
            requestActivationCode()
        }
    }

    private fun requestActivationCode() {
        val ref = tokenUniqueReference
        if (ref.isNullOrBlank()) return
        val sdk = VeyraWalletSdk.getInstance() ?: return
        binding.resendButton.visibility = View.GONE
        sdk.tokenisationService.requestActivationCode(
            tokenUniqueReference = ref,
            selectedActivationMethod = method,
            preferredActivationChannel = method,
            reasonCode = ActivationCodeReason.ADD_CARD
        ) { result ->
            result.fold(
                onSuccess = { response ->
                    if (response.status?.uppercase() == "FAILURE") {
                        Toast.makeText(this, response.message ?: getString(R.string.error_occurred), Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, getString(R.string.activation_code_sent), Toast.LENGTH_SHORT).show()
                        response.expirationDateTime?.let { startCountdown(it) }
                    }
                },
                onFailure = { e ->
                    Toast.makeText(this, getString(R.string.error_occurred) + ": " + (e.message ?: ""), Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    private fun resendOtp() {
        countDownTimer?.cancel()
        binding.countdownTextView.visibility = View.GONE
        binding.resendButton.visibility = View.GONE
        requestActivationCode()
    }

    private fun startCountdown(expirationDateTime: String) {
        countDownTimer?.cancel()
        val parsedMs = parseExpirationTime(expirationDateTime)
        if (parsedMs == null) {
            // Unparseable expiry — skip the countdown but don't show "expired"
            return
        }
        expirationTimeMs = parsedMs
        val now = System.currentTimeMillis()
        val remainingMs = expirationTimeMs - now
        if (remainingMs <= 0L) {
            showExpired()
            return
        }
        binding.countdownTextView.visibility = View.VISIBLE
        binding.resendButton.visibility = View.GONE
        countDownTimer = object : CountDownTimer(remainingMs, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished)
                val seconds = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) % 60
                binding.countdownTextView.text = getString(
                    R.string.activation_code_expires_in,
                    String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
                )
            }
            override fun onFinish() {
                showExpired()
            }
        }.start()
    }

    private fun showExpired() {
        countDownTimer = null
        binding.countdownTextView.visibility = View.GONE
        binding.resendButton.visibility = View.VISIBLE
        binding.resendButton.text = getString(R.string.activation_resend_otp)
        Toast.makeText(this, getString(R.string.activation_code_expired), Toast.LENGTH_SHORT).show()
    }

    private fun parseExpirationTime(expirationDateTime: String): Long? {
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd'T'HH:mm:ss"
        )
        for (pattern in formats) {
            try {
                val f = SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                val date = f.parse(expirationDateTime.trim())
                if (date != null) return date.time
            } catch (_: Exception) { }
        }
        return null
    }

    private fun submitOtp() {
        val code = binding.otpEditText.text?.toString()?.trim()
        if (code.isNullOrBlank()) {
            binding.otpInputLayout.error = getString(R.string.activation_otp_hint)
            return
        }
        val ref = tokenUniqueReference
        if (ref.isNullOrBlank()) {
            Toast.makeText(this, getString(R.string.error_occurred), Toast.LENGTH_SHORT).show()
            return
        }
        binding.otpInputLayout.error = null
        binding.submitButton.isEnabled = false
        val sdk = VeyraWalletSdk.getInstance()
        if (sdk == null) {
            binding.submitButton.isEnabled = true
            Toast.makeText(this, getString(R.string.error_occurred), Toast.LENGTH_SHORT).show()
            return
        }
        sdk.tokenisationService.activate(
            tokenUniqueReference = ref,
            activationCode = code
        ) { result ->
            binding.submitButton.isEnabled = true
            result.fold(
                onSuccess = { response ->
                    if (response.status?.uppercase() == "SUCCESS") {
                        Toast.makeText(this, response.message ?: getString(R.string.activation_code_submitted), Toast.LENGTH_SHORT).show()
                        startActivity(
                            Intent(this, PayActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        )
                    } else {
                        Toast.makeText(this, response.message ?: getString(R.string.error_occurred), Toast.LENGTH_LONG).show()
                    }
                },
                onFailure = { e ->
                    Toast.makeText(this, getString(R.string.error_occurred) + ": " + (e.message ?: ""), Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }

    companion object {
        private const val EXTRA_TOKEN_ID = "token_id"
        private const val EXTRA_METHOD = "method"
        private const val EXTRA_TOKEN_UNIQUE_REFERENCE = "token_unique_reference"
        private const val EXTRA_CODE_ALREADY_REQUESTED = "code_already_requested"
        private const val EXTRA_EXPIRATION_DATE_TIME = "expiration_date_time"
        private const val EXTRA_SERVER_MESSAGE = "server_message"
        private const val EXTRA_ACTIVATION_METHOD_CONTACT = "activation_method_contact"

        fun intent(
            context: Context,
            tokenId: String,
            method: String,
            tokenUniqueReference: String? = null,
            codeAlreadyRequested: Boolean = false,
            expirationDateTime: String? = null,
            serverMessage: String? = null,
            activationMethodContact: String? = null
        ): Intent {
            return Intent(context, ActivationOtpActivity::class.java).apply {
                putExtra(EXTRA_TOKEN_ID, tokenId)
                putExtra(EXTRA_METHOD, method)
                putExtra(EXTRA_TOKEN_UNIQUE_REFERENCE, tokenUniqueReference)
                putExtra(EXTRA_CODE_ALREADY_REQUESTED, codeAlreadyRequested)
                putExtra(EXTRA_EXPIRATION_DATE_TIME, expirationDateTime)
                putExtra(EXTRA_SERVER_MESSAGE, serverMessage)
                putExtra(EXTRA_ACTIVATION_METHOD_CONTACT, activationMethodContact)
            }
        }
    }
}
