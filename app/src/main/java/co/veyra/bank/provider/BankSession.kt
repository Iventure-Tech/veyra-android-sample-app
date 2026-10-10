package co.veyra.bank.provider

import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * The signed-in user's **bank session**: an access token from your bank's authorization server,
 * obtained by logging the user in with their username and password (OAuth 2.0 password grant).
 * Both providers use it — the assertion provider as the token exchange's `subject_token`, the
 * proxy provider as the `Authorization: Bearer` on every relayed call.
 *
 * Request: `POST {bankBackendBaseUrl}/oauth2/token`, your bank's client authenticated with HTTP
 * Basic `bankClientId:bankClientSecret`, form `grant_type=password&username=…&password=…`.
 * Response: `{"access_token": "…", "expires_in": 3600, …}`.
 *
 * The token is cached until shortly before it expires. A refused login (400 or 401 — wrong
 * credentials) or missing credentials mean "nobody is signed in": [invoke] returns null, which
 * both providers report to the SDK as not authenticated, so nothing is sent. Any other failure
 * throws, with the same effect.
 *
 * This demo reads the username and password from local config; a real app takes them from its
 * login screen and never stores the password.
 *
 * Called on the SDK's background threads (the providers run it off the main thread); one login
 * at a time.
 */
class BankSession(
    private val bankBackendBaseUrl: String,
    private val bankClientId: String,
    private val bankClientSecret: String,
    private val username: () -> String?,
    private val password: () -> String?,
    private val http: OkHttpClient,
    private val clock: () -> Long = System::currentTimeMillis,
) : () -> String? {

    private class Cached(val token: String, val expiresAtMillis: Long)

    @Volatile
    private var cached: Cached? = null

    /** The current session token, logging in when there is none (or it is about to expire). */
    @Synchronized
    override fun invoke(): String? {
        cached?.let { if (clock() < it.expiresAtMillis) return it.token }
        cached = null
        val user = username()?.takeIf { it.isNotBlank() } ?: return null
        val pass = password()?.takeIf { it.isNotEmpty() } ?: return null
        val request = Request.Builder()
            .url("$bankBackendBaseUrl/oauth2/token")
            .header("Authorization", Credentials.basic(bankClientId, bankClientSecret))
            .post(
                FormBody.Builder()
                    .add("grant_type", "password")
                    .add("username", user)
                    .add("password", pass)
                    .build(),
            )
            .build()
        return http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            when {
                response.code == 400 || response.code == 401 -> null // credentials refused: signed out
                !response.isSuccessful -> throw IllegalStateException("bank login answered HTTP ${response.code}")
                else -> {
                    val json = JSONObject(body)
                    val token = json.optString("access_token").takeIf { it.isNotBlank() }
                        ?: throw IllegalStateException("bank login answered without an access_token")
                    val lifetimeSeconds = json.optLong("expires_in", DEFAULT_LIFETIME_SECONDS)
                    val expiresAt = clock() + (lifetimeSeconds - EXPIRY_MARGIN_SECONDS).coerceAtLeast(0) * 1000
                    cached = Cached(token, expiresAt)
                    token
                }
            }
        }
    }

    /** Forget the session (sign-out): the next call logs in again. */
    fun clear() {
        cached = null
    }

    private companion object {
        /** When the server does not say, assume a short life rather than a long one. */
        const val DEFAULT_LIFETIME_SECONDS = 300L
        /** Renew a little early, so a token never expires in flight. */
        const val EXPIRY_MARGIN_SECONDS = 30L
    }
}
