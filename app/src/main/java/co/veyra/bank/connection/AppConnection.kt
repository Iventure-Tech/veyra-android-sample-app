package co.veyra.bank.connection

import co.veyra.bank.BuildConfig
import co.veyra.common.connection.VeyraConnection
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * How this app connects both SDKs to Veyra, read from the git-ignored `veyra.properties`
 * (see `veyra.properties.example`). The mode is a decision this app makes, so there is no
 * default: an unset or unknown mode stops the app at startup, naming what to set.
 *
 * Both SDKs use the same mode here for simplicity; a real app may choose per SDK.
 */
object AppConnection {

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /** The connection for either SDK. Built per call; the SDK re-binds it on every initialise. */
    fun connection(): VeyraConnection = from(
        mode = BuildConfig.VEYRA_CONNECTION_MODE,
        clientId = BuildConfig.VEYRA_CLIENT_ID,
        clientSecret = BuildConfig.VEYRA_CLIENT_SECRET,
        bankBackendBaseUrl = BuildConfig.BANK_BACKEND_BASE_URL,
        bankSession = { BuildConfig.BANK_SESSION_TOKEN },
        http = http,
    )

    internal fun from(
        mode: String,
        clientId: String,
        clientSecret: String,
        bankBackendBaseUrl: String,
        bankSession: () -> String?,
        http: OkHttpClient,
    ): VeyraConnection = when (mode) {
        "directWithClientSecret" ->
            @Suppress("DEPRECATION")
            VeyraConnection.DirectWithClientSecret(clientId, clientSecret)

        "directWithAssertion" -> VeyraConnection.DirectWithAssertion(
            clientId = clientId,
            assertionProvider = BankBackendAssertionProvider(required(bankBackendBaseUrl), bankSession, http),
        )

        "viaAppBackend" -> VeyraConnection.ViaAppBackend(
            BankBackendRelay(required(bankBackendBaseUrl), bankSession, http),
        )

        else -> error(
            "veyra.connection.mode is not set (got \"$mode\"). Copy veyra.properties.example to " +
                "veyra.properties in the project root and choose directWithClientSecret, " +
                "directWithAssertion or viaAppBackend.",
        )
    }

    private fun required(baseUrl: String): String =
        baseUrl.trimEnd('/').ifBlank { error("veyra.bankBackendBaseUrl must be set in veyra.properties for this mode") }
}
