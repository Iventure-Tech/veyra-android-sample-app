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
import co.veyra.bank.databinding.ActivityTransactionDetailBinding
import co.veyra.wallet.sdk.VeyraWalletSdk
import co.veyra.wallet.sdk.TransactionSummary
import co.veyra.wallet.sdk.util.CurrencyUtils
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TransactionDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTransactionDetailBinding
    private var summary: TransactionSummary? = null

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

        fun intent(context: Context, summary: TransactionSummary): Intent =
            Intent(context, TransactionDetailActivity::class.java).putExtra(EXTRA_SUMMARY, summary.toJson())
    }
}
