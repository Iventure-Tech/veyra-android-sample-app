package co.veyra.bank.connection

import co.veyra.bank.BuildConfig
import co.veyra.common.providers.VeyraProvider
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * The provider this app passes to `initialize` — one for both SDKs — chosen in the untracked
 * `veyra.properties` (see `veyra.properties.example`). Which kind is a decision this app makes, so there is no default:
 * an unset or unknown mode stops the app at startup, naming what to set.
 *
 * Each mode builds its own provider from its own settings only: the client-secret provider never
 * sees the bank backend, and the backend providers never see a secret.
 */
object AppConnection {

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val bankSession: () -> String? = { BuildConfig.BANK_SESSION_TOKEN.ifBlank { null } }

    /** The provider for both SDKs. Built per call; the SDK re-binds it on every initialise. */
    fun provider(): VeyraProvider = forMode(
        mode = BuildConfig.VEYRA_CONNECTION_MODE,
        assertion = { assertionProvider(BuildConfig.VEYRA_CLIENT_ID, BuildConfig.BANK_BACKEND_BASE_URL, bankSession, http) },
        proxy = { proxyProvider(BuildConfig.BANK_BACKEND_BASE_URL, bankSession, http) },
        clientSecret = { clientSecretProvider(BuildConfig.VEYRA_CLIENT_ID, BuildConfig.VEYRA_CLIENT_SECRET) },
    )

    /** Builds only the selected mode's provider; the others are never invoked. */
    internal fun forMode(
        mode: String,
        assertion: () -> VeyraProvider,
        proxy: () -> VeyraProvider,
        clientSecret: () -> VeyraProvider,
    ): VeyraProvider = when (mode) {
        "directWithAssertion" -> assertion()
        "viaAppBackend" -> proxy()
        "directWithClientSecret" -> clientSecret()
        else -> error(
            "veyra.connection.mode is not set (got \"$mode\"). Copy veyra.properties.example to veyra.properties in the project root " +
                "and choose directWithAssertion, viaAppBackend or directWithClientSecret.",
        )
    }

    /** `directWithAssertion`: your client id, and the bank backend that signs the assertion. */
    internal fun assertionProvider(
        clientId: String,
        bankBackendBaseUrl: String,
        bankSession: () -> String?,
        http: OkHttpClient,
    ): VeyraProvider = BankBackendAssertionProvider(clientId, required(bankBackendBaseUrl), bankSession, http)

    /** `viaAppBackend`: only the bank backend that relays the SDK's calls — no client id, no secret. */
    internal fun proxyProvider(
        bankBackendBaseUrl: String,
        bankSession: () -> String?,
        http: OkHttpClient,
    ): VeyraProvider = BankBackendRelay(required(bankBackendBaseUrl), bankSession, http)

    /** `directWithClientSecret` (deprecated, testing only): just the client id and secret. */
    internal fun clientSecretProvider(clientId: String, clientSecret: String): VeyraProvider =
        ClientSecretCredentials(clientId, clientSecret)

    private fun required(baseUrl: String): String =
        baseUrl.trimEnd('/').ifBlank { error("veyra.bankBackendBaseUrl must be set in veyra.properties for this mode") }
}

/**
 * The deprecated client-secret provider — **for testing only**, e.g. against UAT before your bank
 * backend can sign assertions. A secret inside an app can be extracted: ship a
 * [BankBackendAssertionProvider] or [BankBackendRelay] instead.
 */
@Suppress("DEPRECATION")
class ClientSecretCredentials(
    override val clientId: String,
    override val clientSecret: String,
) : co.veyra.common.providers.VeyraClientSecretProvider
