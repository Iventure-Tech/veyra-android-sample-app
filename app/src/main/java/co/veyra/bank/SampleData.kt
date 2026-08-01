package co.veyra.bank

import android.content.Context

/**
 * Joins address parts into a single line, dropping blanks and collapsing consecutive
 * duplicates (so a city and state that are both "Lagos" render once).
 */
private fun composeAddress(vararg parts: String): String =
    parts.map { it.trim() }
        .filter { it.isNotEmpty() }
        .fold(mutableListOf<String>()) { acc, part ->
            if (!acc.lastOrNull().equals(part, ignoreCase = true)) acc.add(part)
            acc
        }
        .joinToString(", ")

/**
 * A demo account holder. The sample user is **either** a [PersonalMerchant] **or** a
 * [BusinessMerchant]; both can receive (SoftPOS) and pay (Wallet), so both carry the same
 * common identity below. A Business additionally has a [cacNumber]; a Personal does not.
 *
 * `merchantName` == `accountName` for both (a business's name replaces the individual's name).
 * Every value comes from `res/values/sample_data.xml` — never hardcode demo data in an Activity.
 */
sealed class Merchant {
    abstract val accountNumber: String
    abstract val institutionCode: String
    /** Account holder name — the merchant name for both types. */
    abstract val accountName: String
    abstract val bvn: String
    abstract val emailAddress: String
    abstract val walletAccountId: String
    abstract val mobileNumber: String
    abstract val acquirerId: String
    abstract val addressLine1: String
    abstract val addressLine2: String
    abstract val city: String
    abstract val state: String
    abstract val country: String
    abstract val countryCode: String

    /** The merchant name is the account holder name (business name for a business). */
    val merchantName: String get() = accountName

    /** Business registration number — only a [BusinessMerchant] has one. */
    open val cacNumber: String? get() = null

    /** Single-line address **built** from the structured parts (for Wallet accountHolderAddress). */
    val fullAddress: String
        get() = composeAddress(addressLine1, addressLine2, city, state, country)
}

data class PersonalMerchant(
    override val accountNumber: String,
    override val institutionCode: String,
    override val accountName: String,
    override val bvn: String,
    override val emailAddress: String,
    override val walletAccountId: String,
    override val mobileNumber: String,
    override val acquirerId: String,
    override val addressLine1: String,
    override val addressLine2: String,
    override val city: String,
    override val state: String,
    override val country: String,
    override val countryCode: String,
) : Merchant()

data class BusinessMerchant(
    override val accountNumber: String,
    override val institutionCode: String,
    override val accountName: String,
    override val bvn: String,
    override val emailAddress: String,
    override val walletAccountId: String,
    override val mobileNumber: String,
    override val acquirerId: String,
    override val addressLine1: String,
    override val addressLine2: String,
    override val city: String,
    override val state: String,
    override val country: String,
    override val countryCode: String,
    /** Businesses have a CAC number (individuals do not). */
    override val cacNumber: String,
) : Merchant()

/**
 * Single typed access point for the sample/demo prefill data in `res/values/sample_data.xml`.
 * Provides the two merchant identities and [active] — the one the current demo user is.
 */
object SampleData {

    // A Personal and a Business merchant share the same single identity — the user is one or
    // the other, never both — differing only by the CAC number a business additionally has.
    fun personal(context: Context): PersonalMerchant = with(context) {
        PersonalMerchant(
            accountNumber = getString(R.string.sample_account_number),
            institutionCode = getString(R.string.sample_institution_code),
            accountName = getString(R.string.sample_account_name),
            bvn = getString(R.string.sample_bvn),
            emailAddress = getString(R.string.sample_email),
            walletAccountId = getString(R.string.sample_wallet_account_id),
            mobileNumber = getString(R.string.sample_mobile_number),
            acquirerId = getString(R.string.sample_acquirer_id),
            addressLine1 = getString(R.string.sample_address_line1),
            addressLine2 = getString(R.string.sample_address_line2),
            city = getString(R.string.sample_city),
            state = getString(R.string.sample_state),
            country = getString(R.string.sample_country),
            countryCode = getString(R.string.sample_country_code),
        )
    }

    fun business(context: Context): BusinessMerchant = with(personal(context)) {
        BusinessMerchant(
            accountNumber = accountNumber,
            institutionCode = institutionCode,
            accountName = accountName,
            bvn = bvn,
            emailAddress = emailAddress,
            walletAccountId = walletAccountId,
            mobileNumber = mobileNumber,
            acquirerId = acquirerId,
            addressLine1 = addressLine1,
            addressLine2 = addressLine2,
            city = city,
            state = state,
            country = country,
            countryCode = countryCode,
            cacNumber = context.getString(R.string.sample_cac_number),
        )
    }

    /**
     * The identity of the current demo user — the one used for **both** receiving and paying.
     * Derived from what was registered (a stored CAC number ⇒ business), defaulting to
     * [personal] before any registration.
     */
    fun active(context: Context): Merchant =
        if (co.veyra.softpos.payment.sdk.VeyraSoftPOSSdk.storedMerchant(context)?.merchantType == "BUSINESS") business(context) else personal(context)
}
