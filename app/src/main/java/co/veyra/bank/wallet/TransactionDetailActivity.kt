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
        bindCreditConfirmation(tx)
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
