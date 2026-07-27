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
import co.veyra.bank.databinding.ActivityTransactionHistoryBinding
import co.veyra.wallet.sdk.VeyraWalletSdk
import co.veyra.wallet.sdk.TransactionSummary
import co.veyra.wallet.sdk.util.CurrencyUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Displays the last 50 transactions for a card (from SDK [TokenisationService.getTransactions]).
 */
class TransactionHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTransactionHistoryBinding
    private var tokenUniqueReference: String? = null
    private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTransactionHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        tokenUniqueReference = intent.getStringExtra(EXTRA_TOKEN_UNIQUE_REFERENCE) ?: run {
            finish()
            return
        }

        binding.transactionsRecycler.layoutManager = LinearLayoutManager(this)
    }

    override fun onResume() {
        super.onResume()
        loadTransactions()
    }

    private fun loadTransactions() {
        val tur = tokenUniqueReference ?: return
        val sdk = VeyraWalletSdk.getInstance() ?: return
        val transactions = sdk.tokenisationService.getTransactions(tur, 50)
        if (transactions.isEmpty()) {
            binding.emptyText.visibility = View.VISIBLE
            binding.transactionsRecycler.visibility = View.GONE
        } else {
            binding.emptyText.visibility = View.GONE
            binding.transactionsRecycler.visibility = View.VISIBLE
            binding.transactionsRecycler.adapter = TransactionHistoryAdapter(transactions, dateFormat) { summary ->
                startActivity(TransactionDetailActivity.intent(this, summary))
            }
        }
    }

    companion object {
        private const val EXTRA_TOKEN_UNIQUE_REFERENCE = "token_unique_reference"

        fun intent(context: Context, tokenUniqueReference: String): Intent {
            return Intent(context, TransactionHistoryActivity::class.java).apply {
                putExtra(EXTRA_TOKEN_UNIQUE_REFERENCE, tokenUniqueReference)
            }
        }
    }
}

internal fun extractMerchantName(raw: String?): String =
    raw?.substringBefore('*')?.trim().orEmpty()

private fun parseLocalTransactionDateMs(isoDateTime: String?): Long? {
    if (isoDateTime.isNullOrBlank()) return null
    return try {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault()).parse(isoDateTime)?.time
    } catch (e: Exception) {
        null
    }
}

private class TransactionHistoryAdapter(
    private val transactions: List<TransactionSummary>,
    private val dateFormat: SimpleDateFormat,
    private val onItemClick: (TransactionSummary) -> Unit
) : RecyclerView.Adapter<TransactionHistoryAdapter.ViewHolder>() {

    override fun getItemCount(): Int = transactions.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transaction, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val tx = transactions[position]
        holder.itemView.setOnClickListener { onItemClick(tx) }
        holder.merchant.text = extractMerchantName(tx.merchantName)
        val dateMs = parseLocalTransactionDateMs(tx.localTransactionDateTime)
        holder.date.text = if (dateMs != null) dateFormat.format(Date(dateMs)) else ""
        holder.amount.text = CurrencyUtils.formatAmount(tx.amountInMinorUnit, tx.transactionCurrencyCode)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val merchant: TextView = itemView.findViewById(R.id.transactionMerchant)
        val date: TextView = itemView.findViewById(R.id.transactionDate)
        val amount: TextView = itemView.findViewById(R.id.transactionAmount)
    }
}
