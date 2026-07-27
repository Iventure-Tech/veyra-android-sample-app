package co.veyra.bank.wallet

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import co.veyra.bank.R
import co.veyra.bank.databinding.ActivityReceiptsBinding
import co.veyra.wallet.sdk.VeyraWalletSdk
import co.veyra.wallet.sdk.TransactionReceipt
import co.veyra.wallet.sdk.util.CurrencyUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Displays scanned transaction receipts (from QR scans).
 */
class ReceiptsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReceiptsBinding
    private val dateFormat = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReceiptsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val sdk = VeyraWalletSdk.getInstance() ?: run {
            finish()
            return
        }

        val receipts = sdk.tokenisationService.getLastReceipts(limit = 100)

        if (receipts.isEmpty()) {
            binding.emptyText.visibility = View.VISIBLE
            binding.receiptsRecycler.visibility = View.GONE
        } else {
            binding.emptyText.visibility = View.GONE
            binding.receiptsRecycler.visibility = View.VISIBLE
            binding.receiptsRecycler.layoutManager = LinearLayoutManager(this)
            binding.receiptsRecycler.adapter = ReceiptsAdapter(receipts, dateFormat) { receipt ->
                startActivity(ReceiptDetailActivity.intent(this, receipt))
            }
        }
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, ReceiptsActivity::class.java)
        }
    }
}

private class ReceiptsAdapter(
    private val receipts: List<TransactionReceipt>,
    private val dateFormat: SimpleDateFormat,
    private val onItemClick: (TransactionReceipt) -> Unit
) : RecyclerView.Adapter<ReceiptsAdapter.ViewHolder>() {

    override fun getItemCount(): Int = receipts.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_receipt, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val receipt = receipts[position]
        holder.itemView.setOnClickListener { onItemClick(receipt) }
        holder.merchant.text = receipt.merchantName
        val amountCents = receipt.totalAmount.toIntOrNull() ?: 0
        val currencyCode = receipt.currency?.filter { it.isDigit() }?.take(4)?.takeIf { it.length in 3..4 }
        holder.amount.text = if (amountCents > 0) {
            CurrencyUtils.formatAmountWithCode(amountCents, currencyCode)
        } else {
            receipt.totalAmountFormatted.ifBlank { receipt.totalAmount }
        }
        val storedAt = receipt.storedAtMs
        holder.date.text = if (storedAt > 0) dateFormat.format(Date(storedAt)) else receipt.transactionTime
        holder.status.text = receipt.transactionStatus.ifBlank { "—" }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val merchant: TextView = itemView.findViewById(R.id.receiptMerchant)
        val amount: TextView = itemView.findViewById(R.id.receiptAmount)
        val date: TextView = itemView.findViewById(R.id.receiptDate)
        val status: TextView = itemView.findViewById(R.id.receiptStatus)
    }
}
