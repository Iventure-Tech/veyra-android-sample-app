package co.veyra.bank

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import co.veyra.bank.softpos.GetPaidActivity
import co.veyra.bank.wallet.PayActivity
import co.veyra.sdk.VeyraSdk

/**
 * Neo-bank home. NFC mode is implicit — no switch anywhere:
 *
 * - opening **Get paid** puts the app in SOFTPOS (receive) mode for as long as that
 *   context is on screen;
 * - opening **Pay** puts it in WALLET mode for as long as that context is on screen;
 * - at Home (and whenever neither context is on screen) NFC is off.
 *
 * The switching is done **inside the SDK**: each SDK claims its NFC mode while its
 * screen is in the foreground and releases it on leaving, and the SDK's own inert
 * backstop guarantees any non-claiming screen (this one included) drops the process
 * to NONE — the app never touches a mode API. This screen only launches the flows and
 * gates Get paid until a merchant is registered — registration lives behind the
 * settings gear.
 */
class HomeActivity : AppCompatActivity() {

    private lateinit var sdk: VeyraSdk
    private lateinit var cardGetPaid: View
    private lateinit var getPaidSubtitle: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        sdk = VeyraBank.ensureInitialized(this)

        cardGetPaid = findViewById(R.id.cardGetPaid)
        getPaidSubtitle = findViewById(R.id.getPaidSubtitle)

        cardGetPaid.setOnClickListener {
            startActivity(Intent(this, GetPaidActivity::class.java))
        }
        findViewById<View>(R.id.cardPay).setOnClickListener {
            startActivity(Intent(this, PayActivity::class.java))
        }
        findViewById<ImageButton>(R.id.settingsButton).setOnClickListener { anchor ->
            showMerchantMenu(anchor)
        }
    }

    override fun onResume() {
        super.onResume()
        // No mode handling needed: the SDK's inert backstop guarantees Home is NFC-inert
        // (HCE component disabled, routing released) however this screen was reached.
        reflectMerchantState()
    }

    /**
     * Merchant management, moved here from the Get paid amount screen. If no merchant is
     * registered yet, the gear jumps straight to registration; once registered it offers
     * Edit / Activate / Deactivate. Each routes to [GetPaidActivity] (which holds the
     * initialised SoftPOS SDK and its `merchantService`).
     */
    private fun showMerchantMenu(anchor: android.view.View) {
        if (!VeyraBank.isMerchantRegistered(this)) {
            startActivity(
                Intent(this, GetPaidActivity::class.java)
                    .putExtra(GetPaidActivity.EXTRA_MERCHANT_SETTINGS, true)
            )
            return
        }
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.menu_settings, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            val intent = Intent(this, GetPaidActivity::class.java)
            when (item.itemId) {
                R.id.action_edit_profile -> intent.putExtra(GetPaidActivity.EXTRA_MERCHANT_SETTINGS, true)
                R.id.action_activate -> intent.putExtra(GetPaidActivity.EXTRA_MERCHANT_ACTION, GetPaidActivity.ACTION_ACTIVATE)
                R.id.action_deactivate -> intent.putExtra(GetPaidActivity.EXTRA_MERCHANT_ACTION, GetPaidActivity.ACTION_DEACTIVATE)
                else -> return@setOnMenuItemClickListener false
            }
            startActivity(intent)
            true
        }
        popup.show()
    }

    private fun reflectMerchantState() {
        val registered = VeyraBank.isMerchantRegistered(this)
        cardGetPaid.isEnabled = registered
        cardGetPaid.alpha = if (registered) 1f else 0.45f
        getPaidSubtitle.text = getString(
            if (registered) R.string.home_get_paid_subtitle else R.string.home_get_paid_locked
        )
    }
}
