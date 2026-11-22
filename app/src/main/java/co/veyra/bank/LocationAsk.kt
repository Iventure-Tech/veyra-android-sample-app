package co.veyra.bank

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat

/**
 * Asks for location once, before the first payment or card added, with a short explanation first.
 *
 * The Veyra SDKs never ask for a permission themselves: they send the phone's location with each
 * payment, card added and key refresh when the app already holds it, and report it as unavailable
 * otherwise. That evidence helps the payment's fraud checks, which isn't obvious to a user, so the app
 * explains before the system prompt (Google Play asks for this). Asked once: whatever the answer, the
 * flow carries on, and a "no" is never asked again here; the user can still allow it in Settings.
 *
 * Create it as a property of the activity, so its permission launcher registers before the activity
 * starts.
 */
class LocationAsk(private val activity: ComponentActivity) {

    private var next: (() -> Unit)? = null

    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Allowed or not, the payment goes ahead: without location the SDK reports it unavailable.
        next?.invoke()
        next = null
    }

    /** Runs [then] at once if location is allowed or was already asked for; otherwise asks first. */
    fun beforePayment(then: () -> Unit) {
        if (granted(activity) || asked(activity)) {
            then()
            return
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.location_ask_title)
            .setMessage(R.string.location_ask_message)
            .setPositiveButton(R.string.location_ask_continue) { _, _ ->
                markAsked(activity)
                next = then
                launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
            .setNegativeButton(R.string.location_ask_not_now) { _, _ ->
                markAsked(activity)
                then()
            }
            .setCancelable(false)
            .show()
    }

    companion object {
        private const val PREFS = "veyra_bank_location_ask"
        private const val KEY_ASKED = "asked"

        fun granted(context: Context): Boolean =
            listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

        private fun asked(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ASKED, false)

        private fun markAsked(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ASKED, true).apply()
        }
    }
}
