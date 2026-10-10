package co.veyra.bank.provider

import co.veyra.common.providers.VeyraAssertionProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * The assertion provider: exchange the signed-in user's bank session for a short-lived assertion at
 * **your authorization server's token endpoint** (`POST {base}/oauth2/token`), using OAuth 2.0
 * Token Exchange (RFC 8693). See the integration guide for the minimum claims — `iss`, `sub`,
 * `aud` equal to the [audience] the SDK passes here, `iat`, `exp` ≤ 5 min and a unique `jti`.
 *
 * Request (form-encoded, your bank's client authenticated with HTTP Basic `bankClientId:bankClientSecret`):
 * `grant_type=urn:ietf:params:oauth:grant-type:token-exchange`, `subject_token=<bank session>`,
 * `subject_token_type=…:token-type:access_token`, `requested_token_type=…:token-type:jwt`,
 * `audience=<Veyra API base URL>`. Response: `{"access_token": "<compact JWT>", …}`.
 * Returns null when no user is signed in, or the server refuses the session (401), which the SDK
 * reports as `NOT_AUTHENTICATED` without sending anything; any other failure throws, with the
 * same effect.
 */
class BankBackendAssertionProvider(
    /** The OAuth client id Veyra issued to this app (public, not a secret). The SDK's only credential. */
    override val clientId: String,
    /** Your bank's client at its authorization server (HTTP Basic). Never given to the SDK. */
    private val bankClientId: String,
    /** A secret in an app can be extracted — a real app does this exchange on its own backend. */
    private val bankClientSecret: String,
    /** Your bank backend, whose authorization server serves `/oauth2/token`. */
    private val bankBackendBaseUrl: String,
    /** Your bank app's session. This demo uses a placeholder token from local config. */
    private val bankSession: () -> String?,
    private val http: OkHttpClient,
) : VeyraAssertionProvider {

    override suspend fun assertion(audience: String, jkt: String): String? = withContext(Dispatchers.IO) {
        val subjectToken = bankSession()?.takeIf { it.isNotBlank() } ?: return@withContext null // logged out
        val form = FormBody.Builder()
            .add("grant_type", GRANT_TOKEN_EXCHANGE)
            .add("subject_token", subjectToken)
            .add("subject_token_type", TOKEN_TYPE_ACCESS_TOKEN)
            .add("requested_token_type", TOKEN_TYPE_JWT)
            .add("audience", audience)
            .build()
        val request = Request.Builder()
            .url("$bankBackendBaseUrl/oauth2/token")
            .header("Authorization", Credentials.basic(bankClientId, bankClientSecret))
            .post(form)
            .build()
        http.newCall(request).execute().use { response ->
            parse(response.code, response.body?.string().orEmpty())
        }
    }

    companion object {
        const val GRANT_TOKEN_EXCHANGE = "urn:ietf:params:oauth:grant-type:token-exchange"
        const val TOKEN_TYPE_ACCESS_TOKEN = "urn:ietf:params:oauth:token-type:access_token"
        const val TOKEN_TYPE_JWT = "urn:ietf:params:oauth:token-type:jwt"
        /** The exchanged token from the server's answer: null on 401 (no session), throws otherwise. */
        fun parse(status: Int, body: String): String? = when {
            status == 401 -> null
            status !in 200..299 -> throw IllegalStateException("token exchange answered HTTP $status: $body")
            else -> JSONObject(body).optString("access_token").takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("token exchange answered without an access_token")
        }
    }
}
