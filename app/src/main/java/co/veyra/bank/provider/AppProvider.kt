package co.veyra.bank.provider

import co.veyra.bank.BuildConfig
import co.veyra.common.providers.VeyraProvider
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * The provider this app passes to `initialize` — one for both SDKs. There is no mode to
 * configure: the SDK works out how to reach Veyra from the kind of provider it is given, so
 * switching is choosing which provider [provider] returns. The values each one needs come from
 * the untracked `veyra.properties` (see `veyra.properties.example`).
 */
object AppProvider {

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * The signed-in user's bank session: logs in to your bank with the user's username and password
     * (password grant at `{bankBackendBaseUrl}/oauth2/token`) and caches the token. This sample
     * reads the credentials from veyra.properties; a real app takes them from its login screen.
     */
    private val bankSession: BankSession by lazy {
        BankSession(
            required(BuildConfig.BANK_BACKEND_BASE_URL),
            BuildConfig.BANK_CLIENT_ID.ifBlank { error("veyra.bankClientId must be set in veyra.properties to log in") },
            BuildConfig.BANK_CLIENT_SECRET.ifBlank { error("veyra.bankClientSecret must be set in veyra.properties to log in") },
            username = { BuildConfig.BANK_USERNAME },
            password = { BuildConfig.BANK_PASSWORD },
            http = http,
        )
    }

    /**
     * The provider for both SDKs. Built per call; the SDK re-binds it on every initialise.
     *
     * Return ONE of the three. The sample ships with [proxyProvider]: every SDK call goes through
     * your bank backend, so the app holds no Veyra secret. [assertionProvider] is the other
     * production choice; the deprecated [clientSecretProvider] is for testing only.
     */
    fun provider(): VeyraProvider =
        proxyProvider(BuildConfig.BANK_BACKEND_BASE_URL, bankSession, http)
    //  assertionProvider(BuildConfig.VEYRA_CLIENT_ID, BuildConfig.BANK_CLIENT_ID, BuildConfig.BANK_CLIENT_SECRET, BuildConfig.BANK_BACKEND_BASE_URL, bankSession, http)
    //  clientSecretProvider(BuildConfig.VEYRA_CLIENT_ID, BuildConfig.VEYRA_CLIENT_SECRET)

    /**
     * Your Veyra client id (the only value the SDK receives), and your bank's own client at the
     * authorization server that exchanges the session for the assertion.
     */
    internal fun assertionProvider(
        clientId: String,
        bankClientId: String,
        bankClientSecret: String,
        bankBackendBaseUrl: String,
        bankSession: () -> String?,
        http: OkHttpClient,
    ): VeyraProvider = BankBackendAssertionProvider(
        clientId,
        bankClientId.ifBlank { error("veyra.bankClientId must be set in veyra.properties for this provider") },
        bankClientSecret.ifBlank { error("veyra.bankClientSecret must be set in veyra.properties for this provider") },
        required(bankBackendBaseUrl), bankSession, http,
    )

    /** Only the bank backend that relays the SDK's calls — no client id, no secret. */
    internal fun proxyProvider(
        bankBackendBaseUrl: String,
        bankSession: () -> String?,
        http: OkHttpClient,
    ): VeyraProvider = BankBackendRelay(required(bankBackendBaseUrl), bankSession, http)

    /** Deprecated, testing only: just the client id and secret. */
    internal fun clientSecretProvider(clientId: String, clientSecret: String): VeyraProvider =
        ClientSecretCredentials(clientId, clientSecret)

    private fun required(baseUrl: String): String =
        baseUrl.trimEnd('/').ifBlank { error("veyra.bankBackendBaseUrl must be set in veyra.properties for this provider") }
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
