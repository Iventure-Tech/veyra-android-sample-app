package co.veyra.bank.connection

import co.veyra.common.providers.VeyraAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * `DirectWithAssertion`: fetch a short-lived assertion for the signed-in user from **your bank
 * backend's endpoint** (`POST {base}/sdk-assertion`). See the integration guide for the minimum
 * claims — `iss`, `sub`, `aud` equal to the [audience] the SDK passes here, `iat`, `exp` ≤ 5 min
 * and a unique `jti` — plus the optional `cnf.jkt` (the [jkt] the SDK passes here) and `acr`.
 *
 * Request `{"audience": "<Veyra API base URL>", "jkt": "<jkt>"}` with your app's own session;
 * response `{"assertion": "<compact JWT>"}`.
 * Returns null when no user is signed in (401), which the SDK reports as `NOT_AUTHENTICATED`
 * without sending anything; any other failure throws, with the same effect.
 */
class BankBackendAssertionProvider(
    /** The OAuth client id Veyra issued to this app (public, not a secret). */
    override val clientId: String,
    private val baseUrl: String,
    /** Your bank app's session. This demo uses a placeholder token from local config. */
    private val bankSession: () -> String?,
    private val http: OkHttpClient,
) : VeyraAuthProvider {

    override suspend fun assertion(audience: String, jkt: String): String? = withContext(Dispatchers.IO) {
        val session = bankSession()?.takeIf { it.isNotBlank() } ?: return@withContext null // logged out
        val request = Request.Builder()
            .url("$baseUrl/sdk-assertion")
            .header("Authorization", "Bearer $session") // your bank session, not a Veyra credential
            .post(JSONObject().put("audience", audience).put("jkt", jkt).toString().toRequestBody(JSON))
            .build()
        http.newCall(request).execute().use { response ->
            parse(response.code, response.body?.string().orEmpty())
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        /** The assertion from your backend's answer: null on 401 (no session), throws otherwise. */
        fun parse(status: Int, body: String): String? = when {
            status == 401 -> null
            status !in 200..299 -> throw IllegalStateException("sdk-assertion answered HTTP $status")
            else -> JSONObject(body).optString("assertion").takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("sdk-assertion answered without an assertion")
        }
    }
}
