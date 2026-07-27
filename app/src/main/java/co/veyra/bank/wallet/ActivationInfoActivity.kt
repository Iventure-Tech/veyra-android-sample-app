package co.veyra.bank.wallet

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import co.veyra.bank.R
import co.veyra.bank.databinding.ActivityActivationInfoBinding
import co.veyra.wallet.sdk.VeyraWalletSdk

class ActivationInfoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityActivationInfoBinding
    private var tokenUniqueReference: String? = null
    private var shouldPoll = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityActivationInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val type = intent.getStringExtra(EXTRA_INFO_TYPE) ?: TYPE_CALL_CENTER
        val contact = intent.getStringExtra(EXTRA_CONTACT) ?: ""
        tokenUniqueReference = intent.getStringExtra(EXTRA_TOKEN_UNIQUE_REFERENCE)

        when (type) {
            TYPE_CALL_CENTER -> { setupCallCenter(contact, automated = false); shouldPoll = true }
            TYPE_AUTOMATED_CALL_CENTER -> { setupCallCenter(contact, automated = true); shouldPoll = true }
            TYPE_WEBSITE -> { setupWebsite(contact); shouldPoll = true }
            TYPE_ISSUER_APP -> { setupIssuerApp(contact); shouldPoll = true }
            else -> {
                binding.titleTextView.text = getString(R.string.activation_required)
                binding.messageTextView.text = getString(R.string.activation_website_message)
            }
        }

        binding.backButton.setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        val tur = tokenUniqueReference
        val sdk = VeyraWalletSdk.getInstance()
        if (!shouldPoll || tur.isNullOrBlank() || sdk == null) return

        binding.statusView.visibility = View.VISIBLE
        binding.pollingSpinner.visibility = View.VISIBLE
        binding.statusTextView.text = getString(R.string.activation_polling_checking)

        sdk.tokenisationService.observeActivation(
            tokenUniqueReference = tur,
            onActivated = {
                binding.pollingSpinner.visibility = View.GONE
                binding.statusTextView.text = getString(R.string.activation_polling_active)
                Handler(Looper.getMainLooper()).postDelayed({
                    startActivity(
                        Intent(this, PayActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    )
                }, 2000L)
            },
            onTimeout = {
                binding.pollingSpinner.visibility = View.GONE
                binding.statusTextView.text = getString(R.string.activation_polling_timeout)
            }
        )
    }

    override fun onPause() {
        super.onPause()
        tokenUniqueReference?.let {
            VeyraWalletSdk.getInstance()?.tokenisationService?.pauseActivationObserver(it)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tokenUniqueReference?.let {
            VeyraWalletSdk.getInstance()?.tokenisationService?.stopActivationObserver(it)
        }
    }

    private fun setupCallCenter(contact: String, automated: Boolean) {
        binding.titleTextView.text = getString(
            if (automated) R.string.activation_automated_call_title else R.string.activation_call_center_title
        )
        binding.messageTextView.text = if (contact.isNotBlank()) {
            getString(
                if (automated) R.string.activation_automated_call_contact else R.string.activation_call_center_contact,
                contact
            )
        } else {
            getString(R.string.activation_call_center_message)
        }
        if (contact.isNotBlank()) {
            binding.actionButton.visibility = View.VISIBLE
            binding.actionButton.text = getString(R.string.activation_action_call)
            binding.actionButton.setOnClickListener { dialPhone(contact) }
        }
    }

    private fun setupWebsite(contact: String) {
        binding.titleTextView.text = getString(R.string.activation_website_title)
        val displayUrl = if (contact.isNotBlank()) Uri.parse(contact).host ?: contact else ""
        binding.messageTextView.text = if (displayUrl.isNotBlank()) {
            getString(R.string.activation_website_contact, displayUrl)
        } else {
            getString(R.string.activation_website_message)
        }
        if (contact.isNotBlank()) {
            binding.actionButton.visibility = View.VISIBLE
            binding.actionButton.text = getString(R.string.activation_action_open_website)
            binding.actionButton.setOnClickListener { openUrl(contact) }
        }
    }

    private fun setupIssuerApp(contact: String) {
        binding.titleTextView.text = getString(R.string.activation_issuer_app_title)
        binding.messageTextView.text = getString(R.string.activation_issuer_app_message)
        if (contact.isNotBlank()) {
            binding.actionButton.visibility = View.VISIBLE
            binding.actionButton.text = getString(R.string.activation_action_open_app)
            binding.actionButton.setOnClickListener { openApp(contact) }
        }
    }

    private fun dialPhone(phoneNumber: String) {
        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(phoneNumber)}")))
    }

    private fun openUrl(url: String) {
        val uri = if (url.startsWith("http://") || url.startsWith("https://")) Uri.parse(url)
                  else Uri.parse("https://$url")
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    }

    private fun openApp(packageName: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            startActivity(launchIntent)
        } else {
            Toast.makeText(this, getString(R.string.activation_app_not_installed), Toast.LENGTH_SHORT).show()
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
            } catch (e: ActivityNotFoundException) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
            }
        }
    }

    companion object {
        private const val EXTRA_TOKEN_ID = "token_id"
        private const val EXTRA_INFO_TYPE = "info_type"
        private const val EXTRA_CONTACT = "contact"
        private const val EXTRA_TOKEN_UNIQUE_REFERENCE = "token_unique_reference"
        private const val TYPE_CALL_CENTER = "call_center"
        private const val TYPE_AUTOMATED_CALL_CENTER = "automated_call_center"
        private const val TYPE_WEBSITE = "website"
        private const val TYPE_ISSUER_APP = "issuer_app"

        fun intentCallCenter(context: Context, tokenId: String, contact: String = "", tokenUniqueReference: String? = null): Intent =
            Intent(context, ActivationInfoActivity::class.java).apply {
                putExtra(EXTRA_TOKEN_ID, tokenId)
                putExtra(EXTRA_INFO_TYPE, TYPE_CALL_CENTER)
                putExtra(EXTRA_CONTACT, contact)
                putExtra(EXTRA_TOKEN_UNIQUE_REFERENCE, tokenUniqueReference)
            }

        fun intentAutomatedCallCenter(context: Context, tokenId: String, contact: String = "", tokenUniqueReference: String? = null): Intent =
            Intent(context, ActivationInfoActivity::class.java).apply {
                putExtra(EXTRA_TOKEN_ID, tokenId)
                putExtra(EXTRA_INFO_TYPE, TYPE_AUTOMATED_CALL_CENTER)
                putExtra(EXTRA_CONTACT, contact)
                putExtra(EXTRA_TOKEN_UNIQUE_REFERENCE, tokenUniqueReference)
            }

        fun intentWebsite(context: Context, tokenId: String, contact: String = "", tokenUniqueReference: String? = null): Intent =
            Intent(context, ActivationInfoActivity::class.java).apply {
                putExtra(EXTRA_TOKEN_ID, tokenId)
                putExtra(EXTRA_INFO_TYPE, TYPE_WEBSITE)
                putExtra(EXTRA_CONTACT, contact)
                putExtra(EXTRA_TOKEN_UNIQUE_REFERENCE, tokenUniqueReference)
            }

        fun intentIssuerApp(context: Context, tokenId: String, contact: String = "", tokenUniqueReference: String? = null): Intent =
            Intent(context, ActivationInfoActivity::class.java).apply {
                putExtra(EXTRA_TOKEN_ID, tokenId)
                putExtra(EXTRA_INFO_TYPE, TYPE_ISSUER_APP)
                putExtra(EXTRA_CONTACT, contact)
                putExtra(EXTRA_TOKEN_UNIQUE_REFERENCE, tokenUniqueReference)
            }

        fun intentGeneric(context: Context, tokenId: String, method: String, contact: String = ""): Intent =
            Intent(context, ActivationInfoActivity::class.java).apply {
                putExtra(EXTRA_TOKEN_ID, tokenId)
                putExtra(EXTRA_INFO_TYPE, method)
                putExtra(EXTRA_CONTACT, contact)
            }
    }
}
