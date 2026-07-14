package io.github.hypershell.ui

internal data class RawInputDelta(
    val deleteCount: Int,
    val insertedText: String,
)

internal fun rawInputDelta(previous: String, next: String): RawInputDelta {
    val prefix = previous.commonPrefixWith(next).length
    val suffixLimit = minOf(previous.length - prefix, next.length - prefix)
    var suffix = 0
    while (
        suffix < suffixLimit &&
        previous[previous.lastIndex - suffix] == next[next.lastIndex - suffix]
    ) {
        suffix++
    }
    return RawInputDelta(
        deleteCount = previous.length - prefix - suffix,
        insertedText = next.substring(prefix, next.length - suffix),
    )
}
