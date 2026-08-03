package co.veyra.bank.softpos

import co.veyra.bank.R
import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Base64
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import co.veyra.softpos.payment.sdk.TransactionResponse
import co.veyra.softpos.payment.sdk.merchant.NubanBank
import co.veyra.softpos.payment.sdk.VeyraSoftPOSSdk
import co.veyra.common.Environment
import co.veyra.softpos.payment.sdk.VeyraSoftPosSdkConfig
import co.veyra.softpos.payment.sdk.CurrencyUtils
import co.veyra.softpos.payment.sdk.TransactionInfo
import co.veyra.softpos.payment.sdk.TransactionRequest
import co.veyra.softpos.payment.sdk.TransactionStatus
import co.veyra.softpos.payment.sdk.merchant.MerchantRegistrationData
import co.veyra.softpos.payment.sdk.context.ContextPaymentClient
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * GetPaidActivity - Multi-page UI flow for payment processing
 * Registration flow (if not registered): Merchant Type → Registration Form → Payment
 * Payment flow: Amount Entry → Contactless Waiting → Payment Result
 */
class GetPaidActivity : AppCompatActivity() {
    
    private lateinit var sdk: VeyraSoftPOSSdk
    
    // Container for pages
    private lateinit var container: FrameLayout
    
    // Page 1: Amount Entry
    private lateinit var pageAmountEntry: View
    private lateinit var amountEditText: TextInputEditText
    private lateinit var processPaymentButton: MaterialButton
    private lateinit var merchantInactiveMessage: TextView
    
    // Page 2: Contactless Waiting
    private lateinit var pageContactlessWaiting: View
    private lateinit var amountDisplay: TextView
    private lateinit var waitingText: TextView
    private lateinit var statusMessage: TextView
    private lateinit var nfcProgressBar: ProgressBar
    private lateinit var cancelButton: android.widget.ImageButton
    
    // Page 3: Payment Result
    private lateinit var pagePaymentResult: View
    private lateinit var resultIcon: ImageView
    private lateinit var animatedCircle: AnimatedCircleView
    private lateinit var resultText: TextView
    private lateinit var resultMessage: TextView
    private lateinit var resultAmountDisplay: TextView
    private lateinit var additionalDetails: TextView
    private lateinit var doneButton: MaterialButton
    private lateinit var viewReceiptButton: MaterialButton
    private lateinit var pageReceipt: View
    private lateinit var receiptPageQrImage: ImageView
    private var receiptTransactionRef: String = ""
    private var receiptReturnPage: Int = PAGE_PAYMENT_RESULT
    
    // Registration flow
    private lateinit var pageMerchantTypeSelection: View
    private lateinit var pageMerchantRegistration: View

    // Edit merchant profile page
    private lateinit var pageMerchantEdit: View
    private lateinit var editMerchantNameEditText: TextInputEditText
    private lateinit var editEmailEditText: TextInputEditText
    private lateinit var editPhoneEditText: TextInputEditText
    private lateinit var editAddressLine1EditText: TextInputEditText
    private lateinit var editAddressLine2EditText: TextInputEditText
    private lateinit var editCityEditText: TextInputEditText
    private lateinit var editStateEditText: TextInputEditText
    private lateinit var editCountryCodeEditText: TextInputEditText
    private lateinit var editAccountNumberEditText: TextInputEditText
    private lateinit var editBankDropdown: AutoCompleteTextView
    private lateinit var editBankLoadingProgress: ProgressBar
    private lateinit var editBvnEditText: TextInputEditText
    private lateinit var editCacNumberEditText: TextInputEditText
    private lateinit var editBvnLayout: com.google.android.material.textfield.TextInputLayout
    private lateinit var editCacNumberLayout: com.google.android.material.textfield.TextInputLayout
    private var merchantTypeIsPersonal: Boolean = true
    private lateinit var merchantNameEditText: TextInputEditText
    private lateinit var emailEditText: TextInputEditText
    private lateinit var phoneEditText: TextInputEditText
    private lateinit var addressLine1EditText: TextInputEditText
    private lateinit var addressLine2EditText: TextInputEditText
    private lateinit var cityEditText: TextInputEditText
    private lateinit var stateEditText: TextInputEditText
    private lateinit var countryCodeEditText: TextInputEditText
    private lateinit var accountNumberEditText: TextInputEditText
    private lateinit var bankDropdown: AutoCompleteTextView
    private lateinit var bankLoadingProgress: ProgressBar
    private lateinit var acquirerIdEditText: TextInputEditText
    private lateinit var bvnEditText: TextInputEditText
    private lateinit var cacNumberEditText: TextInputEditText
    private lateinit var bvnLayout: com.google.android.material.textfield.TextInputLayout
    private lateinit var cacNumberLayout: com.google.android.material.textfield.TextInputLayout
    private lateinit var registerButton: MaterialButton
    private lateinit var registerProgressBar: ProgressBar
    private lateinit var saveChangesButton: MaterialButton

    // Bank list state
    private var cachedBanks: List<NubanBank>? = null
    private var selectedRegistrationBankCode: String = ""
    private var editSelectedBankCode: String = ""
    
    // Payment-method choice + MPM QR waiting (live rail: context create + lifecycle polling)
    private lateinit var pagePaymentMethod: View
    private lateinit var pageCpmConfirm: View
    private lateinit var methodAmountDisplay: TextView
    private lateinit var pageQrWaiting: View
    private lateinit var qrAmountDisplay: TextView
    private var qrPaymentJob: Job? = null
    // Kept so the QR page can stop the SDK-owned expiry watch on teardown.
    private var contextClient: co.veyra.softpos.payment.sdk.context.ContextPaymentClient? = null

    // Transactions flow
    private lateinit var pageTransactionsList: View
    private lateinit var pageTransactionDetail: View
    private lateinit var transactionsRecyclerView: RecyclerView
    private lateinit var transactionsEmptyText: TextView
    private var selectedTransactionRef: String = ""
    
    // Current page state
    private var currentPage = PAGE_AMOUNT_ENTRY
    /** Authorised amount in minor units (matches [TransactionRequest.amount] / EMV 9F02). */
    private var currentAmountMinorUnits: Long = 0
    private var currentPaymentCurrencyCode: String = "0566"
    private var lastOriginalTransactionReference: String = ""
    private var lastSuccessfulResponse: TransactionResponse? = null
    
    // Auto-navigation handler
    private val handler = Handler(Looper.getMainLooper())
    private var autoNavigateRunnable: Runnable? = null

    // CPM: scan the customer's payment QR; non-CPM scans show a hint and re-arm
    // by simply returning to the amount page (no terminal failure).
    // A valid scan lands on a full confirmation PAGE
    // — not a dialog floating over the amount-entry page — before anything is sent.
    private val cpmScanLauncher = registerForActivityResult(
        com.journeyapps.barcodescanner.ScanContract(),
    ) { result ->
        val text = result.contents ?: return@registerForActivityResult // scan cancelled
        val scanned = try {
            sdk.cpmCustomerQrService.inspect(text)
        } catch (e: IllegalArgumentException) {
            Toast.makeText(this, "Not a payment code — try again", Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        pendingCpmScan = scanned
        showPage(PAGE_CPM_CONFIRM)
    }

    /** The scanned CPM QR awaiting merchant confirmation on [PAGE_CPM_CONFIRM]. */
    private var pendingCpmScan: co.veyra.softpos.payment.sdk.cpm.ScannedCpmQr? = null

    private fun chargeCpm(scanned: co.veyra.softpos.payment.sdk.cpm.ScannedCpmQr) {
        currentAmountMinorUnits = scanned.amountMinorUnits
        currentPaymentCurrencyCode = scanned.currencyNumeric4
        // App-supplied reference (tap idiom) so the CPM result can offer the
        // receipt — the recorded transaction is keyed under it.
        val reference = "${System.currentTimeMillis()}_${(1000..9999).random()}"
        lifecycleScope.launch {
            try {
                val response = sdk.cpmCustomerQrService.charge(scanned, reference)
                lastOriginalTransactionReference = reference
                showPage(PAGE_PAYMENT_RESULT)
                // A delivered outcome (approved or declined) is recorded — receipt available.
                viewReceiptButton.visibility = View.VISIBLE
                showPaymentResult(
                    isSuccess = response.responseCode == "00",
                    title = getString(if (response.responseCode == "00") R.string.payment_successful else R.string.declined),
                    message = "Customer QR payment · ${response.responseCode ?: "-"}",
                    amountMinorUnits = scanned.amountMinorUnits,
                    details = response.transactionId?.let { "Transaction: $it" } ?: "",
                )
                scheduleAutoNavigate()
            } catch (e: Exception) {
                showPage(PAGE_PAYMENT_RESULT)
                // Transport failure — nothing recorded, so no receipt.
                viewReceiptButton.visibility = View.GONE
                showPaymentResult(
                    isSuccess = false,
                    title = getString(R.string.declined),
                    message = e.message ?: "Payment failed",
                    amountMinorUnits = scanned.amountMinorUnits,
                    details = "",
                )
                scheduleAutoNavigate()
            }
        }
    }
    
    companion object {
        /** When true, open straight into merchant registration/edit (Home's Settings gear). */
        const val EXTRA_MERCHANT_SETTINGS = "co.veyra.bank.MERCHANT_SETTINGS"

        /** "activate" / "deactivate": perform the merchant status change, then return to Home. */
        const val EXTRA_MERCHANT_ACTION = "co.veyra.bank.MERCHANT_ACTION"
        const val ACTION_ACTIVATE = "activate"
        const val ACTION_DEACTIVATE = "deactivate"

        private const val PAGE_MERCHANT_TYPE_SELECTION = 0
        private const val PAGE_MERCHANT_REGISTRATION = 1
        private const val PAGE_AMOUNT_ENTRY = 2
        private const val PAGE_CONTACTLESS_WAITING = 3
        private const val PAGE_PAYMENT_RESULT = 4
        private const val PAGE_TRANSACTIONS_LIST = 5
        private const val PAGE_TRANSACTION_DETAIL = 6
        private const val PAGE_RECEIPT = 7
        private const val PAGE_MERCHANT_EDIT = 8
        private const val PAGE_PAYMENT_METHOD = 9
        private const val PAGE_QR_WAITING = 10
        // Full-page CPM charge confirmation.
        private const val PAGE_CPM_CONFIRM = 11
        // Terminal outcomes (approved included) auto-return to Home after 5s if the
        // merchant touches nothing, matching the iOS sample.
        private const val AUTO_NAVIGATE_DELAY_MS = 5000L
        private const val QR_POLL_INTERVAL_MS = 2500L
    }

    /** Runtime location access so payment_device_info can include lat/lng (manifest alone is not enough). */
    private val requestLocationPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (!granted) {
            Toast.makeText(
                this,
                "Location permission denied — latitude/longitude may be omitted from the payment request.",
                Toast.LENGTH_LONG
            ).show()
        }
        navigateToContactlessAndStartPayment()
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_get_paid)

        // VeyraSdk facade owns the exclusive NFC mode (fully SDK-managed — claims at the
        // point of use, inert backstop on non-claiming screens); the SoftPOS SDK is
        // initialised here so its reader arming binds to this activity's lifecycle.
        co.veyra.bank.VeyraBank.ensureInitialized(this)
        sdk = VeyraSoftPOSSdk.initialize(this, co.veyra.bank.VeyraBank.softposConfig(this))


        container = findViewById(R.id.container)

        initializePages()
        setupClickListeners()

        // A merchant activate/deactivate action from Home's settings menu: perform it and
        // return to Home (no payment UI shown).
        val merchantAction = intent.getStringExtra(EXTRA_MERCHANT_ACTION)
        if (merchantAction != null) {
            performMerchantAction(merchantAction)
            return
        }

        val openMerchantSettings = intent.getBooleanExtra(EXTRA_MERCHANT_SETTINGS, false)
        when {
            // Opened from Home's Settings gear: go straight to merchant registration/edit
            // (NONE mode — no reader), regardless of amount-entry readiness.
            openMerchantSettings && sdk.merchantService.isRegistered() -> showPage(PAGE_MERCHANT_EDIT)
            openMerchantSettings -> showPage(PAGE_MERCHANT_TYPE_SELECTION)
            sdk.merchantService.isRegistered() -> showPage(PAGE_AMOUNT_ENTRY)
            else -> showPage(PAGE_MERCHANT_TYPE_SELECTION)
        }

    }

    /** Runs a merchant activate/deactivate requested from Home, then finishes back to Home. */
    private fun performMerchantAction(action: String) {
        if (!sdk.merchantService.isRegistered()) {
            Toast.makeText(this, getString(R.string.merchant_not_registered), Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val onResult: (Any?) -> Unit = { response ->
            runOnUiThread {
                val ok = response != null
                val msg = when {
                    action == ACTION_ACTIVATE && ok -> "Merchant activated"
                    action == ACTION_ACTIVATE -> "Failed to activate merchant"
                    ok -> "Merchant deactivated"
                    else -> "Failed to deactivate merchant"
                }
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                finish()
            }
        }
        if (action == ACTION_ACTIVATE) {
            sdk.merchantService.activate { onResult(it) }
        } else {
            sdk.merchantService.deactivate { onResult(it) }
        }
    }

    private fun initializePages() {
        val inflater = LayoutInflater.from(this)
        
        // Inflate Registration flow pages
        pageMerchantTypeSelection = inflater.inflate(R.layout.page_merchant_type_selection, container, false)
        pageMerchantRegistration = inflater.inflate(R.layout.page_merchant_registration, container, false)
        bvnLayout = pageMerchantRegistration.findViewById(R.id.bvnLayout)
        cacNumberLayout = pageMerchantRegistration.findViewById(R.id.cacNumberLayout)
        bvnEditText = pageMerchantRegistration.findViewById(R.id.bvnEditText)
        cacNumberEditText = pageMerchantRegistration.findViewById(R.id.cacNumberEditText)
        merchantNameEditText = pageMerchantRegistration.findViewById(R.id.merchantNameEditText)
        emailEditText = pageMerchantRegistration.findViewById(R.id.emailEditText)
        phoneEditText = pageMerchantRegistration.findViewById(R.id.phoneEditText)
        addressLine1EditText = pageMerchantRegistration.findViewById(R.id.addressLine1EditText)
        addressLine2EditText = pageMerchantRegistration.findViewById(R.id.addressLine2EditText)
        cityEditText = pageMerchantRegistration.findViewById(R.id.cityEditText)
        stateEditText = pageMerchantRegistration.findViewById(R.id.stateEditText)
        countryCodeEditText = pageMerchantRegistration.findViewById(R.id.countryCodeEditText)
        accountNumberEditText = pageMerchantRegistration.findViewById(R.id.accountNumberEditText)
        bankDropdown = pageMerchantRegistration.findViewById(R.id.bankDropdown)
        bankLoadingProgress = pageMerchantRegistration.findViewById(R.id.bankLoadingProgress)
        acquirerIdEditText = pageMerchantRegistration.findViewById(R.id.acquirerIdEditText)
        registerButton = pageMerchantRegistration.findViewById(R.id.registerButton)
        registerProgressBar = pageMerchantRegistration.findViewById(R.id.registerProgressBar)
        
        // Inflate Payment flow pages
        pageAmountEntry = inflater.inflate(R.layout.page_amount_entry, container, false)
        amountEditText = pageAmountEntry.findViewById(R.id.amountEditText)
        processPaymentButton = pageAmountEntry.findViewById(R.id.processPaymentButton)
        merchantInactiveMessage = pageAmountEntry.findViewById(R.id.merchantInactiveMessage)
        
        // Inflate Page 2: Contactless Waiting
        pageContactlessWaiting = inflater.inflate(R.layout.page_contactless_waiting, container, false)
        amountDisplay = pageContactlessWaiting.findViewById(R.id.amountDisplay)
        waitingText = pageContactlessWaiting.findViewById(R.id.waitingText)
        statusMessage = pageContactlessWaiting.findViewById(R.id.statusMessage)
        nfcProgressBar = pageContactlessWaiting.findViewById(R.id.nfcProgressBar)
        cancelButton = pageContactlessWaiting.findViewById(R.id.cancelButton)
        
        // Inflate Page 3: Payment Result
        pagePaymentResult = inflater.inflate(R.layout.page_payment_result, container, false)
        resultIcon = pagePaymentResult.findViewById(R.id.resultIcon)
        animatedCircle = pagePaymentResult.findViewById(R.id.animatedCircle)
        resultText = pagePaymentResult.findViewById(R.id.resultText)
        resultMessage = pagePaymentResult.findViewById(R.id.resultMessage)
        resultAmountDisplay = pagePaymentResult.findViewById(R.id.resultAmountDisplay)
        additionalDetails = pagePaymentResult.findViewById(R.id.additionalDetails)
        doneButton = pagePaymentResult.findViewById(R.id.doneButton)
        viewReceiptButton = pagePaymentResult.findViewById(R.id.viewReceiptButton)
        pageReceipt = inflater.inflate(R.layout.page_receipt, container, false)
        receiptPageQrImage = pageReceipt.findViewById(R.id.receiptQrImage)
        
        // Inflate payment-method choice + QR waiting pages
        pagePaymentMethod = inflater.inflate(R.layout.page_payment_method, container, false)
        methodAmountDisplay = pagePaymentMethod.findViewById(R.id.methodAmountDisplay)
        pageCpmConfirm = inflater.inflate(R.layout.page_cpm_confirm, container, false)
        pageQrWaiting = inflater.inflate(R.layout.page_qr_waiting, container, false)
        qrAmountDisplay = pageQrWaiting.findViewById(R.id.qrAmountDisplay)

        // Inflate transactions flow pages
        pageTransactionsList = inflater.inflate(R.layout.page_transactions_list, container, false)
        transactionsRecyclerView = pageTransactionsList.findViewById(R.id.transactionsRecyclerView)
        transactionsEmptyText = pageTransactionsList.findViewById(R.id.emptyText)
        pageTransactionDetail = inflater.inflate(R.layout.page_transaction_detail, container, false)

        // Inflate Edit Merchant Profile page
        pageMerchantEdit = inflater.inflate(R.layout.page_merchant_edit, container, false)
        editMerchantNameEditText = pageMerchantEdit.findViewById(R.id.editMerchantNameEditText)
        editEmailEditText = pageMerchantEdit.findViewById(R.id.editEmailEditText)
        editPhoneEditText = pageMerchantEdit.findViewById(R.id.editPhoneEditText)
        editAddressLine1EditText = pageMerchantEdit.findViewById(R.id.editAddressLine1EditText)
        editAddressLine2EditText = pageMerchantEdit.findViewById(R.id.editAddressLine2EditText)
        editCityEditText = pageMerchantEdit.findViewById(R.id.editCityEditText)
        editStateEditText = pageMerchantEdit.findViewById(R.id.editStateEditText)
        editCountryCodeEditText = pageMerchantEdit.findViewById(R.id.editCountryCodeEditText)
        editAccountNumberEditText = pageMerchantEdit.findViewById(R.id.editAccountNumberEditText)
        editBankDropdown = pageMerchantEdit.findViewById(R.id.editBankDropdown)
        editBankLoadingProgress = pageMerchantEdit.findViewById(R.id.editBankLoadingProgress)
        editBvnLayout = pageMerchantEdit.findViewById(R.id.editBvnLayout)
        editBvnEditText = pageMerchantEdit.findViewById(R.id.editBvnEditText)
        editCacNumberLayout = pageMerchantEdit.findViewById(R.id.editCacNumberLayout)
        editCacNumberEditText = pageMerchantEdit.findViewById(R.id.editCacNumberEditText)
        saveChangesButton = pageMerchantEdit.findViewById(R.id.saveChangesButton)

        // Add all pages to container (initially hidden)
        container.addView(pageMerchantTypeSelection)
        container.addView(pageMerchantRegistration)
        container.addView(pageAmountEntry)
        container.addView(pageContactlessWaiting)
        container.addView(pagePaymentResult)
        container.addView(pageTransactionsList)
        container.addView(pageTransactionDetail)
        container.addView(pageReceipt)
        container.addView(pageMerchantEdit)
        container.addView(pagePaymentMethod)
        container.addView(pageQrWaiting)
        container.addView(pageCpmConfirm)
    }
    
    private fun showPage(page: Int) {
        currentPage = page

        // Hide all pages
        pageMerchantTypeSelection.visibility = View.GONE
        pageMerchantRegistration.visibility = View.GONE
        pageAmountEntry.visibility = View.GONE
        pageContactlessWaiting.visibility = View.GONE
        pagePaymentResult.visibility = View.GONE
        pageTransactionsList.visibility = View.GONE
        pageTransactionDetail.visibility = View.GONE
        pageReceipt.visibility = View.GONE
        pageMerchantEdit.visibility = View.GONE
        pagePaymentMethod.visibility = View.GONE
        pageQrWaiting.visibility = View.GONE
        pageCpmConfirm.visibility = View.GONE

        // Show selected page
        when (page) {
            PAGE_MERCHANT_TYPE_SELECTION -> {
                pageMerchantTypeSelection.visibility = View.VISIBLE
            }
            PAGE_MERCHANT_REGISTRATION -> {
                pageMerchantRegistration.visibility = View.VISIBLE
                populateRegistrationForm()
                loadBanksForRegistration()
            }
            PAGE_AMOUNT_ENTRY -> {
                pageAmountEntry.visibility = View.VISIBLE
                amountEditText.setText("")
                amountEditText.requestFocus()
                updateMerchantActiveState()
            }
            PAGE_CONTACTLESS_WAITING -> {
                pageContactlessWaiting.visibility = View.VISIBLE
                amountDisplay.text = CurrencyUtils.formatAmount(currentAmountMinorUnits, currentPaymentCurrencyCode)
                statusMessage.text = ""
                nfcProgressBar.visibility = View.GONE
                cancelButton.visibility = View.VISIBLE
            }
            PAGE_PAYMENT_RESULT -> {
                pagePaymentResult.visibility = View.VISIBLE
            }
            PAGE_TRANSACTIONS_LIST -> {
                pageTransactionsList.visibility = View.VISIBLE
                loadTransactionsList()
            }
            PAGE_TRANSACTION_DETAIL -> {
                pageTransactionDetail.visibility = View.VISIBLE
                loadTransactionDetail(selectedTransactionRef)
            }
            PAGE_RECEIPT -> {
                pageReceipt.visibility = View.VISIBLE
                loadReceiptPage()
            }
            PAGE_MERCHANT_EDIT -> {
                pageMerchantEdit.visibility = View.VISIBLE
                populateEditForm()
                loadBanksForEdit()
            }
            PAGE_PAYMENT_METHOD -> {
                pagePaymentMethod.visibility = View.VISIBLE
                methodAmountDisplay.text = CurrencyUtils.formatAmount(currentAmountMinorUnits, currentPaymentCurrencyCode)
            }
            PAGE_QR_WAITING -> {
                pageQrWaiting.visibility = View.VISIBLE
                qrAmountDisplay.text = CurrencyUtils.formatAmount(currentAmountMinorUnits, currentPaymentCurrencyCode)
                startQrContextPayment()
            }
            PAGE_CPM_CONFIRM -> {
                pageCpmConfirm.visibility = View.VISIBLE
                val scanned = pendingCpmScan
                pageCpmConfirm.findViewById<TextView>(R.id.cpmConfirmAmountDisplay).text =
                    scanned?.let { CurrencyUtils.formatAmount(it.amountMinorUnits, it.currencyNumeric4) } ?: ""
                pageCpmConfirm.findViewById<TextView>(R.id.cpmConfirmCardText).text =
                    scanned?.let {
                        // The QR carries the card's display name ("AFRIGO ****1234"); older QRs
                        // carry none, so fall back to the last four.
                        val card = it.cardholderName ?: "Card •••• ${it.dpan.takeLast(4)}"
                        "$card · amount read from the customer's QR"
                    } ?: ""
            }
        }
        if (page != PAGE_QR_WAITING) stopQrContextPayment()

        cancelAutoNavigate()
    }

    private fun updateMerchantActiveState() {
        val isActive = sdk.merchantService.isMerchantActive()
        if (isActive) {
            merchantInactiveMessage.visibility = View.GONE
            processPaymentButton.isEnabled = true
        } else {
            merchantInactiveMessage.visibility = View.VISIBLE
            processPaymentButton.isEnabled = false
            // Still waiting to be activated — kick an immediate backend status check so the
            // amount screen unlocks as soon as the merchant goes active, without waiting
            // for the periodic (foreground) poll.
            sdk.merchantService.refreshStatus()
        }
    }
    
    private fun loadTransactionsList() {
        val transactions = sdk.transactionService.getLastTransactions(50)
        transactionsEmptyText.visibility = if (transactions.isEmpty()) View.VISIBLE else View.GONE
        transactionsRecyclerView.visibility = if (transactions.isEmpty()) View.GONE else View.VISIBLE
        transactionsRecyclerView.layoutManager = LinearLayoutManager(this)
        transactionsRecyclerView.adapter = TransactionAdapter(transactions) { tx ->
            selectedTransactionRef = tx.merchantTransactionReference
            showPage(PAGE_TRANSACTION_DETAIL)
        }
    }
    
    private fun loadTransactionDetail(ref: String) {
        val tx = sdk.transactionService.getTransaction(ref) ?: return
        pageTransactionDetail.findViewById<TextView>(R.id.detailReference).text = getString(R.string.reference)
        pageTransactionDetail.findViewById<TextView>(R.id.detailReferenceValue).text = tx.merchantTransactionReference
        pageTransactionDetail.findViewById<TextView>(R.id.detailTransactionId).text = getString(R.string.transaction_id)
        pageTransactionDetail.findViewById<TextView>(R.id.detailTransactionIdValue).text = tx.transactionId ?: "—"
        // Which rail took the payment (Tap / QR / Scan) — every rail records its own, so a QR
        // payment must never read as a tap. Wording comes from the SDK, shared across platforms.
        pageTransactionDetail.findViewById<TextView>(R.id.detailRail).text = getString(R.string.paid_via)
        pageTransactionDetail.findViewById<TextView>(R.id.detailRailValue).text = tx.railLabel
        pageTransactionDetail.findViewById<TextView>(R.id.detailAmount).text = getString(R.string.amount)
        pageTransactionDetail.findViewById<TextView>(R.id.detailAmountValue).text = CurrencyUtils.formatAmount(tx.amount, tx.currencyCode)
        pageTransactionDetail.findViewById<TextView>(R.id.detailStatus).text = getString(R.string.status)
        pageTransactionDetail.findViewById<TextView>(R.id.detailStatusValue).text = tx.transactionStatus.name
        pageTransactionDetail.findViewById<TextView>(R.id.detailResponseCode).text = getString(R.string.response_code)
        pageTransactionDetail.findViewById<TextView>(R.id.detailResponseCodeValue).text = tx.responseCode ?: "—"
        pageTransactionDetail.findViewById<TextView>(R.id.detailTime).text = getString(R.string.time)
        pageTransactionDetail.findViewById<TextView>(R.id.detailTimeValue).text = tx.transactionTime ?: "—"
        // EMV tag 5F20 as the card presented it (a Veyra token shows e.g. "AFRIGO ****1234").
        // Null on QR-MPM, where the merchant never reads the card, and on pre-5F20 rows.
        pageTransactionDetail.findViewById<TextView>(R.id.detailCardholder).text = getString(R.string.cardholder)
        pageTransactionDetail.findViewById<TextView>(R.id.detailCardholderValue).text = tx.cardholderName ?: "—"
        // Only show View Receipt for final statuses (approved, declined, failed) - not for pending
        pageTransactionDetail.findViewById<MaterialButton>(R.id.viewReceiptButtonDetail)?.visibility =
            if (tx.transactionStatus == TransactionStatus.PENDING) View.GONE else View.VISIBLE
    }
    
    private fun populateRegistrationForm() {
        // All demo defaults come from res/values/sample_data.xml via SampleData — one shared
        // identity (the same account the Wallet tokenises). Personal and Business differ only by
        // the CAC number, so the common fields are set once and only the CAC is type-specific.
        val d: co.veyra.bank.Merchant =
            if (merchantTypeIsPersonal) co.veyra.bank.SampleData.personal(this)
            else co.veyra.bank.SampleData.business(this)

        merchantNameEditText.setText(d.merchantName)
        emailEditText.setText(d.emailAddress)
        phoneEditText.setText(d.mobileNumber)
        addressLine1EditText.setText(d.addressLine1)
        addressLine2EditText.setText(d.addressLine2)
        cityEditText.setText(d.city)
        stateEditText.setText(d.state)
        countryCodeEditText.setText(d.countryCode)
        accountNumberEditText.setText(d.accountNumber)
        acquirerIdEditText.setText(d.acquirerId)
        bvnEditText.setText(d.bvn)
        cacNumberEditText.setText(d.cacNumber) // null for a personal merchant → clears the field

        // BVN applies to both; the CAC field is shown only for a business (the only difference).
        bvnLayout.visibility = View.VISIBLE
        cacNumberLayout.visibility = if (d.cacNumber != null) View.VISIBLE else View.GONE
    }
    
    private fun setupClickListeners() {
        // Merchant type selection
        pageMerchantTypeSelection.findViewById<MaterialButton>(R.id.personalButton).setOnClickListener {
            merchantTypeIsPersonal = true
            showPage(PAGE_MERCHANT_REGISTRATION)
        }
        pageMerchantTypeSelection.findViewById<MaterialButton>(R.id.businessButton).setOnClickListener {
            merchantTypeIsPersonal = false
            showPage(PAGE_MERCHANT_REGISTRATION)
        }
        
        // Registration
        pageMerchantRegistration.findViewById<View>(R.id.backToTypeButton).setOnClickListener {
            showPage(PAGE_MERCHANT_TYPE_SELECTION)
        }
        registerButton.setOnClickListener {
            performRegistration()
        }
        
        // Amount Entry: See Transactions
        pageAmountEntry.findViewById<MaterialButton>(R.id.seeTransactionsButton).setOnClickListener {
            showPage(PAGE_TRANSACTIONS_LIST)
        }

        // Settings cog: Edit Profile / Activate / Deactivate
        // Merchant management (edit / activate / deactivate) now lives on the Home
        // screen's settings gear — see HomeActivity. This screen only accepts payments.
        
        // Amount Entry: Process Payment Button
        processPaymentButton.setOnClickListener {
            if (!sdk.merchantService.isMerchantActive()) {
                Toast.makeText(this, getString(R.string.merchant_not_active), Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val amountText = amountEditText.text.toString()
            val minorUnits = CurrencyUtils.parseDisplayAmountToMinorUnits(amountText)
            if (minorUnits == null) {
                Toast.makeText(this, "Please enter a valid amount", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Amount committed — dismiss the soft keyboard so it doesn't linger over the
            // method/waiting screens for the rest of the flow (bad UX).
            hideKeyboard()
            currentAmountMinorUnits = minorUnits
            val storedMerchant = sdk.merchantService.getStoredMerchantData()
            currentPaymentCurrencyCode = storedMerchant?.currencyCode
                ?: storedMerchant?.countryCode?.trim()?.padStart(4, '0')?.takeIf { it.length == 4 }
                ?: "0566"

            // Amount committed — the customer now chooses how to pay: NFC tap or MPM QR.
            showPage(PAGE_PAYMENT_METHOD)
        }

        // CPM: scan the CUSTOMER's payment QR — it carries its own cryptogram-bound
        // amount, so no amount entry happens on this side; the merchant confirms what it says.
        pageAmountEntry.findViewById<com.google.android.material.button.MaterialButton>(R.id.scanCustomerQrButton)
            .setOnClickListener {
                if (!sdk.merchantService.isMerchantActive()) {
                    Toast.makeText(this, getString(R.string.merchant_not_active), Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                cpmScanLauncher.launch(
                    com.journeyapps.barcodescanner.ScanOptions()
                        .setDesiredBarcodeFormats(com.journeyapps.barcodescanner.ScanOptions.QR_CODE)
                        .setPrompt(getString(R.string.scan_customer_qr))
                        .setBeepEnabled(false)
                        .setOrientationLocked(true)
                        .setCaptureActivity(co.veyra.bank.wallet.PortraitCaptureActivity::class.java),
                )
            }

        // Payment method: back to amount, tap-to-pay (NFC, via location permission), or QR.
        pagePaymentMethod.findViewById<View>(R.id.methodBackButton).setOnClickListener {
            navigateToAmountEntry()
        }
        pagePaymentMethod.findViewById<MaterialButton>(R.id.tapToPayButton).setOnClickListener {
            startTapToPay()
        }
        // CPM confirm page: Charge sends; Cancel discards the scan.
        pageCpmConfirm.findViewById<MaterialButton>(R.id.cpmConfirmChargeButton).setOnClickListener {
            pendingCpmScan?.let { scanned ->
                pendingCpmScan = null
                chargeCpm(scanned)
            }
        }
        pageCpmConfirm.findViewById<MaterialButton>(R.id.cpmConfirmCancelButton).setOnClickListener {
            pendingCpmScan = null
            showPage(PAGE_AMOUNT_ENTRY)
        }

        pagePaymentMethod.findViewById<MaterialButton>(R.id.qrButton).setOnClickListener {
            showPage(PAGE_QR_WAITING)
        }

        // QR waiting: cancel back to amount entry (mirror of the contactless cancel).
        pageQrWaiting.findViewById<ImageButton>(R.id.qrCancelButton).setOnClickListener {
            navigateToAmountEntry()
        }


        // Page 2: Cancel Button — clear the armed payment so it cannot fire on a later
        // tap (and so a mode switch is never blocked by a stale pending payment)
        cancelButton.setOnClickListener {
            sdk.cardPaymentService.cancelPendingPayment()
            navigateToAmountEntry()
        }
        
        // Result page: Done confirms the outcome and returns to Home.
        doneButton.setOnClickListener {
            returnToHome()
        }
        
        // View Receipt - navigate to receipt page
        viewReceiptButton.setOnClickListener {
            receiptTransactionRef = lastOriginalTransactionReference
            receiptReturnPage = PAGE_PAYMENT_RESULT
            showPage(PAGE_RECEIPT)
        }
        
        // Transactions list: back button
        pageTransactionsList.findViewById<View>(R.id.backButton).setOnClickListener {
            showPage(PAGE_AMOUNT_ENTRY)
        }
        
        // Transaction detail: back button
        pageTransactionDetail.findViewById<View>(R.id.backButton).setOnClickListener {
            showPage(PAGE_TRANSACTIONS_LIST)
        }
        pageTransactionDetail.findViewById<MaterialButton>(R.id.viewReceiptButtonDetail)?.setOnClickListener {
            receiptTransactionRef = selectedTransactionRef
            receiptReturnPage = PAGE_TRANSACTION_DETAIL
            showPage(PAGE_RECEIPT)
        }

        // Receipt page: back button
        pageReceipt.findViewById<View>(R.id.backButton).setOnClickListener {
            showPage(receiptReturnPage)
        }
        // Receipt page: new transaction button
        pageReceipt.findViewById<MaterialButton>(R.id.newTransactionButton).setOnClickListener {
            navigateToAmountEntry()
        }

        // Edit merchant profile page
        pageMerchantEdit.findViewById<View>(R.id.editBackButton).setOnClickListener {
            showPage(PAGE_AMOUNT_ENTRY)
        }
        pageMerchantEdit.findViewById<MaterialButton>(R.id.saveChangesButton).setOnClickListener {
            performUpdateMerchant()
        }
    }
    
    /**
     * The MPM QR rail: obtain a gateway-signed payment context
     * for the sale, render its payload as the QR, and poll the context lifecycle until the
     * customer's push settles — the outcome lands on the same Result screen as a tap.
     */
    private fun startQrContextPayment() {
        stopQrContextPayment()
        val qrImage = pageQrWaiting.findViewById<ImageView>(R.id.qrImage)
        val placeholder = pageQrWaiting.findViewById<TextView>(R.id.qrPlaceholder)
        qrImage.visibility = View.GONE
        placeholder.visibility = View.VISIBLE
        placeholder.text = getString(R.string.qr_creating)

        val merchantId = sdk.merchantService.getStoredMerchantData()?.merchantId
        if (merchantId.isNullOrBlank()) {
            placeholder.text = getString(R.string.merchant_not_registered)
            return
        }
        qrPaymentJob = lifecycleScope.launch {
            val client = ContextPaymentClient(
                this@GetPaidActivity,
                Environment.TEST,
                getString(R.string.client_id),
                getString(R.string.client_secret),
            )
            contextClient = client
            // The SDK owns the expiry timer; blank the QR the moment it fires (an expired
            // QR must not stay scannable), instead of waiting for the server-polled
            // EXPIRED state up to one poll interval later.
            val created = client.createContextPayment(
                merchantId, currentAmountMinorUnits, currentPaymentCurrencyCode,
                onExpired = {
                    qrImage.visibility = View.GONE
                    placeholder.visibility = View.VISIBLE
                    placeholder.text = getString(R.string.qr_expired)
                    scheduleAutoNavigate()
                },
            )
            if (created == null) {
                placeholder.text = getString(R.string.qr_create_failed)
                scheduleAutoNavigate()
                return@launch
            }
            lastOriginalTransactionReference = created.txRef
            renderQrPayload(created.mpmPayload)

            // Poll the context lifecycle until the push settles (or the QR expires).
            while (isActive) {
                delay(QR_POLL_INTERVAL_MS)
                val status = client.contextStatus(created.txRef) ?: continue
                when {
                    status.isSettled -> {
                        showPage(PAGE_PAYMENT_RESULT)
                        showPaymentResult(
                            isSuccess = status.isApproved,
                            title = getString(
                                if (status.isApproved) R.string.payment_successful else R.string.declined,
                            ),
                            message = getString(R.string.qr_paid_by_customer),
                            amountMinorUnits = currentAmountMinorUnits,
                            details = "Ref: ${created.txRef}\nResponse: ${status.responseCode ?: "-"}",
                        )
                        // showPage cleared any pending auto-return; re-arm it for this outcome.
                        scheduleAutoNavigate()
                        break
                    }
                    status.state == "EXPIRED" -> {
                        qrImage.visibility = View.GONE
                        placeholder.visibility = View.VISIBLE
                        placeholder.text = getString(R.string.qr_expired)
                        scheduleAutoNavigate()
                        break
                    }
                }
            }
        }
    }

    private fun stopQrContextPayment() {
        qrPaymentJob?.cancel()
        qrPaymentJob = null
        // Stop the SDK expiry watch so its callback can't fire after the QR page is left.
        contextClient?.cancelQrExpiry()
        contextClient = null
    }

    /** Encode [payload] verbatim into the qrImage slot. */
    private fun renderQrPayload(payload: String) {
        val qrImage = pageQrWaiting.findViewById<ImageView>(R.id.qrImage)
        val placeholder = pageQrWaiting.findViewById<TextView>(R.id.qrPlaceholder)
        try {
            val size = 512
            val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bitmap.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            qrImage.setImageBitmap(bitmap)
            qrImage.visibility = View.VISIBLE
            placeholder.visibility = View.GONE
        } catch (e: Exception) {
            qrImage.visibility = View.GONE
            placeholder.visibility = View.VISIBLE
            placeholder.text = getString(R.string.qr_create_failed)
        }
    }

    /** Tap-to-pay chosen: runtime location first (payment_device_info wants lat/lng), then arm. */
    private fun startTapToPay() {
        val hasLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        if (hasLocation) {
            navigateToContactlessAndStartPayment()
        } else {
            requestLocationPermissions.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun navigateToContactlessAndStartPayment() {
        showPage(PAGE_CONTACTLESS_WAITING)
        initiatePayment()
    }

    /** Dismiss the soft keyboard (e.g. after the amount is entered and payment starts). */
    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        val token = currentFocus?.windowToken ?: amountEditText.windowToken
        if (token != null) imm?.hideSoftInputFromWindow(token, 0)
        amountEditText.clearFocus()
    }

    private fun initiatePayment() {
        waitingText.text = getString(R.string.tap_to_pay_here)
        waitingText.setTextColor(getColor(android.R.color.white))
        statusMessage.text = ""
        nfcProgressBar.visibility = View.GONE
        // Show cancel button when waiting for card
        cancelButton.visibility = View.VISIBLE
        
        // Build payment request (app provides amount, merchant_transaction_reference, currency, tx_type)
        lastOriginalTransactionReference = "${System.currentTimeMillis()}_${(1000..9999).random()}"
        val request = TransactionRequest.Builder(
            amount = currentAmountMinorUnits,
            merchantTransactionReference = lastOriginalTransactionReference,
            currency = currentPaymentCurrencyCode,
            txType = TransactionRequest.TxType.PURCHASE
        ).build()
        
        // Initiate payment using SDK. makeCardPayment claims SOFTPOS at the point of use;
        // in this combined app the claim is refused only while a wallet payment is
        // genuinely mid-flight — surface that as "finish the other payment first".
        try {
            sdk.cardPaymentService.makeCardPayment(
                request = request,
                callback = { transactionResponse ->
                    runOnUiThread {
                        handlePaymentResponse(transactionResponse)
                    }
                },
                onCardDetected = {
                    runOnUiThread {
                        // Update UI to show card detected and hide cancel button during processing
                        waitingText.text = getString(R.string.card_detected)
                        waitingText.setTextColor(getColor(R.color.success_green))
                        statusMessage.text = "Card detected! Processing..."
                        nfcProgressBar.visibility = View.VISIBLE
                        // Hide cancel button while processing
                        cancelButton.visibility = View.GONE
                    }
                },
                // Tapped target had no compatible payment app (another phone, a transit/access
                // card, an empty wallet). The SDK stays armed — just hint and keep waiting.
                onUnsupportedCard = {
                    runOnUiThread { showReTapHint(getString(R.string.card_not_supported)) }
                },
                // Card contact lost before it could be read. The SDK stays armed — prompt a
                // steady re-tap and keep waiting.
                onCardContactLost = {
                    runOnUiThread { showReTapHint(getString(R.string.card_hold_steady)) }
                }
            )
        } catch (e: co.veyra.core.SdkModeException) {
            // Should not happen: entering the contactless-waiting page already switched
            // the app into SOFTPOS mode. Defensive fallback only.
            Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
            navigateToAmountEntry()
        }
    }
    
    /**
     * A tap could not be turned into a transaction through no fault of the merchant (an
     * unsupported/non-payment card, or lost contact). The SDK keeps the payment armed, so
     * we stay on the waiting screen, restore the "tap to pay" prompt with a transient hint,
     * and leave Cancel available — the next tap is processed automatically.
     */
    private fun showReTapHint(hint: String) {
        if (currentPage != PAGE_CONTACTLESS_WAITING) return
        waitingText.text = getString(R.string.tap_to_pay_here)
        waitingText.setTextColor(getColor(android.R.color.white))
        statusMessage.text = hint
        nfcProgressBar.visibility = View.GONE
        cancelButton.visibility = View.VISIBLE
    }

    private fun handlePaymentResponse(response: TransactionResponse) {
        // A cancelled pending payment (Cancel button / mode switch) is not a result.
        if (response.message == "Payment cancelled") {
            return
        }

        nfcProgressBar.visibility = View.GONE

        // Navigate to result page
        showPage(PAGE_PAYMENT_RESULT)
        
        // Hide View Receipt when showing new result
        viewReceiptButton.visibility = View.GONE
        
        when (response.transactionCode) {
            "00" -> {
                lastSuccessfulResponse = response
                showPaymentResult(
                    isSuccess = true,
                    title = getString(R.string.payment_successful),
                    message = response.message ?: "Transaction approved",
                    amountMinorUnits = currentAmountMinorUnits,
                    details = buildString {
                        response.cardScheme?.let { append("Card Scheme: $it\n") }
                        response.cardExpiry?.let { append("Card Expiry: $it\n") }
                        response.merchantTransactionReference?.let { append("Reference: $it\n") }
                    }
                )
                showViewReceiptButton()
            }
            "99" -> {
                lastSuccessfulResponse = null
                // Pending - no response from issuer (timeout/network), status unknown - no receipt yet
                showPaymentResult(
                    isSuccess = null,
                    title = getString(R.string.payment_pending),
                    message = response.message ?: getString(R.string.payment_pending_message),
                    amountMinorUnits = currentAmountMinorUnits,
                    details = getString(R.string.payment_pending_message)
                )
                // Do NOT show View Receipt - transaction not final, status may change after polling
            }
            "05", "06" -> {
                lastSuccessfulResponse = null
                // Transaction declined or failed (explicit server response)
                showPaymentResult(
                    isSuccess = false,
                    title = getString(R.string.payment_failed),
                    message = response.message ?: "Transaction failed",
                    amountMinorUnits = currentAmountMinorUnits,
                    details = ""
                )
                showViewReceiptButton()
            }
            else -> {
                lastSuccessfulResponse = null
                // Other response codes
                showPaymentResult(
                    isSuccess = false,
                    title = "Transaction Status",
                    message = response.message ?: "Transaction completed with code: ${response.transactionCode}",
                    amountMinorUnits = currentAmountMinorUnits,
                    details = ""
                )
                showViewReceiptButton()
            }
        }

        // Auto-return to Home after the delay — every terminal outcome, approved included.
        scheduleAutoNavigate()
    }
    
    private fun showPaymentResult(
        isSuccess: Boolean?,
        title: String,
        message: String,
        amountMinorUnits: Long,
        details: String
    ) {
        when (isSuccess) {
            true -> {
                resultIcon.setImageResource(R.drawable.success_tick_only)
                resultText.text = getString(R.string.approved)
                resultText.setTextColor(getColor(R.color.success_green))
                animateCircle(getColor(R.color.success_green))
            }
            false -> {
                resultIcon.setImageResource(R.drawable.error_x_only)
                resultText.text = getString(R.string.declined)
                resultText.setTextColor(getColor(R.color.error_red))
                animateCircle(getColor(R.color.error_red))
            }
            null -> {
                resultIcon.setImageResource(R.drawable.pending_clock_only)
                resultText.text = getString(R.string.pending)
                resultText.setTextColor(getColor(R.color.warning_orange))
                animateCircle(getColor(R.color.warning_orange))
            }
        }
        
        resultMessage.text = message
        val currencyCode = currentPaymentCurrencyCode
        resultAmountDisplay.text = CurrencyUtils.formatAmount(amountMinorUnits, currencyCode)
        
        if (details.isNotEmpty()) {
            additionalDetails.text = details.trim()
            additionalDetails.visibility = View.VISIBLE
        } else {
            additionalDetails.visibility = View.GONE
        }
    }
    
    private fun animateCircle(color: Int) {
        // Set the circle color
        animatedCircle.setColor(color)
        
        // Reset the sweep angle
        animatedCircle.setSweepAngle(0f)
        
        // Animate the circle drawing effect - progressively draw the circle stroke
        val circleAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 800
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                val angle = animation.animatedValue as Float
                animatedCircle.setSweepAngle(angle)
            }
        }
        
        // Also add a subtle scale animation for polish
        val scaleAnimator = ObjectAnimator.ofFloat(animatedCircle, "scaleX", 0.9f, 1.0f).apply {
            duration = 800
        }
        val scaleYAnimator = ObjectAnimator.ofFloat(animatedCircle, "scaleY", 0.9f, 1.0f).apply {
            duration = 800
        }
        
        // Start animations together
        circleAnimator.start()
        scaleAnimator.start()
        scaleYAnimator.start()
    }
    
    private fun navigateToAmountEntry() {
        cancelAutoNavigate()
        showPage(PAGE_AMOUNT_ENTRY)
    }
    
    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    /**
     * Validates the fields common to registration and edit. Returns the 4-digit padded country
     * code, or null (after showing a toast) when something is invalid — call as `... ?: return`.
     */
    private fun validateMerchantForm(
        institutionCode: String,
        requiredFields: List<String>,
        countryCode: String,
    ): String? {
        if (institutionCode.isBlank()) { toast("Please select a bank"); return null }
        if (requiredFields.any { it.isBlank() }) { toast("Please fill all required fields"); return null }
        val padded = countryCode.padStart(4, '0')
        if (padded.length != 4 || padded.toIntOrNull() == null) {
            toast("Country code must be 4-digit numeric (e.g. 0566 for Nigeria)")
            return null
        }
        return padded
    }

    private fun performRegistration() {
        val merchantName = merchantNameEditText.text.toString().trim()
        val email = emailEditText.text.toString().trim()
        val phone = phoneEditText.text.toString().trim()
        val addressLine1 = addressLine1EditText.text.toString().trim()
        val addressLine2 = addressLine2EditText.text.toString().trim()
        val city = cityEditText.text.toString().trim()
        val state = stateEditText.text.toString().trim()
        val countryCode = countryCodeEditText.text.toString().trim()
        val accountNumber = accountNumberEditText.text.toString().trim()
        val institutionCode = selectedRegistrationBankCode
        val acquirerId = acquirerIdEditText.text.toString().trim()
        val bvn = bvnEditText.text.toString().trim()
        val cacNumber = cacNumberEditText.text.toString().trim()

        val countryCodePadded = validateMerchantForm(
            institutionCode = institutionCode,
            requiredFields = listOf(merchantName, email, phone, addressLine1, city, state, countryCode, accountNumber, acquirerId),
            countryCode = countryCode,
        ) ?: return

        if (merchantTypeIsPersonal && bvn.isBlank()) {
            toast("BVN is required for personal merchants"); return
        }
        if (!merchantTypeIsPersonal && cacNumber.isBlank()) {
            toast("CAC number is required for business merchants"); return
        }

        registerButton.isEnabled = false
        registerProgressBar.visibility = View.VISIBLE

        val data = MerchantRegistrationData(
            merchantName = merchantName,
            emailAddress = email,
            phoneNumber = phone,
            addressLine1 = addressLine1,
            addressLine2 = addressLine2,
            city = city,
            state = state,
            countryCode = countryCodePadded,
            accountNumber = accountNumber,
            institutionCode = institutionCode,
            acquirerId = acquirerId,
            bvn = if (merchantTypeIsPersonal) bvn else null,
            cacNumber = if (!merchantTypeIsPersonal) cacNumber else null
        )

        // Personal vs business differ only in which SDK call is made — the result handling is shared.
        val register = if (merchantTypeIsPersonal) sdk.merchantService::registerPersonalMerchant
                       else sdk.merchantService::registerBusinessMerchant
        register(data) { response ->
            runOnUiThread {
                registerButton.isEnabled = true
                registerProgressBar.visibility = View.GONE
                if (response.success) {
                    Toast.makeText(this, getString(R.string.registration_successful), Toast.LENGTH_LONG).show()
                    showPage(PAGE_AMOUNT_ENTRY)
                } else {
                    Toast.makeText(this, response.message ?: getString(R.string.registration_failed), Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun populateEditForm() {
        val merchant = sdk.merchantService.getStoredMerchantData() ?: return
        editMerchantNameEditText.setText(merchant.merchantName)
        editEmailEditText.setText(merchant.emailAddress)
        editPhoneEditText.setText(merchant.phoneNumber)
        editAddressLine1EditText.setText(merchant.addressLine1)
        editAddressLine2EditText.setText(merchant.addressLine2)
        editCityEditText.setText(merchant.city)
        editStateEditText.setText(merchant.state)
        editCountryCodeEditText.setText(merchant.countryCode)
        editAccountNumberEditText.setText(merchant.accountNumber)
        // BVN (personal) and CAC (business) are mutually exclusive per merchant type — show and
        // fill each only when the stored merchant has it.
        bindOptionalField(editBvnLayout, editBvnEditText, merchant.bvn)
        bindOptionalField(editCacNumberLayout, editCacNumberEditText, merchant.cacNumber)
    }

    /** Shows [field] with [value] when present, otherwise hides its [layout]. */
    private fun bindOptionalField(
        layout: com.google.android.material.textfield.TextInputLayout,
        field: TextInputEditText,
        value: String?,
    ) {
        layout.visibility = if (!value.isNullOrBlank()) View.VISIBLE else View.GONE
        field.setText(value)
    }

    private fun performUpdateMerchant() {
        val merchantName = editMerchantNameEditText.text.toString().trim()
        val email = editEmailEditText.text.toString().trim()
        val phone = editPhoneEditText.text.toString().trim()
        val addressLine1 = editAddressLine1EditText.text.toString().trim()
        val addressLine2 = editAddressLine2EditText.text.toString().trim()
        val city = editCityEditText.text.toString().trim()
        val state = editStateEditText.text.toString().trim()
        val countryCode = editCountryCodeEditText.text.toString().trim()
        val accountNumber = editAccountNumberEditText.text.toString().trim()
        val institutionCode = editSelectedBankCode

        val countryCodePadded = validateMerchantForm(
            institutionCode = institutionCode,
            requiredFields = listOf(merchantName, email, phone, addressLine1, city, state, countryCode, accountNumber),
            countryCode = countryCode,
        ) ?: return

        saveChangesButton.isEnabled = false
        sdk.merchantService.updateMerchant(
            merchantName = merchantName,
            emailAddress = email,
            phoneNumber = phone,
            addressLine1 = addressLine1,
            addressLine2 = addressLine2,
            city = city,
            state = state,
            countryCode = countryCodePadded,
            accountNumber = accountNumber,
            institutionCode = institutionCode
        ) { response ->
            saveChangesButton.isEnabled = true
            if (response != null) {
                Toast.makeText(this, "Merchant profile updated", Toast.LENGTH_SHORT).show()
                showPage(PAGE_AMOUNT_ENTRY)
            } else {
                Toast.makeText(this, "Failed to update merchant — please try again", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadBanksForRegistration() {
        selectedRegistrationBankCode = ""
        bankDropdown.setText("", false)
        // Pre-select the sample bank so the default institution is shown and submitted.
        val sampleBankCode = if (merchantTypeIsPersonal) {
            co.veyra.bank.SampleData.personal(this).institutionCode
        } else {
            co.veyra.bank.SampleData.business(this).institutionCode
        }
        val banks = cachedBanks
        if (banks != null) {
            setupBankDropdown(bankDropdown, banks, sampleBankCode) { code -> selectedRegistrationBankCode = code }
            return
        }
        bankLoadingProgress.visibility = View.VISIBLE
        bankDropdown.isEnabled = false
        sdk.merchantService.getBanks { result ->
            bankLoadingProgress.visibility = View.GONE
            bankDropdown.isEnabled = true
            if (result != null) {
                cachedBanks = result
                setupBankDropdown(bankDropdown, result, sampleBankCode) { code -> selectedRegistrationBankCode = code }
            } else {
                Toast.makeText(this, "Failed to load bank list. Please go back and try again.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadBanksForEdit() {
        val merchantCode = sdk.merchantService.getStoredMerchantData()?.institutionCode
        editSelectedBankCode = merchantCode ?: ""
        editBankDropdown.setText("", false)
        val banks = cachedBanks
        if (banks != null) {
            setupBankDropdown(editBankDropdown, banks, merchantCode) { code -> editSelectedBankCode = code }
            return
        }
        editBankLoadingProgress.visibility = View.VISIBLE
        editBankDropdown.isEnabled = false
        sdk.merchantService.getBanks { result ->
            editBankLoadingProgress.visibility = View.GONE
            editBankDropdown.isEnabled = true
            if (result != null) {
                cachedBanks = result
                setupBankDropdown(editBankDropdown, result, merchantCode) { code -> editSelectedBankCode = code }
            } else {
                Toast.makeText(this, "Failed to load bank list. Please go back and try again.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupBankDropdown(
        dropdown: AutoCompleteTextView,
        banks: List<NubanBank>,
        preselectCode: String?,
        onSelected: (String) -> Unit
    ) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, banks.map { it.name })
        dropdown.setAdapter(adapter)
        dropdown.setOnItemClickListener { _, _, position, _ ->
            onSelected(banks[position].institutionCode)
        }
        if (!preselectCode.isNullOrBlank()) {
            val match = banks.find { it.institutionCode == preselectCode }
            if (match != null) {
                dropdown.setText(match.name, false)
                // Also record the selection so the pre-filled bank is actually submitted,
                // not just displayed (the sample institution comes from sample_data.xml).
                onSelected(match.institutionCode)
            }
        }
    }

    private fun scheduleAutoNavigate() {
        cancelAutoNavigate()
        autoNavigateRunnable = Runnable {
            returnToHome()
        }
        handler.postDelayed(autoNavigateRunnable!!, AUTO_NAVIGATE_DELAY_MS)
    }

    /** Confirming (or ignoring) a terminal outcome finishes back to Home. */
    private fun returnToHome() {
        cancelAutoNavigate()
        finish()
    }
    
    private fun cancelAutoNavigate() {
        autoNavigateRunnable?.let {
            handler.removeCallbacks(it)
            autoNavigateRunnable = null
        }
    }
    
    override fun onStop() {
        super.onStop()
        // Stop the context-status poll once the screen leaves the foreground. lifecycleScope only
        // cancels on onDestroy, so a backgrounded QR would otherwise keep hitting the gateway
        // every ~2.5s (15s per attempt once it's unreachable) until the activity is destroyed.
        // The QR is short-lived; returning shows a fresh one via NEW QR.
        stopQrContextPayment()
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelAutoNavigate()
        stopQrContextPayment()
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // singleTask: keep getIntent() pointing at the latest launch
    }
    
    private fun showViewReceiptButton() {
        viewReceiptButton.visibility = View.VISIBLE
    }
    
    private fun loadReceiptPage() {
        val result = sdk.transactionService.generateTransactionReceipt(receiptTransactionRef)
        if (result != null) {
            val bytes = Base64.decode(result.qrCodeBase64, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bitmap != null) {
                receiptPageQrImage.setImageBitmap(bitmap)
                receiptPageQrImage.visibility = View.VISIBLE
                pageReceipt.findViewById<TextView>(R.id.receiptHint).visibility = View.VISIBLE
                // The paying card as it presented itself (EMV 5F20) — shown on the merchant's
                // copy only; absent on QR-MPM, where the merchant never reads the card.
                pageReceipt.findViewById<TextView>(R.id.receiptCardholder).apply {
                    text = result.cardholderName
                    visibility = if (result.cardholderName.isNullOrBlank()) View.GONE else View.VISIBLE
                }
            } else {
                receiptPageQrImage.visibility = View.GONE
                pageReceipt.findViewById<TextView>(R.id.receiptHint).text = getString(R.string.receipt_not_available)
            }
        } else {
            receiptPageQrImage.visibility = View.GONE
            pageReceipt.findViewById<TextView>(R.id.receiptHint).text = getString(R.string.receipt_not_available)
            pageReceipt.findViewById<TextView>(R.id.receiptHint).visibility = View.VISIBLE
        }
    }
}
