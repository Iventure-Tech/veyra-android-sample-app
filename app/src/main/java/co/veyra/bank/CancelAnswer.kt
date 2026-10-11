package co.veyra.bank

import co.veyra.common.cancel.CancelResult

/**
 * What a screen does with the SDK's answer to a cancel — one rule for every Cancel button in this
 * app (card tap, customer-QR charge, payment-QR create, scan-to-pay, add card).
 *
 * The cancel's answer is not the call's outcome: the call still delivers its own result to its
 * callback. So a screen never shows "Cancelled" off the answer alone.
 */
enum class CancelAction {
    /**
     * `CANCELLED`: nothing was sent. The call's own callback now delivers `NOT_SENT` / `CANCELLED`,
     * and the outcome handler leaves the screen (the same reference can be used again).
     */
    AWAIT_CANCELLED_OUTCOME,

    /**
     * The request has already left the device (or the call has just finished): the call carries on
     * and its normal result arrives on the callback. Stay on the screen and keep waiting; when the
     * request has left ([cancelRefusedTooLate]) say "This can no longer be cancelled".
     */
    KEEP_WAITING,

    /** Nothing is in progress under that reference: nothing to wait for, just leave. */
    LEAVE,
}

fun cancelAction(result: CancelResult): CancelAction = when (result) {
    CancelResult.CANCELLED -> CancelAction.AWAIT_CANCELLED_OUTCOME
    CancelResult.TOO_LATE_TO_CANCEL_REQUEST_ALREADY_SENT,
    CancelResult.TOO_LATE_TO_CANCEL_TASK_ALREADY_PROCESSED -> CancelAction.KEEP_WAITING
    CancelResult.NO_PAYMENT_IN_PROGRESS_FOR_ORDER_ID,
    CancelResult.NO_DIGITISATION_IN_PROGRESS_FOR_CLIENT_REQUEST_ID -> CancelAction.LEAVE
}

/** Only a refused cancel whose request has left tells the user; a just-finished call shows its result. */
fun cancelRefusedTooLate(result: CancelResult): Boolean =
    result == CancelResult.TOO_LATE_TO_CANCEL_REQUEST_ALREADY_SENT
