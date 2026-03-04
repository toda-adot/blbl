package blbl.cat3399.core.ui

import androidx.recyclerview.widget.RecyclerView

/**
 * Helpers for "refresh" focus behavior:
 * - During refresh, we don't try to restore the exact previous card.
 * - After data is applied, focus should deterministically land on the first content card (position 0).
 */
internal fun RecyclerView.requestFocusFirstItemOrSelfAfterRefresh(
    itemCount: Int,
    isAlive: () -> Boolean,
    smoothScroll: Boolean = false,
    onDone: (focusedFirstItem: Boolean) -> Unit = {},
): Boolean {
    if (itemCount <= 0) {
        requestFocus()
        onDone(false)
        return true
    }

    return requestFocusAdapterPositionReliable(
        position = 0,
        smoothScroll = smoothScroll,
        isAlive = isAlive,
        onFocused = { onDone(true) },
    )
}

internal fun parkFocusForDataSetReset(vararg controllers: DpadGridController?) {
    for (c in controllers) c?.parkFocusForDataSetReset()
}

internal fun unparkFocusAfterDataSetReset(vararg controllers: DpadGridController?) {
    for (c in controllers) c?.unparkFocusAfterDataSetReset()
}

