package co.veyra.bank.wallet

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import co.veyra.bank.HomeActivity
import co.veyra.bank.R
import co.veyra.softpos.payment.sdk.CurrencyUtils
import co.veyra.wallet.sdk.VeyraWalletSdk
import co.veyra.wallet.sdk.api.mpm.MpmScanResult
import co.veyra.wallet.sdk.api.mpm.VerifiedPaymentContext
import com.google.android.material.button.MaterialButton
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

/**
 * Scan-to-pay (MPM QR rail): scan the merchant's QR → the SDK verifies the
 * gateway signature + expiry ON-DEVICE → confirm the verified merchant + amount → pay with the
 * active token (the SDK builds the tap-shaped proof and pushes online). A rejected scan never
 * reaches the confirm screen.
 */
class ScanToPayActivity : AppCompatActivity() {

    private lateinit var sdk: VeyraWalletSdk
    private var verified: VerifiedPaymentContext? = null

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        val payload = result.contents
        if (payload == null) {
            finish() // scanner dismissed
        } else {
            handleScan(payload)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        co.veyra.bank.VeyraBank.ensureInitialized(this)
        sdk = VeyraWalletSdk.initialize(this, co.veyra.bank.VeyraBank.walletConfig(this), activity = this)
        setContentView(R.layout.activity_scan_to_pay)

        findViewById<MaterialButton>(R.id.cancelScanButton).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.doneButton).setOnClickListener { returnToHome() }
        findViewById<MaterialButton>(R.id.payButton).setOnClickListener { pay() }

        scanLauncher.launch(
            ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt(getString(R.string.scan_to_pay))
                .setBeepEnabled(false)
                // Portrait scanner: the zxing default CaptureActivity is sensorLandscape.
                .setCaptureActivity(PortraitCaptureActivity::class.java)
                .setOrientationLocked(true),
        )
    }

    private fun handleScan(payload: String) {
        when (val scan = sdk.tokenisationService.inspectScannedQr(payload)) {
            is MpmScanResult.Verified -> showConfirm(scan.context)
            is MpmScanResult.Rejected -> showResult(
                success = false,
                title = getString(R.string.declined),
                message = when (scan.reason) {
                    MpmScanResult.Reason.EXPIRED -> getString(R.string.scan_expired)
                    else -> getString(R.string.scan_rejected)
                },
            )
        }
    }

    private fun showConfirm(context: VerifiedPaymentContext) {
        verified = context
        findViewById<TextView>(R.id.scanStatusText).visibility = View.GONE
        findViewById<View>(R.id.resultGroup).visibility = View.GONE
        findViewById<View>(R.id.confirmGroup).visibility = View.VISIBLE
        findViewById<TextView>(R.id.merchantName).text = context.merchantName
        findViewById<TextView>(R.id.merchantCity).text = context.merchantCity ?: ""
        findViewById<TextView>(R.id.payAmount).text =
            CurrencyUtils.formatAmount(context.amountMinorUnits, context.currencyNumeric)
    }

    private fun pay() {
        val context = verified ?: return
        // CDCVM: the OS biometric sheet is the authorization gesture — bound to the
        // merchant + amount in the subtitle. Cancel/failure stays on this confirm screen.
        sdk.tokenisationService.authenticateForScannedPayment(
            activity = this,
            title = getString(R.string.scan_pay_auth_title),
            subtitle = getString(
                R.string.scan_pay_auth_subtitle,
                CurrencyUtils.formatAmount(context.amountMinorUnits, context.currencyNumeric),
                context.merchantName,
            ),
        ) { auth ->
            if (auth.isSuccess) pushPayment(context)
            // else: stay on the confirm screen; the system sheet already told the user.
        }
    }

    private fun pushPayment(context: VerifiedPaymentContext) {
        val payButton = findViewById<MaterialButton>(R.id.payButton)
        payButton.isEnabled = false
        payButton.text = getString(R.string.scan_paying)
        sdk.tokenisationService.payScannedContext(context) { result ->
            result.fold(
                onSuccess = { outcome ->
                    showResult(
                        success = outcome.approved,
                        title = getString(
                            if (outcome.approved) R.string.payment_successful else R.string.declined,
                        ),
                        message = "Ref: ${context.txRef}\nResponse: ${outcome.responseCode ?: "-"}" +
                            (outcome.message?.let { "\n$it" } ?: ""),
                    )
                },
                onFailure = { error ->
                    payButton.isEnabled = true
                    payButton.text = getString(R.string.scan_pay_confirm)
                    showResult(
                        success = false,
                        title = getString(R.string.declined),
                        message = error.message ?: "Payment failed",
                    )
                },
            )
        }
    }

    private fun showResult(success: Boolean, title: String, message: String) {
        findViewById<TextView>(R.id.scanStatusText).visibility = View.GONE
        findViewById<View>(R.id.confirmGroup).visibility = View.GONE
        findViewById<View>(R.id.resultGroup).visibility = View.VISIBLE
        findViewById<ImageView>(R.id.resultIcon).setImageResource(
            if (success) R.drawable.success_tick_only else R.drawable.error_x_only,
        )
        findViewById<TextView>(R.id.resultTitle).apply {
            text = title
            setTextColor(getColor(if (success) R.color.success_green else R.color.error_red))
        }
        findViewById<TextView>(R.id.resultMessage).text = message
        // Any terminal outcome auto-returns to the main menu after a short window,
        // mirroring the SoftPOS Get-paid result. Done returns immediately.
        scheduleAutoReturn()
    }

    /** Return to the main menu (Home), clearing the Pay/Scan stack. */
    private fun returnToHome() {
        cancelAutoReturn()
        startActivity(
            Intent(this, HomeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        finish()
    }

    private val autoReturnHandler = Handler(Looper.getMainLooper())
    private var autoReturnRunnable: Runnable? = null

    private fun scheduleAutoReturn() {
        cancelAutoReturn()
        autoReturnRunnable = Runnable { returnToHome() }
        autoReturnHandler.postDelayed(autoReturnRunnable!!, AUTO_RETURN_DELAY_MS)
    }

    private fun cancelAutoReturn() {
        autoReturnRunnable?.let { autoReturnHandler.removeCallbacks(it) }
        autoReturnRunnable = null
    }

    override fun onDestroy() {
        cancelAutoReturn()
        super.onDestroy()
    }

    private companion object {
        /** Idle window before a completed scan-to-pay returns to the main menu. */
        const val AUTO_RETURN_DELAY_MS = 5000L
    }
}
