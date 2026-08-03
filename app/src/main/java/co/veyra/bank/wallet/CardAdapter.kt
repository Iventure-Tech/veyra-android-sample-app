package co.veyra.bank.wallet

import android.content.res.ColorStateList
import android.graphics.drawable.RippleDrawable
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import co.veyra.bank.databinding.ItemCardBinding
import co.veyra.bank.databinding.ItemEmptyCardBinding
import co.veyra.bank.databinding.ItemDefaultCardBinding
import co.veyra.wallet.sdk.Token
import android.util.Log
import co.veyra.bank.R

class CardAdapter : ListAdapter<CardItem, RecyclerView.ViewHolder>(CardDiffCallback()) {
    
    var onAddCardClick: (() -> Unit)? = null
    var onTokenClick: ((Token, Int) -> Unit)? = null
    
    /**
     * Get card item at position (public accessor)
     */
    fun getCardItem(position: Int): CardItem? {
        return if (position >= 0 && position < itemCount) {
            getItem(position)
        } else {
            null
        }
    }
    
    override fun getItemViewType(position: Int): Int {
        return try {
            when (getItem(position)) {
                is CardItem.Empty -> VIEW_TYPE_EMPTY
                is CardItem.Default -> VIEW_TYPE_DEFAULT
                is CardItem.Token -> VIEW_TYPE_CARD
            }
        } catch (e: Exception) {
            VIEW_TYPE_DEFAULT
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_EMPTY -> {
                val binding = ItemEmptyCardBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                EmptyCardViewHolder(binding)
            }
            VIEW_TYPE_DEFAULT -> {
                val binding = ItemDefaultCardBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                DefaultCardViewHolder(binding)
            }
            VIEW_TYPE_CARD -> {
                val binding = ItemCardBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                CardViewHolder(binding, this)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }
    
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is CardItem.Empty -> {
                (holder as EmptyCardViewHolder).bind(item, onAddCardClick)
            }
            is CardItem.Default -> {
                (holder as DefaultCardViewHolder).bind(item, onAddCardClick)
            }
            is CardItem.Token -> {
                (holder as CardViewHolder).bind(item.token, position, item.pendingActivation)
            }
        }
    }
    
    class CardViewHolder(
        private val binding: ItemCardBinding,
        private val adapter: CardAdapter
    ) :
        RecyclerView.ViewHolder(binding.root) {
        
        private var gestureDetector: GestureDetector? = null
        
        fun bind(token: Token, position: Int, pendingActivation: Boolean = false) {
            // The SDK derives the card's display name — scheme label + masked last four, e.g.
            // "AFRIGO ****1234" — so it is already short and safe to render as-is. Blank only on
            // cards digitised before the SDK supplied it.
            binding.cardHolderNameTextView.text = token.cardHolderName.ifBlank { "CARDHOLDER" }
            binding.cardNumberTextView.text = token.getMaskedPAN()
            // Expiry: show month and year only (MM/YY)
            val expiryDateDisplay = formatExpiryMonthYear(token.expiryDate)
            binding.expiryDateTextView.text = expiryDateDisplay
            binding.expiryDateTextView.visibility = View.VISIBLE
            binding.lastFourDigitsTextView.text = token.getLastFourDigits()
            binding.lastFourDigitsTextView.visibility = View.VISIBLE

            binding.pendingActivationBadge?.let { badge ->
                // The requires-online (frozen) badge wins over pending-activation.
                when {
                    token.requiresOnline -> {
                        badge.visibility = View.VISIBLE
                        badge.text = itemView.context.getString(R.string.requires_online_badge)
                    }
                    pendingActivation -> {
                        badge.visibility = View.VISIBLE
                        badge.text = itemView.context.getString(R.string.activation_pending_badge)
                    }
                    else -> badge.visibility = View.GONE
                }
            }

            // Grey card art when deactivated OR frozen (requires online)
            binding.cardContentContainer.setBackgroundResource(
                if (token.isActive && !token.requiresOnline) R.drawable.card_gradient
                else R.drawable.card_gradient_grey
            )

            if (token.requiresOnline) {
                // A frozen card is not tappable — no pay affordance reachable from it.
                binding.root.setOnClickListener(null)
                binding.root.isClickable = false
                binding.root.isFocusable = false
                binding.root.foreground = null
                binding.cardContentContainer.alpha = 0.55f
            } else {
                binding.root.setOnClickListener {
                    adapter.onTokenClick?.invoke(token, position)
                }
                binding.root.isClickable = true
                binding.root.isFocusable = true
                binding.root.foreground = RippleDrawable(
                    ColorStateList.valueOf(0x40FFFFFF),
                    null,
                    null
                )
                binding.cardContentContainer.alpha = 1f
            }
            Log.d("CardAdapter", "Binding card - Expiry: $expiryDateDisplay")
        }

        /** Format expiry as month and year only (MM/YY). Handles DD/MM/YY or MM/YY input. */
        private fun formatExpiryMonthYear(expiry: String): String {
            if (expiry.isBlank() || expiry == "MM/YY") return "12/25"
            val parts = expiry.trim().split("/").map { it.trim() }.filter { it.isNotEmpty() }
            return when {
                parts.size >= 3 -> "${parts[1].padStart(2, '0')}/${parts[2].takeLast(2)}"
                parts.size == 2 -> "${parts[0].padStart(2, '0')}/${parts[1].takeLast(2)}"
                else -> expiry
            }
        }
    }
    
    class EmptyCardViewHolder(private val binding: ItemEmptyCardBinding) :
        RecyclerView.ViewHolder(binding.root) {
        
        fun bind(item: CardItem.Empty, onAddCardClick: (() -> Unit)?) {
            val clickListener = View.OnClickListener {
                onAddCardClick?.invoke()
            }
            binding.root.setOnClickListener(clickListener)
            binding.addCardButton.setOnClickListener(clickListener)
        }
    }
    
    class DefaultCardViewHolder(private val binding: ItemDefaultCardBinding) :
        RecyclerView.ViewHolder(binding.root) {
        
        fun bind(item: CardItem.Default, onAddCardClick: (() -> Unit)?) {
            val clickListener = View.OnClickListener {
                onAddCardClick?.invoke()
            }
            binding.root.setOnClickListener(clickListener)
        }
    }
    
    companion object {
        private const val VIEW_TYPE_EMPTY = 0
        private const val VIEW_TYPE_DEFAULT = 1
        private const val VIEW_TYPE_CARD = 2
    }
}

sealed class CardItem {
    data class Token(val token: co.veyra.wallet.sdk.Token, val pendingActivation: Boolean = false) : CardItem()
    object Empty : CardItem()
    object Default : CardItem()
}

class CardDiffCallback : DiffUtil.ItemCallback<CardItem>() {
    override fun areItemsTheSame(oldItem: CardItem, newItem: CardItem): Boolean {
        return when {
            oldItem is CardItem.Token && newItem is CardItem.Token ->
                oldItem.token.tokenId == newItem.token.tokenId
            oldItem is CardItem.Empty && newItem is CardItem.Empty -> true
            oldItem is CardItem.Default && newItem is CardItem.Default -> true
            else -> false
        }
    }

    override fun areContentsTheSame(oldItem: CardItem, newItem: CardItem): Boolean {
        return when {
            oldItem is CardItem.Token && newItem is CardItem.Token ->
                oldItem.token.tokenId == newItem.token.tokenId &&
                oldItem.pendingActivation == newItem.pendingActivation &&
                oldItem.token.isActive == newItem.token.isActive &&
                oldItem.token.requiresOnline == newItem.token.requiresOnline
            else -> oldItem == newItem
        }
    }
}
