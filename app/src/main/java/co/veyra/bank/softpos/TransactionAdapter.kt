package co.veyra.bank.softpos

import co.veyra.bank.R
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import co.veyra.softpos.payment.sdk.CurrencyUtils
import co.veyra.softpos.payment.sdk.TransactionInfo
import co.veyra.softpos.payment.sdk.TransactionStatus

class TransactionAdapter(
    private val items: List<TransactionInfo>,
    private val onItemClick: (TransactionInfo) -> Unit
) : RecyclerView.Adapter<TransactionAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val amountText: TextView = view.findViewById(R.id.amountText)
        val statusText: TextView = view.findViewById(R.id.statusText)
        val refText: TextView = view.findViewById(R.id.refText)
        val timeText: TextView = view.findViewById(R.id.timeText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_softpos_transaction, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.amountText.text = CurrencyUtils.formatAmount(item.amount, item.currencyCode)
        holder.refText.text = item.merchantTransactionReference
        holder.timeText.text = item.transactionTime ?: "—"
        holder.statusText.text = item.transactionStatus.name
        holder.statusText.setTextColor(
            when (item.transactionStatus) {
                TransactionStatus.APPROVED -> holder.itemView.context.getColor(R.color.success_green)
                TransactionStatus.DECLINED, TransactionStatus.FAILED -> holder.itemView.context.getColor(R.color.error_red)
                TransactionStatus.PENDING -> holder.itemView.context.getColor(android.R.color.holo_orange_dark)
            }
        )
        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount() = items.size
}
