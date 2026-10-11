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
import co.veyra.bank.CancelAction
import co.veyra.bank.R
import co.veyra.bank.cancelAction
import co.veyra.bank.cancelRefusedTooLate
import co.veyra.bank.outcomeText
import co.veyra.common.outcome.OutcomeReason
import co.veyra.common.outcome.OutcomeStatus
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

    /** True from Pay until the payment's outcome arrives. */
    private var paying = false

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
        sdk = VeyraWalletSdk.initialize(this, co.veyra.bank.VeyraBank.customerId(this), co.veyra.bank.VeyraBank.walletConfig(this), co.veyra.bank.VeyraBank.provider(), activity = this)
        setContentView(R.layout.activity_scan_to_pay)

        findViewById<MaterialButton>(R.id.cancelScanButton).setOnClickListener { cancel() }
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
        // bound to the merchant and amount — and a cancelled or failed gesture comes back as a
        // NOT_SENT / AUTH_* outcome on the same callback, leaving the customer on this confirm screen.
        pushPayment(context)
    }

    /**
     * Cancel: before Pay (or after a not-sent attempt), just leave. While paying, cancel the payment
     * by the order ID signed into the QR — possible only while it is still on the phone:
     *  - `CANCELLED`: the pay callback receives `NOT_SENT` / `CANCELLED` and leaves the screen.
     *  - `TOO_LATE_TO_CANCEL_REQUEST_ALREADY_SENT`: say it can no longer be cancelled; keep waiting.
     *  - nothing in progress for that order ID: leave.
     * A QR with no order ID cannot be cancelled once paying, so Cancel is disabled for it then.
     */
    private fun cancel() {
        val orderId = verified?.merchantOrderId
        if (!paying || orderId.isNullOrBlank()) return finish()
        val answer = sdk.tokenisationService.cancelPayment(orderId)
        when (cancelAction(answer)) {
            CancelAction.AWAIT_CANCELLED_OUTCOME -> Unit
            CancelAction.KEEP_WAITING -> if (cancelRefusedTooLate(answer)) showReason(getString(R.string.cancel_too_late))
            CancelAction.LEAVE -> finish()
        }
    }

    private fun setPaying(value: Boolean, context: VerifiedPaymentContext) {
        paying = value
        val payButton = findViewById<MaterialButton>(R.id.payButton)
        payButton.isEnabled = !value
        payButton.text = getString(if (value) R.string.scan_paying else R.string.scan_pay_confirm)
        // Nothing to name the payment by: a QR without an order ID can't be cancelled mid-payment.
        findViewById<MaterialButton>(R.id.cancelScanButton).isEnabled =
            !value || !context.merchantOrderId.isNullOrBlank()
    }

    private fun pushPayment(context: VerifiedPaymentContext) {
        setPaying(true, context)
        sdk.tokenisationService.payScannedContext(context) { result ->
            setPaying(false, context)
            // The Result fails only on a setup mistake (VeyraSetupException) — a bug in this app's
            // integration, found in development. Every refusal is an outcome.
            val outcome = result.getOrElse { setupError ->
                showReason(setupError.message ?: getString(R.string.error_occurred))
                return@payScannedContext
            }
            if (outcome.responseStatusReason == OutcomeReason.CANCELLED) {
                // Cancelled before it left the phone: nothing was paid — leave.
                finish()
                return@payScannedContext
            }
            if (outcome.responseStatus == OutcomeStatus.NOT_SENT) {
                // Nothing reached the gateway, so nothing was charged: stay on the confirm screen
                // and say what to fix (AUTH_CANCELLED, ONLINE_REQUIRED, NO_NETWORK_CONNECTION…).
                // Paying again pays this same QR.
                showReason(
                    if (outcome.responseStatusReason == "DEVICE_NOT_BOUND") {
                        // Card added on another device: retrying can never succeed here.
                        getString(R.string.device_not_bound_refusal)
                    } else {
                        outcomeText(outcome.responseStatusReason, outcome.message, getString(R.string.error_occurred))
                    },
                )
                return@payScannedContext
            }
            // The push answers with a status, not just a code, and PENDING is a real answer — the
            // payer must not be told they were refused when the SDK has stored the payment as
            // unresolved and is still polling for it.
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
                message = listOf(
                    "Ref: ${context.txRef}",
                    "Response: ${outcome.responseCode ?: "-"}",
                    outcomeText(outcome.responseStatusReason, outcome.message, ""),
                    if (state == ResultState.PENDING) getString(R.string.payment_pending_message) else "",
                ).filter { it.isNotBlank() }.joinToString("\n"),
            )
        }
    }

    /** A refusal before anything was sent: shown above the still-visible confirm screen. */
    private fun showReason(text: String) {
        findViewById<TextView>(R.id.scanStatusText).apply {
            this.text = text
            visibility = View.VISIBLE
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
            OutcomeStatus.APPROVED -> ResultState.APPROVED
            OutcomeStatus.DECLINED, OutcomeStatus.FAILED -> ResultState.REFUSED
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
