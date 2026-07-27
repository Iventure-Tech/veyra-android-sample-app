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
import co.veyra.bank.databinding.ActivityActivationMethodSelectionBinding
import co.veyra.wallet.sdk.ActivationMethod
import co.veyra.wallet.sdk.ActivationMethods
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ActivationMethodSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityActivationMethodSelectionBinding
    private lateinit var tokenId: String
    private lateinit var methods: List<ActivationMethod>
    private var tokenUniqueReference: String? = null
    private lateinit var adapter: ActivationMethodAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityActivationMethodSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tokenId = intent.getStringExtra(EXTRA_TOKEN_ID) ?: run { finish(); return }
        val json = intent.getStringExtra(EXTRA_ACTIVATION_METHODS)
        val type = object : TypeToken<List<ActivationMethod>>() {}.type
        methods = if (json != null) Gson().fromJson(json, type) ?: emptyList() else emptyList()
        tokenUniqueReference = intent.getStringExtra(EXTRA_TOKEN_UNIQUE_REFERENCE)

        if (methods.isEmpty()) { finish(); return }

        if (methods.size == 1) {
            setupSingleMethodMode(methods.single())
        } else {
            setupMultiMethodMode()
        }

        binding.backButton.setOnClickListener { finish() }
    }

    private fun setupSingleMethodMode(method: ActivationMethod) {
        binding.titleTextView.text = getString(R.string.activation_verify_identity)
        binding.subtitleTextView.text = when (method.medium) {
            ActivationMethods.MASKED_EMAIL -> getString(R.string.activation_single_email_message)
            ActivationMethods.MASKED_MOBILE_PHONE -> getString(R.string.activation_single_phone_message)
            else -> getString(R.string.activation_single_generic_message, getLabelForMethod(method.medium))
        }
        binding.methodsRecyclerView.visibility = View.GONE
        binding.nextButton.visibility = View.VISIBLE
        binding.nextButton.setOnClickListener { onMethodSelected(method) }
    }

    private fun setupMultiMethodMode() {
        binding.methodsRecyclerView.layoutManager = LinearLayoutManager(this)
        adapter = ActivationMethodAdapter { selectedMethod -> onMethodSelected(selectedMethod) }
        binding.methodsRecyclerView.adapter = adapter
        adapter.submitList(methods.map { ActivationMethodItem(it, getLabelForMethod(it.medium)) })
    }

    private fun getLabelForMethod(medium: String): String = when (medium) {
        ActivationMethods.MASKED_EMAIL -> getString(R.string.activation_method_email)
        ActivationMethods.MASKED_MOBILE_PHONE -> getString(R.string.activation_method_phone)
        ActivationMethods.CALL_CENTER_PHONE -> getString(R.string.activation_method_call_center)
        ActivationMethods.AUTOMATED_CALL_CENTER_PHONE -> getString(R.string.activation_method_automated_call)
        ActivationMethods.WEBSITE -> getString(R.string.activation_method_website)
        ActivationMethods.MOBILE_APPLICATION -> getString(R.string.activation_method_issuer_app)
        else -> medium
    }

    private fun onMethodSelected(method: ActivationMethod) {
        when (method.medium) {
            ActivationMethods.MASKED_EMAIL, ActivationMethods.MASKED_MOBILE_PHONE ->
                startActivity(
                    ActivationCodeRequestActivity.intent(
                        context = this,
                        tokenId = tokenId,
                        method = method.medium,
                        tokenUniqueReference = tokenUniqueReference,
                        contact = method.contact
                    )
                )
            else ->
                openActivationScreenForMethod(method, expirationDateTime = null, message = null)
        }
    }

    private fun openActivationScreenForMethod(
        method: ActivationMethod,
        expirationDateTime: String?,
        message: String?
    ) {
        val intent = when (method.medium) {
            ActivationMethods.MASKED_EMAIL, ActivationMethods.MASKED_MOBILE_PHONE ->
                ActivationOtpActivity.intent(
                    this,
                    tokenId,
                    method.medium,
                    tokenUniqueReference,
                    codeAlreadyRequested = true,
                    expirationDateTime = expirationDateTime,
                    serverMessage = message,
                    activationMethodContact = method.contact
                )
            ActivationMethods.CALL_CENTER_PHONE ->
                ActivationInfoActivity.intentCallCenter(this, tokenId, method.contact, tokenUniqueReference)
            ActivationMethods.AUTOMATED_CALL_CENTER_PHONE ->
                ActivationInfoActivity.intentAutomatedCallCenter(this, tokenId, method.contact, tokenUniqueReference)
            ActivationMethods.WEBSITE ->
                ActivationInfoActivity.intentWebsite(this, tokenId, method.contact, tokenUniqueReference)
            ActivationMethods.MOBILE_APPLICATION ->
                ActivationInfoActivity.intentIssuerApp(this, tokenId, method.contact, tokenUniqueReference)
            else ->
                ActivationInfoActivity.intentGeneric(this, tokenId, method.medium, method.contact)
        }
        startActivity(intent)
    }

    companion object {
        private const val EXTRA_TOKEN_ID = "token_id"
        private const val EXTRA_ACTIVATION_METHODS = "activation_methods_json"
        private const val EXTRA_TOKEN_UNIQUE_REFERENCE = "token_unique_reference"

        fun intent(
            context: Context,
            tokenId: String,
            activationMethods: List<ActivationMethod>,
            tokenUniqueReference: String? = null
        ): Intent {
            return Intent(context, ActivationMethodSelectionActivity::class.java).apply {
                putExtra(EXTRA_TOKEN_ID, tokenId)
                putExtra(EXTRA_ACTIVATION_METHODS, Gson().toJson(activationMethods))
                putExtra(EXTRA_TOKEN_UNIQUE_REFERENCE, tokenUniqueReference)
            }
        }
    }
}

private data class ActivationMethodItem(val method: ActivationMethod, val label: String)

private class ActivationMethodAdapter(
    private val onSelect: (ActivationMethod) -> Unit
) : RecyclerView.Adapter<ActivationMethodAdapter.ViewHolder>() {

    private var items: List<ActivationMethodItem> = emptyList()

    fun submitList(list: List<ActivationMethodItem>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_activation_method, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item) { onSelect(item.method) }
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title = itemView.findViewById<TextView>(R.id.methodTitleTextView)

        fun bind(item: ActivationMethodItem, onSelect: () -> Unit) {
            title.text = item.label
            itemView.setOnClickListener { onSelect() }
        }
    }
}
