package co.veyra.bank

/**
 * What to show for an outcome that is not `APPROVED`.
 *
 * Every SDK call answers with `responseStatus` and `responseStatusReason`. Branch on the status
 * first — it decides what happens next (done, retry with the same reference, retry with a new one,
 * or wait) — and only then use this for the words: the human-readable message, with the reason code
 * beside it so support can look it up in DEVELOPER-GUIDE.md, "Errors and outcomes".
 */
fun outcomeText(reason: String?, message: String?, fallback: String): String {
    val r = reason?.trim()?.takeIf { it.isNotEmpty() }
    val m = message?.trim()?.takeIf { it.isNotEmpty() }
    return when {
        m != null && r != null && !m.startsWith(r) -> "$m ($r)"
        m != null -> m
        r != null -> r
        else -> fallback
    }
}
