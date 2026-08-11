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
                // Refused on-device before anything was sent — no payment exists to be pending.
                state = ResultState.REFUSED,
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
        // No authentication call here. payScannedContext raises the OS biometric sheet itself —
        // bound to the merchant and amount — and a cancelled or failed gesture comes back as an
        // AUTH_* failure on the same callback, leaving the customer on this confirm screen.
        pushPayment(context)
    }

    private fun pushPayment(context: VerifiedPaymentContext) {
        val payButton = findViewById<MaterialButton>(R.id.payButton)
        payButton.isEnabled = false
        payButton.text = getString(R.string.scan_paying)
        sdk.tokenisationService.payScannedContext(context) { result ->
            result.fold(
                onSuccess = { outcome ->
                    // The push answers with a status, not just a code, and PENDING is a real
                    // answer — the payer must not be told they were refused when the SDK has
                    // stored the payment as unresolved and is still polling for it.
                    val state = paymentState(outcome.responseStatus)
                    showResult(
                        state = state,
                        title = getString(
                            when (state) {
                                ResultState.APPROVED -> R.string.payment_successful
                                ResultState.PENDING -> R.string.payment_pending
                                ResultState.REFUSED -> R.string.declined
                            },
                        ),
                        message = "Ref: ${context.txRef}\nResponse: ${outcome.responseCode ?: "-"}" +
                            (outcome.message?.let { "\n$it" } ?: "") +
                            (if (state == ResultState.PENDING) "\n${getString(R.string.payment_pending_message)}" else ""),
                    )
                },
                onFailure = { error ->
                    payButton.isEnabled = true
                    payButton.text = getString(R.string.scan_pay_confirm)
                    showResult(
                        // A pre-dispatch refusal or a transport failure: the SDK recorded nothing,
                        // so there is no unresolved payment to report as pending.
                        state = ResultState.REFUSED,
                        title = getString(R.string.declined),
                        message = error.message ?: "Payment failed",
                    )
                },
            )
        }
    }

    /**
     * How the result page reads an outcome. Three states, because the gateway states three kinds
     * of thing: it approved, it refused (declined/failed), or it does not know yet — and the third
     * is neither of the first two. Anything not stated final is PENDING, which is also exactly what
     * the SDK stored and keeps polling.
     */
    private enum class ResultState { APPROVED, PENDING, REFUSED }

    private fun paymentState(responseStatus: String?): ResultState =
        when (responseStatus?.trim()?.uppercase()) {
            "APPROVED" -> ResultState.APPROVED
            "DECLINED", "FAILED" -> ResultState.REFUSED
            else -> ResultState.PENDING
        }

    private fun showResult(state: ResultState, title: String, message: String) {
        findViewById<TextView>(R.id.scanStatusText).visibility = View.GONE
        findViewById<View>(R.id.confirmGroup).visibility = View.GONE
        findViewById<View>(R.id.resultGroup).visibility = View.VISIBLE
        findViewById<ImageView>(R.id.resultIcon).setImageResource(
            when (state) {
                ResultState.APPROVED -> R.drawable.success_tick_only
                ResultState.PENDING -> R.drawable.pending_clock_only
                ResultState.REFUSED -> R.drawable.error_x_only
            },
        )
        findViewById<TextView>(R.id.resultTitle).apply {
            text = title
            setTextColor(getColor(
                when (state) {
                    ResultState.APPROVED -> R.color.success_green
                    ResultState.PENDING -> R.color.warning_orange
                    ResultState.REFUSED -> R.color.error_red
                },
            ))
        }
        findViewById<TextView>(R.id.resultMessage).text = message
        // Any terminal outcome holds here, then returns to the main menu, mirroring the SoftPOS
        // Get-paid result. Done returns immediately, throughout the hold.
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
        /**
         * How long a completed scan-to-pay holds its result before returning to the main menu;
         * Done dismisses immediately throughout. 60s, matching the SoftPOS Get-paid result page:
         * the payer should be able to read their own outcome rather than have it snatched away.
         */
        const val AUTO_RETURN_DELAY_MS = 60_000L
    }
}
