package co.veyra.bank.wallet

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.ScaleAnimation
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import co.veyra.bank.R
import co.veyra.bank.databinding.ActivityPayBinding
import co.veyra.wallet.sdk.ActivationMethod
import co.veyra.wallet.sdk.ActivationMethods
import co.veyra.wallet.sdk.Environment
import co.veyra.wallet.sdk.VeyraWalletSdk
import co.veyra.wallet.sdk.TransactionSummary
import co.veyra.wallet.sdk.VeyraWalletSdkConfig
import co.veyra.wallet.sdk.Token
import co.veyra.wallet.sdk.TransactionResponse
import co.veyra.wallet.sdk.util.CurrencyUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class PayActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPayBinding
    private lateinit var sdk: VeyraWalletSdk
    private lateinit var cardAdapter: CardAdapter
    private var currentPage = 0
    private val pendingObserverRefs = mutableSetOf<String>()
    // The SDK fires the CPM QR's expiry via showQrToPay(onExpired=…), but the QR dialog
    // UI is built only after the QR is created — this trampoline lets the dialog install the actual
    // "blank the code" action once its views exist.
    private var onQrExpired: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // VeyraSdk facade owns the exclusive NFC mode (fully SDK-managed) and has already
        // initialised the wallet SDK; re-initialising here is an idempotent no-op that
        // keeps this flow openable standalone.
        co.veyra.bank.VeyraBank.ensureInitialized(this)
        sdk = VeyraWalletSdk.initialize(this, co.veyra.bank.VeyraBank.walletConfig(this), activity = this)

        binding = ActivityPayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewPager()
        setupSettingsButton()
        setupFab()
        loadCards()
    }

    // ── ViewPager ──────────────────────────────────────────────────────────────

    private fun setupViewPager() {
        cardAdapter = CardAdapter().apply {
            onAddCardClick = { navigateToTokenizationRequest() }
            onTokenClick = { token, _ -> handleTokenClick(token) }
        }

        binding.cardsViewPager.adapter = cardAdapter
        binding.cardsViewPager.offscreenPageLimit = 1
        binding.cardsViewPager.isUserInputEnabled = true

        // Disable over-scroll glow and nested scrolling on the internal RecyclerView
        binding.cardsViewPager.post {
            (binding.cardsViewPager.getChildAt(0) as? RecyclerView)?.apply {
                overScrollMode = RecyclerView.OVER_SCROLL_NEVER
                isNestedScrollingEnabled = false
            }
        }

        // Clean peek transformer: slight scale + alpha for side cards, gap between pages
        val gapPx = (12 * resources.displayMetrics.density).toInt()
        binding.cardsViewPager.setPageTransformer { page, position ->
            val absPos = abs(position)
            page.translationX = position * -gapPx.toFloat()
            page.scaleX = 1f - 0.06f * absPos
            page.scaleY = 1f - 0.06f * absPos
            page.alpha = 1f - 0.3f * absPos
        }

        binding.cardsViewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentPage = position
                updateActivitySectionForCurrentCard()
                activateNFCForPosition(position)
            }
        })

    }

    // ── Settings cog ──────────────────────────────────────────────────────────

    private fun setupSettingsButton() {
        binding.settingsButton.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            val currentItem = cardAdapter.getCardItem(currentPage)
            val tokenRef = (currentItem as? CardItem.Token)?.token?.tokenUniqueReference

            popup.menu.add(0, MENU_REMOVE_TOKEN, 0, "Remove card").apply {
                isEnabled = tokenRef != null
            }

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_REMOVE_TOKEN -> {
                        if (tokenRef != null) confirmRemoveToken(tokenRef)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    private fun confirmRemoveToken(tokenUniqueReference: String) {
        AlertDialog.Builder(this)
            .setTitle("Remove card")
            .setMessage("Are you sure you want to remove this card from your wallet?")
            .setPositiveButton("Remove") { _, _ -> removeToken(tokenUniqueReference) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun removeToken(tokenUniqueReference: String) {
        sdk.tokenisationService.deactivateToken(tokenUniqueReference) { result ->
            result.fold(
                onSuccess = {
                    Toast.makeText(this, "Card removed", Toast.LENGTH_SHORT).show()
                    loadCards()
                },
                onFailure = { error ->
                    Toast.makeText(this, "Failed to remove card: ${error.message}", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    // ── FAB ───────────────────────────────────────────────────────────────────

    private fun setupFab() {
        binding.addCardFab.setOnClickListener { navigateToTokenizationRequest() }
        // Scan-to-pay (MPM QR rail): pay a merchant by scanning their displayed QR.
        binding.scanToPayFab.setOnClickListener {
            startActivity(android.content.Intent(this, ScanToPayActivity::class.java))
        }
        // Show-QR-to-pay (CPM rail): the merchant scans the customer's QR. Amount
        // first (dynamic CPM — it is bound inside the QR's cryptogram), then CDCVM, then render.
        binding.showQrFab.setOnClickListener { promptAmountForPaymentQr() }
    }

    // ── Show QR to pay (CPM) ──────────────────────────────────────────────────

    private fun promptAmountForPaymentQr() {
        val input = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "0.00"
        }
        AlertDialog.Builder(this)
            .setTitle("Show QR to pay")
            .setMessage("Enter the amount the merchant stated (₦):")
            .setView(input)
            .setPositiveButton("Continue") { _, _ ->
                val naira = input.text.toString().toDoubleOrNull()
                if (naira == null || naira <= 0.0) {
                    Toast.makeText(this, "Enter a valid amount", Toast.LENGTH_SHORT).show()
                } else {
                    authenticateThenShowQr((naira * 100).toLong())
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun authenticateThenShowQr(amountMinorUnits: Long) {
        val amountText = "₦%,.2f".format(amountMinorUnits / 100.0)
        // Each render consumes one authentication (SDK-enforced) — regenerate re-authenticates.
        sdk.tokenisationService.authenticateForScannedPayment(
            activity = this,
            title = "Show QR to pay",
            subtitle = "Pay $amountText by QR",
        ) { auth ->
            auth.fold(
                onSuccess = {
                    // The SDK owns the expiry timer; it fires onExpired when the QR lapses.
                    sdk.tokenisationService.showQrToPay(
                        amountMinorUnits,
                        onExpired = { onQrExpired?.invoke() },
                    ) { result ->
                        result.fold(
                            onSuccess = { qr -> showPaymentQrDialog(qr, amountText) },
                            onFailure = { e -> Toast.makeText(this, e.message, Toast.LENGTH_LONG).show() },
                        )
                    }
                },
                onFailure = { e -> Toast.makeText(this, e.message, Toast.LENGTH_LONG).show() },
            )
        }
    }

    private fun showPaymentQrDialog(qr: co.veyra.wallet.sdk.CpmPaymentQr, amountText: String) {
        val density = resources.displayMetrics.density
        val qrBitmap = encodeQrBitmap(qr.payload)
        val qrImage = android.widget.ImageView(this).apply {
            setPadding((24 * density).toInt(), (24 * density).toInt(), (24 * density).toInt(), (8 * density).toInt())
            setImageBitmap(qrBitmap)
        }
        // When the QR lapses or the payment settles, the code must become UNSCANNABLE —
        // a dimmed QR is still machine-readable, so another device could scan a no-longer-valid
        // code. Swap in a solid placeholder of the same size so nothing decodable remains.
        val blankQr = { qrImage.setImageBitmap(blankQrPlaceholder(qrBitmap.width, qrBitmap.height)) }
        val countdown = android.widget.TextView(this).apply {
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, (16 * density).toInt())
        }
        // The settle outcome, shown inline once the gateway reconcile catches it
        // ("Paid — <merchant>" / "Declined").
        val outcome = android.widget.TextView(this).apply {
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, (16 * density).toInt())
            visibility = android.view.View.GONE
        }
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            addView(qrImage)
            addView(countdown)
            addView(outcome)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Show to merchant — $amountText")
            .setView(container)
            .setNegativeButton("Done", null)
            .setNeutralButton("New QR") { _, _ -> authenticateThenShowQr(qr.amountMinorUnits) }
            .create()
        dialog.show()
        // The SDK owns the expiry timer (showQrToPay(onExpired=…)); it fires the blank
        // via this trampoline when the QR lapses — a lapsed QR must not stay scannable.
        onQrExpired = {
            blankQr()
            countdown.text = "Expired — tap New QR"
        }
        // Cosmetic per-second countdown only (the blanking trigger is the SDK callback above; the
        // provider also enforces a freshness window server-side).
        val timer = object : android.os.CountDownTimer(qr.expiresAtEpochMillis - System.currentTimeMillis(), 1000) {
            override fun onTick(millisUntilFinished: Long) {
                countdown.text = "Expires in ${millisUntilFinished / 1000}s"
            }
            override fun onFinish() { /* SDK onExpired handles the blank + label */ }
        }.also { it.start() }
        // Poll the gateway reconcile directly every 3s while the QR is up,
        // so the outcome lands within seconds of the merchant charging — not on WorkManager's
        // schedule. Stops on settle or when the dialog is dismissed.
        val settlePoll = startCpmSettlePolling(qr.tokenUniqueReference, qr.transactionHash) { approved, merchant ->
            // Settled — stop the expiry watch so it can't later overwrite the outcome with "Expired".
            onQrExpired = null
            sdk.tokenisationService.cancelQrExpiry()
            countdown.visibility = android.view.View.GONE
            outcome.visibility = android.view.View.VISIBLE
            outcome.text = if (approved) "Paid" + (merchant?.let { " — $it" } ?: "") else "Declined"
            outcome.setTextColor(if (approved) android.graphics.Color.parseColor("#2E7D32") else android.graphics.Color.parseColor("#C62828"))
            blankQr()
        }
        dialog.setOnDismissListener {
            timer.cancel()
            settlePoll.cancel()
            // Stop the SDK expiry watch so its callback can't fire into a dismissed dialog.
            onQrExpired = null
            sdk.tokenisationService.cancelQrExpiry()
        }
    }

    /**
     * Drive the SDK reconcile on a 3s loop while a CPM QR is displayed and report the
     * terminal outcome. The reconcile is AWAITED before reading (fire-and-forget would race
     * the read). Settlement is matched on THIS render's own [transactionHash], never a
     * time window: `getTransactions` returns only TERMINAL rows, so a record-time
     * heuristic would let a just-settled PREVIOUS CPM payment be shown as this unscanned
     * QR's outcome ("Paid — <merchant>" before the merchant charged). The hash is unique per
     * render, so only the row for this QR can settle the display. "QR payment" is the pre-backfill
     * placeholder — don't show it as a merchant name.
     */
    private fun startCpmSettlePolling(
        tokenUniqueReference: String,
        transactionHash: String,
        onSettled: (approved: Boolean, merchantName: String?) -> Unit,
    ): kotlinx.coroutines.Job = lifecycleScope.launch {
        while (isActive) {
            kotlinx.coroutines.delay(3000)
            val done = kotlinx.coroutines.CompletableDeferred<Unit>()
            sdk.tokenisationService.reconcilePendingTransactions { done.complete(Unit) }
            done.await()
            val row = withContext(Dispatchers.IO) {
                sdk.tokenisationService.getTransactions(tokenUniqueReference, 10)
                    .firstOrNull { it.transactionHash == transactionHash }
            }
            if (row != null) { // terminal by construction: getTransactions returns settled rows only
                val name = row.merchantName?.takeIf { it != "QR payment" }
                onSettled(row.authorizationStatus == "APPROVED", name)
                return@launch
            }
        }
    }

    private fun encodeQrBitmap(payload: String): android.graphics.Bitmap {
        val size = 512
        val matrix = com.google.zxing.qrcode.QRCodeWriter()
            .encode(payload, com.google.zxing.BarcodeFormat.QR_CODE, size, size)
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (matrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        return bitmap
    }

    /**
     * A same-size solid placeholder that replaces the QR once it lapses/settles, so no
     * scannable modules remain on screen (a dimmed QR is still machine-readable).
     */
    private fun blankQrPlaceholder(width: Int, height: Int): android.graphics.Bitmap =
        android.graphics.Bitmap.createBitmap(
            width.coerceAtLeast(1), height.coerceAtLeast(1), android.graphics.Bitmap.Config.RGB_565,
        ).apply { eraseColor(android.graphics.Color.parseColor("#EEEEEE")) }

    // ── Cards loading ─────────────────────────────────────────────────────────

    private fun loadCards() {
        if (!::cardAdapter.isInitialized) return
        lifecycleScope.launch {
            try {
                // getTokens() does synchronous DataStore + Android-Keystore reads (per-token
                // provisioning-bundle checks); keep it off the UI thread so a load that overlaps a
                // background storage write can't stall the main thread into an ANR.
                val tokens = withContext(Dispatchers.IO) { sdk.tokenisationService.getTokens() }
                startObserversForPendingTokens(tokens)
                if (tokens.isEmpty()) {
                    cardAdapter.submitList(emptyList())
                    binding.cardsViewPager.visibility = View.GONE
                    binding.noCardsText.visibility = View.VISIBLE
                    binding.activitySection.visibility = View.GONE
                } else {
                    Log.d(TAG, "loadCards: ${tokens.size} token(s); recent tx per card = " +
                        tokens.joinToString { "${it.tokenUniqueReference?.takeLast(4) ?: "?"}:${it.transactions.size}" })
                    val cardItems = tokens.map { token ->
                        CardItem.Token(token, !token.isActive && !token.activationMethods.isNullOrEmpty())
                    }
                    // No pay affordance while the active card must go online first —
                    // both rails (scan-to-pay and show-QR) are disabled; the card renders frozen.
                    val activeRequiresOnline = tokens.firstOrNull { it.isActive }?.requiresOnline == true
                    binding.scanToPayFab.isEnabled = !activeRequiresOnline
                    binding.scanToPayFab.alpha = if (activeRequiresOnline) 0.4f else 1f
                    binding.showQrFab.isEnabled = !activeRequiresOnline
                    binding.showQrFab.alpha = if (activeRequiresOnline) 0.4f else 1f
                    binding.cardsViewPager.visibility = View.VISIBLE
                    binding.noCardsText.visibility = View.GONE
                    cardAdapter.submitList(cardItems) {
                        // Defer past the ViewPager's first layout: on initial load this commit
                        // callback fires before page 0 is laid out, so the activity section wasn't
                        // populating until a later page-change (e.g. returning from "view more")
                        // re-triggered it. post() runs it once the current card is settled.
                        binding.cardsViewPager.post { updateActivitySectionForCurrentCard() }
                        // Activate NFC for whichever card is currently in focus.
                        // Doing this in the commit callback guarantees the adapter has the
                        // fresh token objects before we read currentPage, avoiding a race
                        // where activateNFCForPosition reads a stale (or wrong) card.
                        activateNFCForPosition(currentPage)
                    }
                }
                binding.addCardFab.visibility = View.VISIBLE
            } catch (e: Exception) {
                Log.e(TAG, "Error loading cards", e)
                cardAdapter.submitList(emptyList())
                binding.cardsViewPager.visibility = View.GONE
                binding.noCardsText.visibility = View.VISIBLE
                binding.addCardFab.visibility = View.VISIBLE
            }
        }
    }

    // ── Activity section (recent transactions) ────────────────────────────────

    private val activityDateFormat = SimpleDateFormat("MMM d", Locale.getDefault())

    private fun updateActivitySectionForCurrentCard() {
        val item = cardAdapter.getCardItem(currentPage)
        if (item !is CardItem.Token) {
            Log.d(TAG, "activity: page $currentPage is not a card → section hidden")
            binding.activitySection.visibility = View.GONE
            return
        }
        val token = item.token
        binding.activitySection.visibility = View.VISIBLE

        val transactions = token.transactions.take(3)
        Log.d(TAG, "activity: card ${token.tokenUniqueReference?.takeLast(4)} at page $currentPage → " +
            "${token.transactions.size} recent tx, showing ${transactions.size}")
        binding.activityTransactionsList.removeAllViews()
        if (transactions.isEmpty()) {
            binding.activityNoTransactionsText.visibility = View.VISIBLE
            binding.activityTransactionsList.visibility = View.GONE
        } else {
            binding.activityNoTransactionsText.visibility = View.GONE
            binding.activityTransactionsList.visibility = View.VISIBLE
            for (tx in transactions) {
                val row = layoutInflater.inflate(R.layout.item_transaction, binding.activityTransactionsList, false)
                row.findViewById<TextView>(R.id.transactionMerchant).text = extractMerchantName(tx.merchantName)
                val txDateMs = try {
                    tx.localTransactionDateTime?.let {
                        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault()).parse(it)?.time
                    }
                } catch (e: Exception) { null }
                row.findViewById<TextView>(R.id.transactionDate).text =
                    if (txDateMs != null) activityDateFormat.format(Date(txDateMs)) else ""
                row.findViewById<TextView>(R.id.transactionAmount).text =
                    CurrencyUtils.formatAmount(tx.amountInMinorUnit, tx.transactionCurrencyCode)
                row.setOnClickListener {
                    startActivity(TransactionDetailActivity.intent(this, tx))
                }
                binding.activityTransactionsList.addView(row)
            }
        }

        val ref = token.tokenUniqueReference
        if (!ref.isNullOrBlank()) {
            binding.viewMoreActivityButton.visibility = View.VISIBLE
            binding.viewMoreActivityButton.setOnClickListener {
                startActivity(TransactionHistoryActivity.intent(this, ref))
            }
        } else {
            binding.viewMoreActivityButton.visibility = View.GONE
        }
    }

    // ── Token click: activation flow only ────────────────────────────────────

    private fun handleTokenClick(token: Token) {
        val methods = token.activationMethods.orEmpty()
        if (!token.isActive && methods.isNotEmpty()) {
            val ref = token.tokenUniqueReference
            if (methods.size == 1) {
                startSingleMethodActivation(token.tokenId, methods.single(), ref)
            } else {
                startActivity(ActivationMethodSelectionActivity.intent(this, token.tokenId, methods, ref))
            }
        }
        // No expand — NFC is activated automatically when the card is swiped into focus
    }

    private fun startSingleMethodActivation(tokenId: String, method: ActivationMethod, tokenUniqueReference: String? = null) {
        when (method.medium) {
            ActivationMethods.MASKED_EMAIL,
            ActivationMethods.MASKED_MOBILE_PHONE ->
                startActivity(ActivationOtpActivity.intent(this, tokenId, method.medium, tokenUniqueReference, activationMethodContact = method.contact))
            ActivationMethods.CALL_CENTER_PHONE ->
                startActivity(ActivationInfoActivity.intentCallCenter(this, tokenId, method.contact))
            ActivationMethods.AUTOMATED_CALL_CENTER_PHONE ->
                startActivity(ActivationInfoActivity.intentAutomatedCallCenter(this, tokenId, method.contact))
            ActivationMethods.WEBSITE ->
                startActivity(ActivationInfoActivity.intentWebsite(this, tokenId, method.contact))
            ActivationMethods.MOBILE_APPLICATION ->
                startActivity(ActivationInfoActivity.intentIssuerApp(this, tokenId, method.contact))
            else ->
                startActivity(ActivationMethodSelectionActivity.intent(this, tokenId, listOf(method), tokenUniqueReference))
        }
    }

    // ── NFC activation ────────────────────────────────────────────────────────

    private fun activateNFCForPosition(position: Int) {
        val item = cardAdapter.getCardItem(position) as? CardItem.Token ?: return
        activateNFCForToken(item.token)
    }

    private fun activateNFCForToken(token: Token) {
        try {
            sdk.tokenisationService.setActiveToken(
                tokenId = token.tokenId,
                activity = this,
                onTransactionStarted = { tokenId ->
                    Log.i(TAG, "Transaction started for token: $tokenId")
                },
                onTransactionCompleted = { response ->
                    runOnUiThread { handleTransactionResponse(response) }
                },
                onActivationFailed = { message ->
                    runOnUiThread {
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                        loadCards()
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error activating NFC for card", e)
        }
    }

    // ── Transaction response ──────────────────────────────────────────────────

    private fun handleTransactionResponse(response: TransactionResponse) {
        when (response.status) {
            "APPROVED" -> {
                showTransactionSuccessCheckmark()
                Toast.makeText(this, "Transaction approved", Toast.LENGTH_SHORT).show()
            }
            "DECLINED" -> Toast.makeText(this, "Transaction declined: ${response.message}", Toast.LENGTH_LONG).show()
            "ERROR" -> Toast.makeText(this, "Transaction error: ${response.message}", Toast.LENGTH_LONG).show()
            else -> Toast.makeText(this, "Transaction completed: ${response.message}", Toast.LENGTH_SHORT).show()
        }
        loadCards()
    }

    private fun showTransactionSuccessCheckmark() {
        val checkmark = binding.transactionSuccessCheckmark
        checkmark.visibility = View.VISIBLE
        checkmark.alpha = 0f
        checkmark.scaleX = 0.5f
        checkmark.scaleY = 0.5f

        val animSet = AnimationSet(true).apply {
            addAnimation(AlphaAnimation(0f, 1f).apply { duration = 300; fillAfter = true })
            addAnimation(ScaleAnimation(0.5f, 1f, 0.5f, 1f,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f).apply { duration = 300; fillAfter = true })
        }
        checkmark.startAnimation(animSet)
        checkmark.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(300)
            .withEndAction {
                checkmark.postDelayed({
                    checkmark.animate().alpha(0f).scaleX(0.8f).scaleY(0.8f).setDuration(400)
                        .withEndAction { checkmark.visibility = View.GONE }.start()
                }, 1000)
            }.start()
    }

    // ── Activation observer management ───────────────────────────────────────

    private fun startObserversForPendingTokens(tokens: List<Token>) {
        val allRefs = tokens.mapNotNull { it.tokenUniqueReference }.toSet()
        val activeRefs = tokens.filter { it.isActive }.mapNotNull { it.tokenUniqueReference }.toSet()
        pendingObserverRefs.filter { it in activeRefs || it !in allRefs }.forEach { ref ->
            sdk.tokenisationService.stopActivationObserver(ref)
            pendingObserverRefs.remove(ref)
        }
        tokens.filter { !it.isActive && !it.activationMethods.isNullOrEmpty() }
            .mapNotNull { it.tokenUniqueReference }
            .filter { it !in pendingObserverRefs || !sdk.tokenisationService.hasActivationObserver(it) }
            .forEach { ref ->
                pendingObserverRefs.add(ref)
                sdk.tokenisationService.observeActivation(
                    tokenUniqueReference = ref,
                    onActivated = {
                        pendingObserverRefs.remove(ref)
                        loadCards()
                    },
                    onTimeout = { pendingObserverRefs.remove(ref) }
                )
            }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onResume() {
        super.onResume()
        pendingObserverRefs.forEach { sdk.tokenisationService.resumeActivationObserver(it) }
        if (::cardAdapter.isInitialized) loadCards()
        // NFC activation is handled by loadCards() commit callback and onPageSelected.
        // Do NOT call activateNFCIfCardAvailable() here — it always activates tokens.first()
        // regardless of currentPage, which swaps the active card mid-transaction if the
        // NFC field is still live when onResume fires (e.g. returning from detail screen).
    }

    override fun onPause() {
        super.onPause()
        pendingObserverRefs.forEach { sdk.tokenisationService.pauseActivationObserver(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        pendingObserverRefs.forEach { sdk.tokenisationService.stopActivationObserver(it) }
        pendingObserverRefs.clear()
    }

    private fun navigateToTokenizationRequest() {
        startActivity(Intent(this, TokenizationRequestActivity::class.java))
    }

    companion object {
        private const val TAG = "PayActivity"
        private const val MENU_REMOVE_TOKEN = 1
    }
}
