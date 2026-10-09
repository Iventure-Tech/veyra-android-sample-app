package co.veyra.bank.connection

import co.veyra.common.connection.AssertionProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * `DirectWithAssertion`: fetch a short-lived assertion for the signed-in user from **your bank
 * backend's endpoint** (`POST {base}/sdk-assertion`). See the integration guide for the claims it
 * must sign — `iss`, `sub`, `aud` equal to the [audience] the SDK passes here, `exp` ≤ 5 min, a
 * unique `jti`, `acr`, and `cnf.jkt` equal to the [jkt] the SDK passes here.
 *
 * Request `{"jkt": "<jkt>", "audience": "<Veyra API base URL>"}` with your app's own session;
 * response `{"assertion": "<compact JWT>"}`.
 * Returns null when no user is signed in (401), which the SDK reports as `NOT_AUTHENTICATED`
 * without sending anything; any other failure throws, with the same effect.
 */
class BankBackendAssertionProvider(
    private val baseUrl: String,
    /** Your bank app's session. This demo uses a placeholder token from local config. */
    private val bankSession: () -> String?,
    private val http: OkHttpClient,
) : AssertionProvider {

    override suspend fun assertion(jkt: String, audience: String): String? = withContext(Dispatchers.IO) {
        val session = bankSession()?.takeIf { it.isNotBlank() } ?: return@withContext null // logged out
        val request = Request.Builder()
            .url("$baseUrl/sdk-assertion")
            .header("Authorization", "Bearer $session") // your bank session, not a Veyra credential
            .post(JSONObject().put("jkt", jkt).put("audience", audience).toString().toRequestBody(JSON))
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
