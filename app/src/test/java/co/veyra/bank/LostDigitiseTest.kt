package co.veyra.bank

import co.veyra.wallet.sdk.DigitiseResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LostDigitiseTest {

    private fun result(status: String?, reason: String?, clientRequestId: String? = "crid-1") = DigitiseResult(
        tokenUniqueReference = null, responseCode = null, message = null,
        responseStatus = status, responseStatusReason = reason,
        activationMethods = emptyList(), tokenStored = false, clientRequestId = clientRequestId,
    )

    @Test
    fun `a sent digitise with no usable answer is cleaned up`() {
        assertTrue(shouldDeactivateLostDigitise(result("FAILED", "NO_RESPONSE_RECEIVED")))
        assertTrue(shouldDeactivateLostDigitise(result("FAILED", "UPSTREAM_ERROR")))
    }

    @Test
    fun `nothing to clean up when nothing was sent, the server refused, or the card was added`() {
        assertFalse(shouldDeactivateLostDigitise(result("NOT_SENT", "NO_NETWORK_CONNECTION")))
        assertFalse(shouldDeactivateLostDigitise(result("FAILED", "DUPLICATE_CLIENT_REQUEST_ID")))
        assertFalse(shouldDeactivateLostDigitise(result("DECLINED", "ACCOUNT_BLOCKED")))
        assertFalse(shouldDeactivateLostDigitise(result("APPROVED", null)))
    }

    @Test
    fun `no id means nothing to name`() {
        assertFalse(shouldDeactivateLostDigitise(result("FAILED", "NO_RESPONSE_RECEIVED", clientRequestId = null)))
        assertFalse(shouldDeactivateLostDigitise(result("FAILED", "NO_RESPONSE_RECEIVED", clientRequestId = "")))
    }
}
