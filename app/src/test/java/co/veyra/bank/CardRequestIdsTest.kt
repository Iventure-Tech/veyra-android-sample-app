package co.veyra.bank

import co.veyra.wallet.sdk.DigitiseResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardRequestIdsTest {

    private fun result(status: String, code: String?, tur: String?, clientRequestId: String? = "crid-1") = DigitiseResult(
        tokenUniqueReference = tur, responseCode = code, message = null,
        responseStatus = status, responseStatusReason = null,
        activationMethods = emptyList(), tokenStored = tur != null, clientRequestId = clientRequestId,
    )

    @Test
    fun `an added card is recorded with its add-card id`() {
        assertEquals("TUR-1" to "crid-1", cardRequestId(result("APPROVED", "APPROVED", "TUR-1")))
    }

    @Test
    fun `a card that still needs activating is recorded too`() {
        assertEquals("TUR-1" to "crid-1", cardRequestId(result("APPROVED", "APPROVE_REQUIRE_AUTH", "TUR-1")))
    }

    @Test
    fun `nothing is recorded when no card was added`() {
        assertNull(cardRequestId(result("DECLINED", "DECLINED", null)))
        assertNull(cardRequestId(result("FAILED", null, null)))
        assertNull(cardRequestId(result("APPROVED", "APPROVED", null)))
    }

    @Test
    fun `nothing is recorded without an id`() {
        assertNull(cardRequestId(result("APPROVED", "APPROVED", "TUR-1", clientRequestId = null)))
        assertNull(cardRequestId(result("APPROVED", "APPROVED", "TUR-1", clientRequestId = "")))
    }
}
