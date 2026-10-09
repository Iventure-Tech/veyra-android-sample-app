package co.veyra.bank.connection

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
 * `ViaAppBackend`: send every SDK call through **your bank backend**. The SDK's envelope goes,
 * unchanged, as the body of `POST {base}/veyra-relay/{method}`; your backend authenticates to
 * Veyra with its own client-credentials token, forwards `path`/`query`/`headers`/`body` to the
 * Veyra API unmodified, and answers with Veyra's response body — which this returns unchanged.
 *
 * This is called from the SDK's background work too (status polling, key refresh), so it must
 * not depend on a screen being up.
 */
class BankBackendRelay(
    private val baseUrl: String,
    /** Your bank app's session. This demo uses a placeholder token from local config. */
    private val bankSession: () -> String?,
    private val http: OkHttpClient,
) : VeyraProxyProvider {

    override suspend fun post(request: String) = forward("post", request)
    override suspend fun get(request: String) = forward("get", request)
    override suspend fun put(request: String) = forward("put", request)
    override suspend fun delete(request: String) = forward("delete", request)
    override suspend fun patch(request: String) = forward("patch", request)

    private suspend fun forward(method: String, envelope: String): String = withContext(Dispatchers.IO) {
        val call = Request.Builder()
            .url("$baseUrl/veyra-relay/$method")
            .apply { bankSession()?.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") } }
            .post(envelope.toRequestBody(JSON))
            .build()
        try {
            http.newCall(call).execute().use { response ->
                val body = response.body?.string().orEmpty()
                // Your backend relays Veyra's status: a non-2xx came back from Veyra (or your
                // backend), so the request was delivered and may have been processed.
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
