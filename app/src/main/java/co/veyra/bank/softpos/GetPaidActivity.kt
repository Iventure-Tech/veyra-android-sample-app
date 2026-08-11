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
    private lateinit var creditConfirmationLine: TextView
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
    // The MPM result page's credit-confirmation watch (see watchCreditConfirmationForQr).
    private var creditWatchJob: Job? = null
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
    /**
     * The reference of the last sale — **as minted by the SDK and returned on the response**.
     * The app does not invent one: since the SDK became the minter, an app-generated value is a
     * key the gateway has never seen, and every receipt or status lookup built on it would miss.
     */
    private var lastOriginalTransactionReference: String = ""
    private var lastSuccessfulResponse: TransactionResponse? = null

    /**
     * A stand-in for the till's own order/basket/invoice id, which a real integration would take
     * from its POS rather than generate. It is optional, is never used as a lookup key, and may
     * repeat across attempts of the same sale — which is exactly what ties a retry back to the
     * original order, now that every attempt mints its own transaction reference.
     */
    private fun nextSampleOrderId(): String = "ORDER-${System.currentTimeMillis()}"

    // The sale the result page is waiting on beneficiary credit confirmation for.
    // Set when an APPROVED response says the merchant's bank supports confirmation; cleared when
    // the confirmation arrives. onCreditConfirmation events for any other reference (a sale from
    // an earlier session) fall back to the toast.
    private var awaitingCreditConfirmationRef: String? = null
    
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


    /** While a CPM charge is in flight the confirm page shows progress instead of buttons. */
    private fun setCpmConfirmCharging(charging: Boolean) {
        val buttons = if (charging) View.GONE else View.VISIBLE
        pageCpmConfirm.findViewById<MaterialButton>(R.id.cpmConfirmChargeButton).visibility = buttons
        pageCpmConfirm.findViewById<MaterialButton>(R.id.cpmConfirmCancelButton).visibility = buttons
        pageCpmConfirm.findViewById<View>(R.id.cpmChargingIndicator).visibility =
            if (charging) View.VISIBLE else View.GONE
    }

    private fun chargeCpm(scanned: co.veyra.softpos.payment.sdk.cpm.ScannedCpmQr) {
        currentAmountMinorUnits = scanned.amountMinorUnits
        currentPaymentCurrencyCode = scanned.currencyNumeric4
        // Your own order id — optional, never a key, and safe to reuse across attempts of the
        // same sale. The transaction *reference* is minted by the SDK and comes back on the
        // response; the app no longer invents one.
        val orderId = nextSampleOrderId()
        // The whole round trip happens behind this page: swap the buttons for a visible
        // processing state so the merchant sees the charge is in flight (iOS parity).
        setCpmConfirmCharging(true)
        lifecycleScope.launch {
            try {
                val response = sdk.cpmCustomerQrService.charge(scanned, merchantOrderId = orderId)
                // Key the receipt and the credit watch off the reference the SDK returns.
                lastOriginalTransactionReference = response.merchantTransactionReference.orEmpty()
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
                // An approved CPM sale can wait on beneficiary credit confirmation just like tap
                // and MPM — the same stored-row watch, which cancels the hold above if the
                // merchant's bank turns out to support it.
                if (response.responseCode == "00") watchCreditConfirmationForQr(reference)
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
        // Terminal outcomes (approved, declined and failed alike) hold the result page for this
        // long and then return to Home on their own; Done dismisses immediately at any point.
        // 60s: a cashier needs long enough to read the outcome to the customer, and an approved
        // sale may still be waiting on the merchant bank's credit confirmation.
        private const val AUTO_NAVIGATE_DELAY_MS = 60_000L
        private const val QR_POLL_INTERVAL_MS = 2500L
        // How the MPM result page watches the stored row for credit-confirmation
        // state (the supported flag lands within the SDK reconciler's first status probe;
        // the confirmation itself whenever the merchant's bank answers). The watch runs while
        // the result page is up.
        private const val CREDIT_WATCH_INTERVAL_MS = 3000L
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
        creditConfirmationLine = pagePaymentResult.findViewById(R.id.creditConfirmationLine)
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
                setCpmConfirmCharging(false)
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
        // Your own order id, from the stored row. Always a row, em-dash when the sale carried
        // none — the same null rendering the wallet's detail uses. (This used to hide the row on
        // legacy sales; superseded so the two sides of a payment show the same shape of detail.)
        val orderIdLabel = pageTransactionDetail.findViewById<TextView>(R.id.detailOrderId)
        val orderIdValue = pageTransactionDetail.findViewById<TextView>(R.id.detailOrderIdValue)
        orderIdLabel.text = getString(R.string.order_id)
        orderIdValue.text = tx.merchantOrderId?.takeIf { it.isNotBlank() } ?: "—"
        orderIdLabel.visibility = View.VISIBLE
        orderIdValue.visibility = View.VISIBLE
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
        // The outcome's stated cause, verbatim from the backend (e.g. INSUFFICIENT_FUNDS);
        // unresolved/legacy rows carry none.
        pageTransactionDetail.findViewById<TextView>(R.id.detailReason).text = getString(R.string.tx_detail_reason)
        pageTransactionDetail.findViewById<TextView>(R.id.detailReasonValue).text = tx.responseStatusReason ?: "—"
        // Whether the merchant's bank has confirmed receiving the funds. Null while
        // unconfirmed (and on unsupported banks/older rows) — shown as an em-dash, never as an
        // alarming "not received"; RECEIVED/UNABLE_TO_CONFIRM are the only stored values.
        pageTransactionDetail.findViewById<TextView>(R.id.detailCredit).text = getString(R.string.tx_detail_merchant_credit)
        pageTransactionDetail.findViewById<TextView>(R.id.detailCreditValue).text = tx.creditConfirmationStatus ?: "—"
        // The credit leg's own id — what you quote to a bank when chasing the settlement. Shown
        // only where the confirmation rail applies at all: with no rail there is nothing to
        // quote, and a bare id under no "Merchant credit" line would read as a promise.
        val creditIdLabel = pageTransactionDetail.findViewById<TextView>(R.id.detailCreditTransactionId)
        val creditIdValue = pageTransactionDetail.findViewById<TextView>(R.id.detailCreditTransactionIdValue)
        val onCreditRail = tx.isCreditConfirmationSupported == true
        creditIdLabel.text = getString(R.string.tx_detail_credit_transaction_id)
        creditIdValue.text = tx.creditTransactionId?.takeIf { it.isNotBlank() } ?: "—"
        creditIdLabel.visibility = if (onCreditRail) View.VISIBLE else View.GONE
        creditIdValue.visibility = if (onCreditRail) View.VISIBLE else View.GONE
        pageTransactionDetail.findViewById<TextView>(R.id.detailTime).text = getString(R.string.time)
        pageTransactionDetail.findViewById<TextView>(R.id.detailTimeValue).text = tx.transactionTime ?: "—"
        // The card's display name (a Veyra token shows e.g. "AFRIGO ****1234") — read off EMV tag
        // 5F20 on a tap, off the scanned QR on CPM, and carried by the gateway on QR-MPM, where
        // the merchant never touches the card. Null only on pre-5F20 rows and on MPM sales paid
        // by a wallet older than the release that added it.
        pageTransactionDetail.findViewById<TextView>(R.id.detailCardholder).text = getString(R.string.cardholder)
        pageTransactionDetail.findViewById<TextView>(R.id.detailCardholderValue).text = tx.cardholderName ?: "—"
        // Only show View Receipt for final statuses (approved, declined, failed) - not for pending
        pageTransactionDetail.findViewById<MaterialButton>(R.id.viewReceiptButtonDetail)?.visibility =
            if (tx.transactionStatus == TransactionStatus.PENDING) View.GONE else View.VISIBLE
        bindStatusCheckButton(tx)
        bindCreditCheckButton(tx)
    }

    /**
     * "Check status" on the transaction detail — the manual ask beside the SDK's own background
     * poll.
     *
     * Visible **only while the row is `PENDING`**: a settled row has nothing left to ask, and
     * offering the action would imply its outcome might still change. It disappears the moment the
     * row resolves.
     *
     * This is the documented route to an answer after the SDK's 30-day give-up, which is why it is
     * offered on however old a pending row. A convenience the rest of the time, never the
     * mechanism: the SDK keeps asking whether or not this page is up.
     */
    private fun bindStatusCheckButton(tx: TransactionInfo) {
        val button = pageTransactionDetail.findViewById<MaterialButton>(R.id.checkStatusButton)
        val note = pageTransactionDetail.findViewById<TextView>(R.id.detailStatusCheckNote)
        note.visibility = View.GONE

        if (tx.transactionStatus != TransactionStatus.PENDING) {
            button.visibility = View.GONE
            return
        }
        button.visibility = View.VISIBLE
        button.isEnabled = true
        button.setOnClickListener {
            // The SDK deliberately has no throttle — the screen disables its own button.
            button.isEnabled = false
            note.text = getString(R.string.status_check_in_flight)
            note.visibility = View.VISIBLE
            lifecycleScope.launch {
                runCatching {
                    sdk.transactionService.refreshTransactionStatus(tx.merchantTransactionReference)
                }.onSuccess { fresh ->
                    if (fresh != null && fresh.transactionStatus != TransactionStatus.PENDING) {
                        // Re-render the detail and the list so the status, this button and the
                        // receipt affordance all agree.
                        loadTransactionDetail(tx.merchantTransactionReference)
                        loadTransactionsList()
                    } else {
                        // Still unsettled — the honest answer, not a failure.
                        note.text = getString(R.string.status_check_still_pending)
                        button.isEnabled = true
                    }
                }.onFailure { e ->
                    // An offline device gets the one piece of advice that helps. The row is
                    // untouched either way — a failed check is never an outcome.
                    note.text = if (e.message?.contains("NO_NETWORK_CONNECTION") == true) {
                        getString(R.string.status_check_offline)
                    } else {
                        getString(R.string.status_check_failed, e.message ?: "unknown error")
                    }
                    button.isEnabled = true
                }
            }
        }
    }

    /**
     * "Check merchant credit" on the transaction detail — the manual ask beside the SDK's own
     * background credit sweep.
     *
     * Visible on exactly the predicate the SDK enforces internally: the sale is **approved**, the
     * merchant's bank is on the confirmation rail (`isCreditConfirmationSupported == true`), and the
     * credit is not already `RECEIVED`. Calling it outside that is a no-op rather than an error, but
     * offering a control that does nothing is its own defect — so the button is gated on the same
     * line the SDK checks.
     *
     * It deliberately stays offered on a row the 30-day sweep gave up on and stamped
     * `UNABLE_TO_CONFIRM`: that means "we stopped asking", never "the funds were not received", and
     * asking again after it is exactly what this button is for. A later `RECEIVED` replaces it.
     *
     * On an approved row this is the button that applies; on a pending one, "Check status" is.
     */
    private fun bindCreditCheckButton(tx: TransactionInfo) {
        val button = pageTransactionDetail.findViewById<MaterialButton>(R.id.checkMerchantCreditButton)
        val note = pageTransactionDetail.findViewById<TextView>(R.id.detailCreditCheckNote)
        note.visibility = View.GONE

        val eligible = tx.transactionStatus == TransactionStatus.APPROVED &&
            tx.isCreditConfirmationSupported == true &&
            tx.creditConfirmationStatus != "RECEIVED"
        if (!eligible) {
            button.visibility = View.GONE
            return
        }
        button.visibility = View.VISIBLE
        button.isEnabled = true
        button.setOnClickListener {
            // The SDK deliberately has no throttle — the screen disables its own button.
            button.isEnabled = false
            note.text = getString(R.string.credit_check_in_flight)
            note.visibility = View.VISIBLE
            lifecycleScope.launch {
                runCatching {
                    sdk.transactionService.refreshCreditConfirmation(tx.merchantTransactionReference)
                }.onSuccess { fresh ->
                    if (fresh?.creditConfirmationStatus == "RECEIVED") {
                        // Re-render the detail so the credit line and this button agree.
                        loadTransactionDetail(tx.merchantTransactionReference)
                    } else {
                        // Not confirmed **yet** — the honest answer, never "not received".
                        note.text = getString(R.string.credit_check_still_unconfirmed)
                        button.isEnabled = true
                    }
                }.onFailure { e ->
                    // An offline device gets the one piece of advice that helps. The row is
                    // untouched either way — a failed check is never a settlement answer.
                    note.text = if (e.message?.contains("NO_NETWORK_CONNECTION") == true) {
                        getString(R.string.credit_check_offline)
                    } else {
                        getString(R.string.credit_check_failed, e.message ?: "unknown error")
                    }
                    button.isEnabled = true
                }
            }
        }
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
                // The merchant-presented rail carries your order id too, so all three rails
                // (tap, CPM charge, MPM) tie a sale back to the same POS order.
                merchantOrderId = nextSampleOrderId(),
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
                        // showPage cleared any pending auto-return; re-arm the 60s hold for this
                        // outcome — approved or not.
                        scheduleAutoNavigate()
                        // An approved MPM sale waits on credit confirmation too —
                        // driven off the SDK's stored row, see watchCreditConfirmationForQr.
                        // The settle itself can't say whether the bank supports confirmation, so
                        // the hold above runs meanwhile; the watch cancels it the moment the
                        // stored row says the bank does support it.
                        if (status.isApproved) watchCreditConfirmationForQr(created.txRef)
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

    /**
     * MPM leg: an approved QR sale waits on beneficiary credit confirmation like a tap,
     * but the plumbing differs — the contexts endpoint carries no credit fields, so the SDK's
     * settle reconciler learns `isCreditConfirmationSupported` from the transaction-status rail
     * moments after the settle, and the background credit sweep then confirms the credit onto the
     * stored row. Neither leg pushes a callback the app registered for this sale (the
     * `onCreditConfirmation` param belongs to `makeCardPayment`), so the result page watches the
     * SDK's own stored row: first for the supported flag (shows the waiting line), then for the
     * terminal confirmation state (flips it). Leaving the result page ends the watch — a later
     * confirmation still lands in history and as the SDK-wide toast/callback where registered.
     *
     * The watch also owns this page's *hold*, and only the hold — it never touches the SDK's
     * polling, which is app-scoped and runs whatever screen is up. The settle armed the normal
     * 60s hold; the moment the row says the bank supports confirmation the hold is CANCELLED
     * (the page must not vanish mid-wait), and once the answer is on screen a fresh 60s hold
     * starts. A row that never says "supported" simply lets the original hold expire — no
     * separate flag-unknown state.
     */
    private fun watchCreditConfirmationForQr(ref: String) {
        creditWatchJob?.cancel()
        creditWatchJob = lifecycleScope.launch {
            var waiting = false
            while (currentPage == PAGE_PAYMENT_RESULT) {
                val row = sdk.transactionService.getTransaction(ref)
                val confirmation = row?.creditConfirmationStatus
                when {
                    confirmation != null -> {
                        val received = confirmation == "RECEIVED"
                        creditConfirmationLine.text = getString(
                            if (received) R.string.funds_received_by_merchant_bank
                            else R.string.credit_could_not_be_confirmed
                        )
                        creditConfirmationLine.setTextColor(
                            getColor(if (received) R.color.success_green else R.color.error_red)
                        )
                        creditConfirmationLine.visibility = View.VISIBLE
                        // The answer is on screen: start a FRESH 60s hold from here.
                        scheduleAutoNavigate()
                        return@launch
                    }
                    !waiting && row?.isCreditConfirmationSupported == true -> {
                        waiting = true
                        // The flag turned true — cancel the hold; the page waits for the bank.
                        cancelAutoNavigate()
                        creditConfirmationLine.text = getString(R.string.confirming_credit_with_merchant_bank)
                        creditConfirmationLine.setTextColor(getColor(R.color.warning_orange))
                        creditConfirmationLine.visibility = View.VISIBLE
                    }
                }
                delay(CREDIT_WATCH_INTERVAL_MS)
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
        
        // Build the payment request: you supply amount, currency, tx type and — optionally —
        // your own order id. The **transaction reference is minted by the SDK** and arrives on
        // the response; an app that invents one is keying its receipts off a value the gateway
        // has never seen.
        lastOriginalTransactionReference = ""
        val request = TransactionRequest.Builder(
            amount = currentAmountMinorUnits,
            currency = currentPaymentCurrencyCode,
            txType = TransactionRequest.TxType.PURCHASE
        ).merchantOrderId(nextSampleOrderId()).build()
        
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
                },
                // The SDK polls the beneficiary bank after an approved sale (when the
                // payment response says confirmation is supported) and pushes the answer here —
                // possibly minutes later, possibly for a sale from an earlier session, so match by
                // the reference. Settlement news only; the payment outcome is already on screen.
                onCreditConfirmation = { confirmation ->
                    runOnUiThread { showCreditConfirmation(confirmation) }
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
     * Surface the credit-receipt confirmation the SDK's background polling delivered.
     * "RECEIVED" means the funds are in the merchant's settlement account; the final
     * "UNABLE_TO_CONFIRM" give-up shows the couldn't-confirm copy, never "not received".
     *
     * When it is the sale the result page is waiting on (the approval said the merchant's bank
     * supports confirmation, so the page shows "Confirming credit with merchant bank…"), the
     * confirmation flips that same line in place. Anything else — a sale from an earlier
     * session, or the merchant already navigated away — lands as a toast, so the news still
     * arrives whichever page is up.
     */
    private fun showCreditConfirmation(confirmation: co.veyra.softpos.payment.sdk.merchant.CreditConfirmation) {
        val received = confirmation.status == "RECEIVED"
        val message = if (received) {
            getString(R.string.funds_received_by_merchant_bank)
        } else {
            getString(R.string.credit_could_not_be_confirmed)
        }
        val waitedOn = confirmation.reference == awaitingCreditConfirmationRef
        if (waitedOn && currentPage == PAGE_PAYMENT_RESULT) {
            awaitingCreditConfirmationRef = null
            creditConfirmationLine.text = message
            creditConfirmationLine.setTextColor(
                getColor(if (received) R.color.success_green else R.color.error_red)
            )
            creditConfirmationLine.visibility = View.VISIBLE
            // The answer is on screen: start a FRESH hold (the wait had cancelled the one the
            // approval armed), then auto-return as usual. Done still dismisses immediately.
            scheduleAutoNavigate()
        } else {
            if (waitedOn) awaitingCreditConfirmationRef = null
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
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
        
        // Branch on the outcome the SDK was TOLD, not on a code we recognise. The old
        // `when (response.transactionCode)` had to know "99" meant pending — a code the SDK invented
        // because it had nowhere to put a status — and read anything unfamiliar as a failure, so a
        // still-settling `09`/`68`/`96` looked like a refusal to the cashier.
        val outcome = response.responseStatus?.name ?: when (response.transactionCode) {
            // Rows/responses from an older SDK build, which carried no status, keep working.
            "00" -> "APPROVED"
            "06", "09", "68", "96", "99" -> "PENDING"
            "91", "25" -> "FAILED"
            "" -> "FAILED" // no code at all: the payment never started (see response.sdkErrorCode)
            else -> "DECLINED"
        }
        when (outcome) {
            "APPROVED" -> {
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
                        response.merchantOrderId?.let { append("Order: $it\n") }
                    }
                )
                // The SDK minted the reference — adopt it, since the receipt, the status poll and
                // the credit watch all key off the value the gateway actually recorded.
                response.merchantTransactionReference?.let { lastOriginalTransactionReference = it }
                // The approval said the merchant's bank supports credit confirmation —
                // the SDK is now polling in the background, so the result page waits: show the
                // confirming line and let onCreditConfirmation flip it when the answer arrives.
                // Setting awaitingCreditConfirmationRef is what suppresses the hold below, so the
                // page cannot auto-return out from under the wait.
                if (response.isCreditConfirmationSupported == true) {
                    awaitingCreditConfirmationRef =
                        response.merchantTransactionReference ?: lastOriginalTransactionReference
                    creditConfirmationLine.text = getString(R.string.confirming_credit_with_merchant_bank)
                    creditConfirmationLine.setTextColor(getColor(R.color.warning_orange))
                    creditConfirmationLine.visibility = View.VISIBLE
                }
                showViewReceiptButton()
            }
            "PENDING" -> {
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
            "DECLINED", "FAILED" -> {
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

        // Hold the result for AUTO_NAVIGATE_DELAY_MS then return to Home — the default for EVERY
        // terminal outcome, approved/pending/declined/failed alike (Done dismisses immediately
        // throughout). The single exception is a sale waiting on beneficiary credit confirmation:
        // that page must not vanish mid-wait, so it holds indefinitely and showCreditConfirmation()
        // starts a fresh hold once the answer is on screen.
        if (awaitingCreditConfirmationRef == null) scheduleAutoNavigate()
    }
    
    private fun showPaymentResult(
        isSuccess: Boolean?,
        title: String,
        message: String,
        amountMinorUnits: Long,
        details: String
    ) {
        // A new result supersedes any credit-confirmation wait from the previous sale —
        // the approved branch re-shows the line when this sale supports it,
        // and the MPM watch is re-armed per settle.
        awaitingCreditConfirmationRef = null
        creditWatchJob?.cancel()
        creditConfirmationLine.visibility = View.GONE

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
                // The paying card's display name — shown on the merchant's copy only. Read off
                // EMV 5F20 on a tap, off the scanned QR on CPM, and carried by the gateway on
                // QR-MPM, so every rail can name the card that paid.
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
