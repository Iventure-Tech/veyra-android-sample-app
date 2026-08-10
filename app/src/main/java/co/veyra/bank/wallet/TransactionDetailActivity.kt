package co.veyra.bank.wallet

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import co.veyra.bank.R
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import co.veyra.bank.databinding.ActivityTransactionDetailBinding
import co.veyra.wallet.sdk.VeyraWalletSdk
import co.veyra.wallet.sdk.TransactionSummary
import co.veyra.wallet.sdk.util.CurrencyUtils
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TransactionDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTransactionDetailBinding
    private var summary: TransactionSummary? = null

    // Refreshes the merchant-credited line while this screen is visible. A UI refresh only — the
    // SDK's own credit poll is app-scoped and unaffected by this screen's lifetime.
    private var creditWatchJob: Job? = null

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchQrScanner()
        else Toast.makeText(this, "Camera permission required to scan receipt", Toast.LENGTH_LONG).show()
    }

    private val qrScannerLauncher = registerForActivityResult(ScanContract()) { result ->
        val contents = result?.contents ?: return@registerForActivityResult
        val base64 = Base64.encodeToString(contents.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        processScannedReceipt(base64)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTransactionDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        // TransactionSummary is not java.io.Serializable — pass the summary
        // as JSON between screens (toJson/fromJson) instead of as an Intent Serializable extra.
        summary = intent.getStringExtra(EXTRA_SUMMARY)?.let { runCatching { TransactionSummary.fromJson(it) }.getOrNull() } ?: run {
            finish()
            return
        }

        bindSummary(summary!!)
        bindReceiptButtons(summary!!)
    }

    override fun onResume() {
        super.onResume()
        refreshSummaryFromStorage()
        summary?.let { bindReceiptButtons(it) }
        startCreditWatch()
    }

    override fun onPause() {
        super.onPause()
        creditWatchJob?.cancel()
        creditWatchJob = null
    }

    /**
     * Re-read the stored row while this screen is up, so a credit confirmation that lands during
     * the visit appears without navigating away and back.
     *
     * This is a **store read**, not a poll: the SDK owns the asking (app-scoped, for up to 30 days)
     * and keeps going whether or not this screen exists. Which is why cancelling in [onPause] is
     * safe — it ends a UI refresh, never a wait. Stops itself once the answer is terminal.
     */
    private fun startCreditWatch() {
        creditWatchJob?.cancel()
        val tx = summary ?: return
        if (tx.isCreditConfirmationSupported != true || tx.creditConfirmationStatus != null) return
        creditWatchJob = lifecycleScope.launch {
            while (isActive) {
                delay(CREDIT_WATCH_INTERVAL_MS)
                refreshSummaryFromStorage()
                if (summary?.creditConfirmationStatus != null) return@launch
            }
        }
    }

    private fun refreshSummaryFromStorage() {
        val current = summary ?: return
        val tur = current.tokenUniqueReference ?: return
        val hash = current.transactionHash ?: return
        val sdk = VeyraWalletSdk.getInstance() ?: return
        val fresh = sdk.tokenisationService.getTransactions(tur, 50)
            .firstOrNull { it.transactionHash == hash } ?: return
        summary = fresh
        bindSummary(fresh)
    }

    private fun bindSummary(tx: TransactionSummary) {
        binding.txDetailAmount.text = CurrencyUtils.formatAmount(tx.amountInMinorUnit, tx.transactionCurrencyCode)
        binding.txDetailMerchant.text = extractMerchantName(tx.merchantName)

        val address = tx.merchantName?.substringAfter('*', "")?.trim().orEmpty()
        if (address.isNotEmpty()) {
            binding.txDetailMerchantAddress.text = address
            binding.txDetailMerchantAddress.visibility = View.VISIBLE
        }

        val status = tx.authorizationStatus ?: "UNKNOWN"
        binding.txDetailStatus.text = status
        binding.txDetailStatus.setTextColor(statusColor(status))

        val (datePart, timePart) = parseDateTime(tx.localTransactionDateTime)
        binding.txDetailDate.text = datePart
        binding.txDetailTime.text = timePart

        tx.transactionCurrencyCode?.takeIf { it.isNotBlank() }?.let {
            binding.rowCurrency.visibility = View.VISIBLE
            binding.dividerCurrency.visibility = View.VISIBLE
            binding.txDetailCurrency.text = it
        }

        // How this wallet paid — legacy rows (null) show nothing rather than a guess.
        entryMethodLabel(tx.entryMethod)?.let {
            binding.rowEntryMethod.visibility = View.VISIBLE
            binding.txDetailEntryMethod.text = it
        }
        // Registered merchant location — present on MPM rows and gateway-reconciled CPM rows.
        tx.merchantLocation?.takeIf { it.isNotBlank() }?.let {
            binding.rowMerchantLocation.visibility = View.VISIBLE
            binding.txDetailMerchantLocation.text = it
        }
        // Outcome cause + response code, verbatim from the rail that resolved this row;
        // legacy/unresolved rows carry neither and show nothing rather than a guess.
        tx.responseStatusReason?.takeIf { it.isNotBlank() }?.let {
            binding.rowReason.visibility = View.VISIBLE
            binding.txDetailReason.text = it
        }
        tx.responseCode?.takeIf { it.isNotBlank() }?.let {
            binding.rowResponseCode.visibility = View.VISIBLE
            binding.txDetailResponseCode.text = it
        }
        bindStatusCheckButton(tx)
        bindCreditConfirmation(tx)
    }

    /**
     * "Check status" — the customer's manual ask beside the SDK's own background poll.
     *
     * Visible **only while the row is still open** (`PENDING`, or a status this build does not
     * recognise — unknown values are carried raw and are equally unresolved). A settled row has
     * nothing left to ask, and offering the action would imply its outcome might still change.
     *
     * This is why an unresolved row is shown in wallet history at all: the SDK stops polling after
     * 30 days, and this button is the only route to an answer thereafter.
     */
    private fun bindStatusCheckButton(tx: TransactionSummary) {
        val hash = tx.transactionHash
        val open = tx.authorizationStatus?.trim()?.uppercase() !in setOf("APPROVED", "DECLINED", "FAILED")
        if (!open || hash.isNullOrBlank()) {
            binding.btnCheckStatus.visibility = View.GONE
            return
        }
        binding.btnCheckStatus.visibility = View.VISIBLE
        binding.btnCheckStatus.isEnabled = true
        binding.btnCheckStatus.setOnClickListener { checkStatus(hash) }
    }

    private fun checkStatus(transactionHash: String) {
        val sdk = VeyraWalletSdk.getInstance() ?: return
        // The SDK deliberately has no throttle — the screen disables its own button while in flight.
        binding.btnCheckStatus.isEnabled = false
        binding.txDetailStatusCheckNote.text = getString(R.string.status_check_in_flight)
        binding.txDetailStatusCheckNote.visibility = View.VISIBLE

        lifecycleScope.launch {
            runCatching {
                sdk.tokenisationService.refreshTransactionStatus(transactionHash)
            }.onSuccess { fresh ->
                if (fresh != null) {
                    summary = fresh
                    bindSummary(fresh)
                }
                val resolved = fresh?.authorizationStatus?.trim()?.uppercase() in
                    setOf("APPROVED", "DECLINED", "FAILED")
                if (resolved) {
                    // bindSummary has already re-rendered the status line and hidden this button.
                    binding.txDetailStatusCheckNote.visibility = View.GONE
                } else {
                    // Still unsettled — the honest answer, never a failure.
                    binding.txDetailStatusCheckNote.text =
                        getString(R.string.status_check_still_pending)
                    binding.btnCheckStatus.isEnabled = true
                }
            }.onFailure { e ->
                // An offline device gets the one piece of advice that helps. The row is untouched
                // either way — a failed check is never an outcome.
                val offline = e.message?.contains("NO_NETWORK_CONNECTION") == true
                binding.txDetailStatusCheckNote.text = if (offline) {
                    getString(R.string.status_check_offline)
                } else {
                    getString(R.string.status_check_failed, e.message ?: "unknown error")
                }
                binding.btnCheckStatus.isEnabled = true
            }
        }
    }

    /**
     * The merchant-credited indicator — did the money actually reach the merchant's bank?
     *
     * `isCreditConfirmationSupported` is the whole gate: false or null means the rail does not
     * exist for this transaction, so we render **nothing at all**. Absence of the line means "we
     * cannot ask", and must never be shown as "the merchant was not paid" — which is also why the
     * 30-day give-up reads "could not confirm" rather than anything stronger.
     *
     * The SDK owns the polling (app-scoped, exponential backoff, up to 30 days) and there is no
     * callback: this screen is a renderer over the stored row, re-read in [onResume]. Leaving the
     * screen changes nothing about the wait.
     */
    private fun bindCreditConfirmation(tx: TransactionSummary) {
        if (tx.isCreditConfirmationSupported != true) {
            binding.txDetailCreditConfirmation.visibility = View.GONE
            binding.txDetailCreditDetail.visibility = View.GONE
            return
        }
        val (text, color) = when (tx.creditConfirmationStatus) {
            "RECEIVED" -> getString(R.string.wallet_credit_received) to Color.parseColor("#4CAF50")
            "UNABLE_TO_CONFIRM" -> getString(R.string.wallet_credit_unconfirmed) to Color.parseColor("#AAAAAA")
            // Null = no answer yet, which with the flag true is the in-flight state.
            else -> getString(R.string.wallet_credit_confirming) to Color.parseColor("#FFA726")
        }
        binding.txDetailCreditConfirmation.text = text
        binding.txDetailCreditConfirmation.setTextColor(color)
        binding.txDetailCreditConfirmation.visibility = View.VISIBLE

        // The bank's own description of the credit — present on RECEIVED only.
        val detail = listOfNotNull(
            tx.creditedAt?.takeIf { it.isNotBlank() },
            tx.bankReference?.takeIf { it.isNotBlank() }
                ?.let { getString(R.string.wallet_credit_bank_reference, it) },
        ).joinToString(" · ")
        binding.txDetailCreditDetail.text = detail
        binding.txDetailCreditDetail.visibility = if (detail.isEmpty()) View.GONE else View.VISIBLE

        bindCreditCheckButton(tx)
    }

    /**
     * "Check merchant credit" — the customer's manual ask beside the SDK's own background credit
     * sweep.
     *
     * Visible on exactly the predicate the SDK enforces internally: the payment is **approved**, the
     * merchant's bank is on the confirmation rail, and the credit is not already `RECEIVED`. That
     * deliberately **includes** a row the 30-day sweep gave up on and marked `UNABLE_TO_CONFIRM` —
     * the give-up means "we stopped asking", not "the merchant was not paid", so that is the row
     * this button matters most for. A later `RECEIVED` replaces it.
     *
     * A convenience, never the mechanism: the SDK keeps asking whether or not this screen exists.
     */
    private fun bindCreditCheckButton(tx: TransactionSummary) {
        val hash = tx.transactionHash
        val eligible = tx.authorizationStatus == "APPROVED" &&
            tx.isCreditConfirmationSupported == true &&
            tx.creditConfirmationStatus != "RECEIVED" &&
            !hash.isNullOrBlank()
        if (!eligible) {
            binding.btnCheckMerchantCredit.visibility = View.GONE
            return
        }
        binding.btnCheckMerchantCredit.visibility = View.VISIBLE
        binding.btnCheckMerchantCredit.isEnabled = true
        binding.btnCheckMerchantCredit.setOnClickListener { checkMerchantCredit(hash!!) }
    }

    private fun checkMerchantCredit(transactionHash: String) {
        val sdk = VeyraWalletSdk.getInstance() ?: return
        // The SDK deliberately has no throttle — the screen disables its own button.
        binding.btnCheckMerchantCredit.isEnabled = false
        binding.txDetailCreditCheckNote.text = getString(R.string.credit_check_in_flight)
        binding.txDetailCreditCheckNote.visibility = View.VISIBLE

        lifecycleScope.launch {
            runCatching {
                sdk.tokenisationService.refreshCreditConfirmation(transactionHash)
            }.onSuccess { fresh ->
                if (fresh != null) {
                    summary = fresh
                    bindSummary(fresh)
                }
                if (fresh?.creditConfirmationStatus == "RECEIVED") {
                    // bindSummary has already re-rendered the credit line and hidden the button.
                    binding.txDetailCreditCheckNote.visibility = View.GONE
                } else {
                    // Not confirmed **yet** — the honest answer, never "not received".
                    binding.txDetailCreditCheckNote.text =
                        getString(R.string.credit_check_still_unconfirmed)
                    binding.btnCheckMerchantCredit.isEnabled = true
                }
            }.onFailure { e ->
                // An offline device gets the one piece of advice that helps. The row is untouched
                // either way — a failed check is never a settlement answer.
                binding.txDetailCreditCheckNote.text =
                    if (e.message?.contains("NO_NETWORK_CONNECTION") == true) {
                        getString(R.string.credit_check_offline)
                    } else {
                        getString(R.string.credit_check_failed, e.message ?: "unknown error")
                    }
                binding.btnCheckMerchantCredit.isEnabled = true
            }
        }
    }

    private fun entryMethodLabel(entryMethod: String?): String? = when (entryMethod) {
        TransactionSummary.ENTRY_TAP -> getString(R.string.entry_method_tap)
        TransactionSummary.ENTRY_QR_GENERATED -> getString(R.string.entry_method_qr_generated)
        TransactionSummary.ENTRY_QR_SCANNED -> getString(R.string.entry_method_qr_scanned)
        else -> null
    }

    private fun bindReceiptButtons(tx: TransactionSummary) {
        val hash = tx.transactionHash
        val sdk = VeyraWalletSdk.getInstance()
        val linkedReceipt = if (hash != null) sdk?.tokenisationService?.getReceiptForTransaction(hash) else null

        if (linkedReceipt != null) {
            binding.btnViewReceipt.visibility = View.VISIBLE
            binding.btnViewReceipt.setOnClickListener {
                startActivity(ReceiptDetailActivity.intent(this, linkedReceipt))
            }
        } else {
            binding.btnViewReceipt.visibility = View.GONE
        }

        // Only show scan button when there's a hash to link against
        binding.btnScanReceipt.visibility = if (hash != null) View.VISIBLE else View.GONE
        binding.btnScanReceipt.setOnClickListener { startScanReceipt() }
    }

    private fun startScanReceipt() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchQrScanner()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchQrScanner() {
        // Portrait scanner (same fix as scan-to-pay): the zxing default
        // CaptureActivity is sensorLandscape — route through PortraitCaptureActivity so the
        // receipt scan preview stays upright like every other scan screen in the app.
        qrScannerLauncher.launch(
            ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setBeepEnabled(true)
                .setOrientationLocked(true)
                .setCaptureActivity(PortraitCaptureActivity::class.java),
        )
    }

    private fun processScannedReceipt(base64Payload: String) {
        val sdk = VeyraWalletSdk.getInstance() ?: return
        binding.btnScanReceipt.isEnabled = false
        // This scan was launched FROM this transaction — the SDK must reject a receipt
        // belonging to any other transaction instead of silently linking it elsewhere.
        sdk.tokenisationService.processReceipt(base64Payload, expectedTransactionHash = summary?.transactionHash) { result ->
            binding.btnScanReceipt.isEnabled = true
            result.fold(
                onSuccess = { receipt ->
                    Toast.makeText(this, "Receipt saved", Toast.LENGTH_SHORT).show()
                    // Show the receipt immediately
                    startActivity(ReceiptDetailActivity.intent(this, receipt))
                    // Refresh button state
                    summary?.let { bindReceiptButtons(it) }
                },
                onFailure = { error ->
                    Toast.makeText(this, "Could not process receipt: ${error.message}", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    private fun parseDateTime(isoDateTime: String?): Pair<String, String> {
        if (isoDateTime.isNullOrBlank()) return "—" to "—"
        return try {
            val date: Date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
                .parse(isoDateTime) ?: return isoDateTime to ""
            SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date) to
                SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(date)
        } catch (e: Exception) {
            isoDateTime to ""
        }
    }

    private fun statusColor(status: String): Int = when (status.uppercase()) {
        "APPROVED" -> Color.parseColor("#4CAF50")
        "DECLINED", "FAILED" -> Color.parseColor("#EF5350")
        "PENDING" -> Color.parseColor("#FFA726")
        else -> Color.parseColor("#AAAAAA")
    }

    companion object {
        private const val EXTRA_SUMMARY = "extra_summary"

        /** How often the visible detail screen re-reads the stored row for a credit answer. */
        private const val CREDIT_WATCH_INTERVAL_MS = 3000L

        fun intent(context: Context, summary: TransactionSummary): Intent =
            Intent(context, TransactionDetailActivity::class.java).putExtra(EXTRA_SUMMARY, summary.toJson())
    }
}
