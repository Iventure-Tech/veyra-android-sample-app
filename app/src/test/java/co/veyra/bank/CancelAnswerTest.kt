package co.veyra.bank

import co.veyra.common.cancel.CancelResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CancelAnswerTest {

    @Test
    fun `cancelled waits for the call's own NOT_SENT CANCELLED outcome`() {
        assertEquals(CancelAction.AWAIT_CANCELLED_OUTCOME, cancelAction(CancelResult.CANCELLED))
    }

    @Test
    fun `a request already sent keeps waiting and tells the user`() {
        val answer = CancelResult.TOO_LATE_TO_CANCEL_REQUEST_ALREADY_SENT
        assertEquals(CancelAction.KEEP_WAITING, cancelAction(answer))
        assertTrue(cancelRefusedTooLate(answer))
    }

    @Test
    fun `a call that just finished keeps waiting for its result without the too-late message`() {
        val answer = CancelResult.TOO_LATE_TO_CANCEL_TASK_ALREADY_PROCESSED
        assertEquals(CancelAction.KEEP_WAITING, cancelAction(answer))
        assertFalse(cancelRefusedTooLate(answer))
    }

    @Test
    fun `nothing in progress under the reference leaves`() {
        assertEquals(CancelAction.LEAVE, cancelAction(CancelResult.NO_PAYMENT_IN_PROGRESS_FOR_ORDER_ID))
        assertEquals(CancelAction.LEAVE, cancelAction(CancelResult.NO_DIGITISATION_IN_PROGRESS_FOR_CLIENT_REQUEST_ID))
    }

    @Test
    fun `only the request-already-sent answer is the too-late message`() {
        CancelResult.entries.forEach {
            assertEquals(it == CancelResult.TOO_LATE_TO_CANCEL_REQUEST_ALREADY_SENT, cancelRefusedTooLate(it))
        }
    }
}
