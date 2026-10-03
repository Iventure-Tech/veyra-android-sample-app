package co.veyra.bank

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import co.veyra.bank.softpos.GetPaidActivity
import co.veyra.bank.wallet.PayActivity

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

    private lateinit var cardGetPaid: View
    private lateinit var cardPay: View
    private lateinit var getPaidSubtitle: TextView
    private lateinit var customerLabel: TextView
    private lateinit var switchCustomerButton: Button
    private lateinit var signInOutButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // Every launch tells the SDKs who is logged in; a signed-out app tells them nothing.
        if (VeyraBank.isSignedIn(this)) VeyraBank.signIn(this)

        cardGetPaid = findViewById(R.id.cardGetPaid)
        cardPay = findViewById(R.id.cardPay)
        getPaidSubtitle = findViewById(R.id.getPaidSubtitle)
        customerLabel = findViewById(R.id.customerLabel)
        switchCustomerButton = findViewById(R.id.switchCustomerButton)
        signInOutButton = findViewById(R.id.signInOutButton)

        switchCustomerButton.setOnClickListener {
            VeyraBank.switchCustomer(this)
            reflectMerchantState()
        }
        signInOutButton.setOnClickListener {
            if (VeyraBank.isSignedIn(this)) VeyraBank.signOut(this) else VeyraBank.signIn(this)
            reflectMerchantState()
        }

        cardGetPaid.setOnClickListener {
            startActivity(Intent(this, GetPaidActivity::class.java))
        }
        cardPay.setOnClickListener {
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
        if (!VeyraBank.isSignedIn(this)) return // merchant settings are a signed-in customer's
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
        val signedIn = VeyraBank.isSignedIn(this)
        customerLabel.text = if (signedIn) {
            getString(R.string.home_signed_in_as, VeyraBank.customerId(this))
        } else {
            getString(R.string.home_signed_out)
        }
        switchCustomerButton.visibility = if (signedIn) View.VISIBLE else View.GONE
        signInOutButton.text = getString(if (signedIn) R.string.home_sign_out else R.string.home_sign_in)

        val registered = signedIn && VeyraBank.isMerchantRegistered(this)
        cardGetPaid.isEnabled = registered
        cardGetPaid.alpha = if (registered) 1f else 0.45f
        getPaidSubtitle.text = getString(
            when {
                !signedIn -> R.string.home_signed_out_locked
                registered -> R.string.home_get_paid_subtitle
                else -> R.string.home_get_paid_locked
            }
        )
        cardPay.isEnabled = signedIn
        cardPay.alpha = if (signedIn) 1f else 0.45f
    }
}
