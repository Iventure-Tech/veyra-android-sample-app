package co.veyra.bank.wallet

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import co.veyra.bank.R
import co.veyra.wallet.sdk.TransactionReceipt
import co.veyra.wallet.sdk.util.CurrencyUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReceiptDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_receipt_detail)

        // TransactionReceipt is not java.io.Serializable — pass
        // it as JSON between screens (toJson/fromJson) instead of as an Intent Serializable extra.
        val receipt = intent.getStringExtra(EXTRA_RECEIPT)?.let { runCatching { TransactionReceipt.fromJson(it) }.getOrNull() } ?: run {
            finish()
            return
        }

        bindReceipt(receipt)
    }

    private fun bindReceipt(r: TransactionReceipt) {
        // Merchant header
        findViewById<TextView>(R.id.receiptMerchantName).text = r.merchantName

        r.merchantAddress?.takeIf { it.isNotBlank() }?.let {
            val tv = findViewById<TextView>(R.id.receiptMerchantAddress)
            tv.text = it
            tv.visibility = View.VISIBLE
        }

        r.merchantId?.takeIf { it.isNotBlank() }?.let {
            val tv = findViewById<TextView>(R.id.receiptMerchantId)
            tv.text = "ID: $it"
            tv.visibility = View.VISIBLE
        }

        // Date and time — split from transactionTime if it contains a space, otherwise show as-is
        val (datePart, timePart) = splitDateTime(r.transactionTime)
        findViewById<TextView>(R.id.receiptDate).text = datePart
        findViewById<TextView>(R.id.receiptTime).text = timePart

        // Transaction type
        findViewById<TextView>(R.id.receiptType).text = r.transactionType

        // Amount
        val amountCents = r.totalAmount.toIntOrNull() ?: 0
        val currencyCode = r.currency?.filter { it.isDigit() }?.take(4)?.takeIf { it.length in 3..4 }
        findViewById<TextView>(R.id.receiptAmount).text = if (amountCents > 0) {
            CurrencyUtils.formatAmountWithCode(amountCents, currencyCode)
        } else {
            r.totalAmountFormatted.ifBlank { r.totalAmount }
        }

        // Currency label
        r.currency?.takeIf { it.isNotBlank() }?.let {
            val tv = findViewById<TextView>(R.id.receiptCurrency)
            tv.text = it
            tv.visibility = View.VISIBLE
        }

        // Status with color
        val statusTv = findViewById<TextView>(R.id.receiptStatus)
        val status = r.transactionStatus.ifBlank { "—" }
        statusTv.text = status
        statusTv.setTextColor(statusColor(status))

        // Masked card
        r.maskedToken?.takeIf { it.isNotBlank() }?.let {
            findViewById<LinearLayout>(R.id.rowCard).visibility = View.VISIBLE
            findViewById<TextView>(R.id.receiptCard).text = it
        }

        // Merchant transaction reference
        r.merchantTransactionReference?.takeIf { it.isNotBlank() }?.let {
            findViewById<LinearLayout>(R.id.rowRef).visibility = View.VISIBLE
            findViewById<TextView>(R.id.receiptRef).text = it
        }

        // Transaction ID
        r.transactionId?.takeIf { it.isNotBlank() }?.let {
            findViewById<LinearLayout>(R.id.rowTxnId).visibility = View.VISIBLE
            findViewById<TextView>(R.id.receiptTxnId).text = it
        }

        // CDCVM
        val cdcvmApproved = r.cdcvmApprovedByWallet
        val cdcvmOutcome = r.cdcvmOutcome?.takeIf { it.isNotBlank() }
        if (cdcvmApproved != null || cdcvmOutcome != null) {
            findViewById<LinearLayout>(R.id.rowCdcvm).visibility = View.VISIBLE
            val label = when {
                cdcvmOutcome != null -> cdcvmOutcome
                cdcvmApproved == true -> "YES"
                else -> "NO"
            }
            findViewById<TextView>(R.id.receiptCdcvm).text = label
        }
    }

    private fun splitDateTime(transactionTime: String): Pair<String, String> {
        // Try "yyyy-MM-dd HH:mm:ss" split on space
        val spaceIdx = transactionTime.indexOf(' ')
        if (spaceIdx > 0) {
            return transactionTime.substring(0, spaceIdx) to transactionTime.substring(spaceIdx + 1)
        }
        // Try to parse ISO 8601 and reformat
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val date = sdf.parse(transactionTime.take(19))
            if (date != null) {
                SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date) to
                    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(date)
            } else {
                transactionTime to ""
            }
        } catch (e: Exception) {
            transactionTime to ""
        }
    }

    private fun statusColor(status: String): Int = when (status.uppercase()) {
        "APPROVED" -> Color.parseColor("#2E7D32")
        "DECLINED", "FAILED" -> Color.parseColor("#C62828")
        "PENDING" -> Color.parseColor("#E65100")
        else -> Color.parseColor("#444444")
    }

    companion object {
        private const val EXTRA_RECEIPT = "extra_receipt"

        fun intent(context: Context, receipt: TransactionReceipt): Intent {
            return Intent(context, ReceiptDetailActivity::class.java)
                .putExtra(EXTRA_RECEIPT, receipt.toJson())
        }
    }
}
