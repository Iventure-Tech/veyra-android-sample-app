package co.veyra.bank

import co.veyra.bank.QrRowStates.MerchantQr
import co.veyra.bank.QrRowStates.ShownQr
import org.junit.Assert.assertEquals
import org.junit.Test

/** What a displayed QR's stored row tells its screen to show. */
class QrRowStatesTest {

    // ── The merchant's QR ────────────────────────────────────────────────────────

    @Test
    fun `an unclaimed merchant QR stays on screen`() {
        assertEquals(MerchantQr.WAITING, QrRowStates.merchantQr(true, "PENDING", null))
        // The row as it is created: no status stated yet.
        assertEquals(MerchantQr.WAITING, QrRowStates.merchantQr(true, null, null))
        assertEquals(MerchantQr.WAITING, QrRowStates.merchantQr(true, "", null))
    }

    @Test
    fun `a claimed merchant QR whose payment is pending comes down`() {
        assertEquals(MerchantQr.CLAIMED_PENDING, QrRowStates.merchantQr(true, "PENDING", true))
        assertEquals(MerchantQr.CLAIMED_PENDING, QrRowStates.merchantQr(true, null, true))
    }

    @Test
    fun `a released merchant QR is payable again`() {
        assertEquals(MerchantQr.WAITING, QrRowStates.merchantQr(true, "PENDING", false))
    }

    @Test
    fun `an accepted push is accepted whether or not the claim was seen`() {
        assertEquals(MerchantQr.ACCEPTED, QrRowStates.merchantQr(true, "ACCEPTED", true))
        assertEquals(MerchantQr.ACCEPTED, QrRowStates.merchantQr(true, "accepted", null))
    }

    @Test
    fun `a final status wins over the claim`() {
        for (status in listOf("APPROVED", "DECLINED", "FAILED", "RETRY")) {
            assertEquals(status, MerchantQr.FINAL, QrRowStates.merchantQr(true, status, true))
            assertEquals(status, MerchantQr.FINAL, QrRowStates.merchantQr(true, status, null))
        }
    }

    @Test
    fun `a status this build does not know keeps waiting on the claim`() {
        assertEquals(MerchantQr.WAITING, QrRowStates.merchantQr(true, "SOMETHING_NEW", null))
        assertEquals(MerchantQr.CLAIMED_PENDING, QrRowStates.merchantQr(true, "SOMETHING_NEW", true))
    }

    @Test
    fun `no row means the QR expired unpaid`() {
        assertEquals(MerchantQr.GONE, QrRowStates.merchantQr(false, null, null))
        assertEquals(MerchantQr.GONE, QrRowStates.merchantQr(false, "PENDING", true))
    }

    // ── The customer's QR ────────────────────────────────────────────────────────

    @Test
    fun `a shown QR nobody has charged stays on screen`() {
        assertEquals(ShownQr.WAITING, QrRowStates.shownQr("PENDING", null))
        assertEquals(ShownQr.WAITING, QrRowStates.shownQr(null, null))
        assertEquals(ShownQr.WAITING, QrRowStates.shownQr("PENDING", false))
    }

    @Test
    fun `a charged QR whose payment is pending comes down`() {
        assertEquals(ShownQr.CHARGED_PENDING, QrRowStates.shownQr("PENDING", true))
    }

    @Test
    fun `an accepted payment is accepted whether or not the charge was seen`() {
        assertEquals(ShownQr.ACCEPTED, QrRowStates.shownQr("ACCEPTED", true))
        assertEquals(ShownQr.ACCEPTED, QrRowStates.shownQr("accepted", null))
    }

    @Test
    fun `a final status wins over the charge`() {
        for (status in listOf("APPROVED", "DECLINED", "FAILED")) {
            assertEquals(status, ShownQr.FINAL, QrRowStates.shownQr(status, true))
            assertEquals(status, ShownQr.FINAL, QrRowStates.shownQr(status, null))
        }
    }

    @Test
    fun `retry is not a final status for a shown QR`() {
        // "No record yet" reads RETRY while the QR is waiting to be scanned.
        assertEquals(ShownQr.WAITING, QrRowStates.shownQr("RETRY", null))
    }
}
