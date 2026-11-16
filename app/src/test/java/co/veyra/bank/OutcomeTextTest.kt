package co.veyra.bank

import co.veyra.common.outcome.ResponseStatus
import co.veyra.softpos.payment.sdk.payment.OriginalPayment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class OutcomeTextTest {

    private fun textFor(status: String?) = duplicateOrderText(OriginalPayment(responseStatus = status))

    @Test
    fun `an order held by an accepted payment says it is waiting for approval`() {
        assertEquals(
            "A payment for this order is accepted and waiting for approval - do not take another payment",
            textFor(ResponseStatus.ACCEPTED),
        )
    }

    @Test
    fun `accepted is never worded as paid or as still being confirmed`() {
        assertNotEquals(textFor(ResponseStatus.APPROVED), textFor(ResponseStatus.ACCEPTED))
        assertNotEquals(textFor(ResponseStatus.PENDING), textFor(ResponseStatus.ACCEPTED))
    }

    @Test
    fun `pending and approved keep their own wording`() {
        assertEquals(
            "A payment for this order is still being confirmed - do not take another payment",
            textFor(ResponseStatus.PENDING),
        )
        assertEquals("This order is already paid - do not take another payment", textFor(ResponseStatus.APPROVED))
        assertEquals("This order is already paid - do not take another payment", duplicateOrderText(null))
    }
}
