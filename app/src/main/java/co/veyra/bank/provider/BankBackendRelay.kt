package co.veyra.bank.provider

import co.veyra.common.providers.VeyraProxyProvider
import co.veyra.common.providers.VeyraRelayException
import co.veyra.common.net.NetworkFailureKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * A [VeyraProxyProvider]: send every SDK call through **your bank**. The SDK's envelope —
 * `{version, service, method, path, query, headers, body}` — goes, unchanged, as the body of
 * `POST {base}/issuertokengateway/v1/proxy`: the envelope already says which method and which Veyra
 * service, so there is one entry point. Your API gateway checks the app's
 * session, removes the `/issuertokengateway/v1` context and forwards it to your ITG's `/proxy`,
 * which calls Veyra and answers with Veyra's response body — this returns it unchanged.
 *
 * This is called from the SDK's background work too (status polling, key refresh), so it must
 * not depend on a screen being up.
 */
class BankBackendRelay(
    private val baseUrl: String,
    /** The signed-in user's bank session ([BankSession]); null when nobody is signed in. */
    private val bankSession: () -> String?,
    private val http: OkHttpClient,
) : VeyraProxyProvider {

    override suspend fun send(request: String): String = withContext(Dispatchers.IO) {
        val envelope = request
        // No bank session — signed out, or the login itself failed — means the call never left.
        val session = try {
            bankSession()?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            throw VeyraRelayException(NetworkFailureKind.OTHER, neverSent = true, message = "Bank login failed", cause = e)
        } ?: throw VeyraRelayException(NetworkFailureKind.OTHER, neverSent = true, message = "Not signed in to the bank")
        val call = Request.Builder()
            .url("$baseUrl/issuertokengateway/v1/proxy")
            .header("Authorization", "Bearer $session")
            .post(envelope.toRequestBody(JSON))
            .build()
        try {
            http.newCall(call).execute().use { response ->
                val body = response.body?.string().orEmpty()
                // A non-2xx came back from Veyra (or your gateway), so the request was delivered
                // and may have been processed. Your proxy's own failures arrive as a 200 body the
                // SDK recognises — return those unchanged too.
                if (!response.isSuccessful) {
                    throw VeyraRelayException(NetworkFailureKind.OTHER, neverSent = false, httpStatus = response.code)
                }
                body
            }
        } catch (e: VeyraRelayException) {
            throw e
        } catch (e: IOException) {
            throw classify(e)
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        /**
         * Whether the request can have left the device. Only "never got onto the network" and a
         * refused connection are provably unsent (`neverSent = true` ⇒ the payment ends failed);
         * anything after the request may have been written — a timeout, a reset — is
         * `neverSent = false`, so the payment stays pending and is reconciled.
         */
        fun classify(e: IOException): VeyraRelayException = when (e) {
            is UnknownHostException, is NoRouteToHostException ->
                VeyraRelayException(NetworkFailureKind.NO_NETWORK, neverSent = true, message = e.message, cause = e)
            is ConnectException ->
                VeyraRelayException(NetworkFailureKind.CONNECTION_REFUSED, neverSent = true, message = e.message, cause = e)
            is SocketTimeoutException ->
                VeyraRelayException(NetworkFailureKind.TIMEOUT, neverSent = false, message = e.message, cause = e)
            else ->
                VeyraRelayException(NetworkFailureKind.OTHER, neverSent = false, message = e.message, cause = e)
        }
    }
}
