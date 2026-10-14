package co.veyra.bank

import co.veyra.common.outcome.OutcomeReason
import co.veyra.common.outcome.OutcomeStatus
import co.veyra.wallet.sdk.DigitiseResult

/**
 * Whether an add-card whose answer never arrived should be cleaned up with
 * `deactivateDigitise(clientRequestId)` before the user tries again.
 *
 * Only a digitise that was **sent** and got no usable answer qualifies: `FAILED` with
 * `NO_RESPONSE_RECEIVED` or `UPSTREAM_ERROR`. The token provider may have created a token this phone
 * never received. Anything else either created nothing (`NOT_SENT`, a stated refusal) or delivered its
 * token (`APPROVED`), so there is nothing to clean up.
 */
fun shouldDeactivateLostDigitise(result: DigitiseResult): Boolean =
    result.responseStatus == OutcomeStatus.FAILED &&
        result.responseStatusReason in setOf(OutcomeReason.NO_RESPONSE_RECEIVED, OutcomeReason.UPSTREAM_ERROR) &&
        !result.clientRequestId.isNullOrBlank()
