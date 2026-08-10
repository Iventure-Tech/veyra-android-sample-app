package co.veyra.bank.wallet

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import co.veyra.bank.R
import co.veyra.bank.databinding.ActivityTokenizationRequestBinding
import co.veyra.wallet.sdk.AccountNumberSource
import co.veyra.wallet.sdk.Bank
import co.veyra.wallet.sdk.VeyraWalletSdk
import co.veyra.wallet.sdk.VerifyAccountParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TokenizationRequestActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityTokenizationRequestBinding
    private var banks: List<Bank> = emptyList()
    private var selectedBank: Bank? = null
    private var lastFetchedAccountNumber: String? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTokenizationRequestBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupViews()
    }
    
    private fun setupViews() {
        // Set text colors to ensure visibility - bright white
        binding.accountNumberEditText.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        
        // Get drawables for focus states
        val normalBackground = ContextCompat.getDrawable(this, R.drawable.input_background)
        val focusedBackground = ContextCompat.getDrawable(this, R.drawable.input_background_focused)
        
        // Add focus listeners for better UX
        binding.accountNumberEditText.setOnFocusChangeListener { _, hasFocus ->
            binding.accountNumberEditText.background = if (hasFocus) focusedBackground else normalBackground
        }
        
        // Add text watcher to validate account number and fetch banks
        binding.accountNumberEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Hide error when user starts typing
                binding.errorTextView.visibility = View.GONE
                binding.accountNumberInputLayout.error = null
                val accountNumber = s?.toString()?.trim() ?: ""
                if (accountNumber.length < 10) {
                    binding.bankInputLayout.visibility = View.GONE
                    selectedBank = null
                    banks = emptyList()
                    lastFetchedAccountNumber = null
                } else if (accountNumber.length == 10 && accountNumber.all { it.isDigit() }) {
                    if (accountNumber != lastFetchedAccountNumber) {
                        fetchBanks(accountNumber)
                    }
                }
            }
            
            override fun afterTextChanged(s: Editable?) {}
        })
        
        binding.submitButton.setOnClickListener {
            handleSubmit()
        }

        binding.tryAgainButton.setOnClickListener {
            binding.errorTextView.visibility = View.GONE
            binding.accountNumberInputLayout.error = null
            binding.tryAgainButton.visibility = View.GONE
            binding.submitButton.isEnabled = true
            binding.submitButton.text = getString(R.string.check_eligibility)
        }
        
        // Show dropdown when user taps the bank field (threshold prevents auto-show with empty text)
        binding.bankAutoComplete.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && banks.isNotEmpty()) {
                binding.bankAutoComplete.showDropDown()
            }
        }
        binding.bankAutoComplete.setOnClickListener {
            if (banks.isNotEmpty()) {
                binding.bankAutoComplete.showDropDown()
            }

        }
        
        // Pre-populate the account number to tokenise from the shared sample source — the
        // SAME account the SoftPOS merchant flow receives into (res/values/sample_data.xml).
        binding.accountNumberEditText.setText(co.veyra.bank.SampleData.active(this).accountNumber)
    }
    
    private fun fetchBanks(accountNumber: String?) {
        val sdk = VeyraWalletSdk.getInstance() ?: return
        lastFetchedAccountNumber = accountNumber
        binding.bankInputLayout.visibility = View.VISIBLE
        binding.bankAutoComplete.setText(getString(R.string.loading_banks), false)
        selectedBank = null
        sdk.tokenisationService.getBanks(accountNumber) { result ->
            lifecycleScope.launch(Dispatchers.Main) {
                result.fold(
                    onSuccess = { bankList ->
                        banks = bankList
                        val displayItems = bankList.map { it.name } + getString(R.string.cant_find_my_bank)
                        val adapter = ArrayAdapter(
                            this@TokenizationRequestActivity,
                            R.layout.item_bank_dropdown,
                            android.R.id.text1,
                            displayItems
                        )
                        binding.bankAutoComplete.setAdapter(adapter)
                        binding.bankAutoComplete.threshold = 1
                        binding.bankAutoComplete.setDropDownBackgroundResource(R.drawable.dropdown_background)
                        binding.bankAutoComplete.setOnItemClickListener { _, _, position, _ ->
                            if (position == banks.size) {
                                // "Can't find my bank" clicked - fetch full bank list
                                fetchBanks(null)
                            } else {
                                selectedBank = banks.getOrNull(position)
                            }
                        }
                        if (bankList.isEmpty()) {
                            binding.bankAutoComplete.setText(getString(R.string.no_banks_found), false)
                        } else {
                            binding.bankAutoComplete.setText("", false)
                            binding.bankInputLayout.hint = getString(R.string.bank_hint)
                            if (bankList.size == 1 && accountNumber != null) {
                                selectedBank = bankList.first()
                                binding.bankAutoComplete.setText(selectedBank!!.name, false)
                                // Bank is pre-selected — replace adapter so the dropdown only offers
                                // "Can't find my bank" and doesn't echo the already-visible bank name.
                                val changeAdapter = ArrayAdapter(
                                    this@TokenizationRequestActivity,
                                    R.layout.item_bank_dropdown,
                                    android.R.id.text1,
                                    listOf(getString(R.string.cant_find_my_bank))
                                )
                                binding.bankAutoComplete.setAdapter(changeAdapter)
                                binding.bankAutoComplete.setOnItemClickListener { _, _, _, _ ->
                                    fetchBanks(null)
                                }
                            } else if (accountNumber == null) {
                                binding.bankAutoComplete.showDropDown()
                            }
                        }
                    },
                    onFailure = { error ->
                        // An offline device says so, instead of claiming the account has no banks.
                        // The wallet SDK's failure idiom is a stable code prefix on the message
                        // (the same shape as ONLINE_REQUIRED:) — see the developer guide.
                        val offline = error.message?.contains("NO_NETWORK_CONNECTION") == true
                        binding.bankAutoComplete.setText(
                            getString(if (offline) R.string.no_network_connection else R.string.no_banks_found),
                            false,
                        )
                        banks = emptyList()
                    }
                )
            }
        }
    }
    
    private fun handleSubmit() {
        val accountNumber = binding.accountNumberEditText.text?.toString()?.trim() ?: ""

        if (!isValidAccountNumber(accountNumber)) {
            showError(getString(R.string.invalid_account_number))
            return
        }

        val bank = selectedBank
        if (bank == null) {
            showError(getString(R.string.select_bank_first))
            return
        }

        val sdk = VeyraWalletSdk.getInstance()
        if (sdk == null) {
            showError("SDK not initialized")
            return
        }

        binding.submitButton.isEnabled = false
        binding.submitButton.text = getString(R.string.checking_eligibility)
        binding.tryAgainButton.visibility = View.GONE
        binding.errorTextView.visibility = View.GONE
        binding.accountNumberInputLayout.error = null

        val sample = co.veyra.bank.SampleData.active(this)
        val params = VerifyAccountParams.Builder(accountNumber, bank.institutionCode, sample.emailAddress)
            .accountHolderName(sample.accountName)
            .accountNumberSource(AccountNumberSource.MANUAL)
            .build()

        sdk.tokenisationService.checkAccountEligibility(params) { verifyResult ->
            verifyResult.fold(
                onSuccess = { verifyResponse ->
                    if (verifyResponse.responseCode?.uppercase() == "APPROVED") {
                        lifecycleScope.launch(Dispatchers.Main) {
                            startActivity(
                                AccountEligibilityActivity.intent(
                                    this@TokenizationRequestActivity,
                                    accountNumber,
                                    bank.institutionCode,
                                    bank.name
                                )
                            )
                            finish()
                        }
                    } else {
                        lifecycleScope.launch(Dispatchers.Main) {
                            showEligibilityError(verifyResponse.message ?: getString(R.string.error_occurred))
                        }
                    }
                },
                onFailure = { error ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        showEligibilityError(error.message ?: getString(R.string.error_occurred))
                    }
                }
            )
        }
    }

    private fun showEligibilityError(message: String) {
        showError(message)
        binding.submitButton.isEnabled = false
        binding.submitButton.text = getString(R.string.check_eligibility)
        binding.tryAgainButton.visibility = View.VISIBLE
    }

    private fun isValidAccountNumber(accountNumber: String): Boolean {
        // Check if account number is exactly 10 digits
        return accountNumber.length == 10 && accountNumber.all { it.isDigit() }
    }

    private fun showError(message: String) {
        binding.errorTextView.text = message
        binding.errorTextView.visibility = View.VISIBLE
        binding.accountNumberInputLayout.error = message
    }
}
