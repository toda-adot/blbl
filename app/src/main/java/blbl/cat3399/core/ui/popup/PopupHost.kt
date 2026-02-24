package blbl.cat3399.core.ui.popup

import androidx.activity.ComponentActivity
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import blbl.cat3399.R
import blbl.cat3399.core.ui.FocusReturn
import blbl.cat3399.core.ui.FocusTreeUtils
import blbl.cat3399.core.ui.userScaledContext
import java.lang.ref.WeakReference
import kotlin.math.max

internal class PopupHost private constructor(
    private val activity: ComponentActivity,
    private val contentRoot: FrameLayout,
    private val hostView: FrameLayout,
    private val noticeLayer: FrameLayout,
    private val modalLayer: FrameLayout,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    private var consumeBackLikeKeyUp: Boolean = false

    private var systemBarsInsets = Insets.NONE
    private var imeInsets = Insets.NONE

    private var modalEntry: ModalEntry? = null

    private data class ModalEntry(
        val rootView: View,
        val cancelable: Boolean,
        val preferredFocusView: View?,
        var lastFocusedView: WeakReference<View>?,
        val focusReturn: FocusReturn,
        val backCallback: OnBackPressedCallback,
        val focusListener: ViewTreeObserver.OnGlobalFocusChangeListener,
        val onDismiss: (() -> Unit)?,
    )

    fun showModal(
        title: CharSequence?,
        contentView: View,
        cancelable: Boolean,
        actions: List<PopupAction>,
        preferredActionRole: PopupActionRole? = PopupActionRole.POSITIVE,
        autoFocus: Boolean = true,
        onModalAttached: ((modalRoot: View) -> Unit)? = null,
        onDismiss: (() -> Unit)? = null,
    ): PopupHandle {
        checkMainThread()

        dismissModalInternal(animate = false, restoreFocus = false)

        val dialogContext = activity.userScaledContext()
        val root =
            LayoutInflater.from(dialogContext)
                .inflate(R.layout.view_popup_modal, modalLayer, false)

        val overlay = root.findViewById<View>(R.id.overlay)
        val card = root.findViewById<View>(R.id.card)
        val tvTitle = root.findViewById<TextView>(R.id.tv_title)
        val content = root.findViewById<FrameLayout>(R.id.content)
        val actionsRow = root.findViewById<LinearLayout>(R.id.actions)

        // The overlay should intercept touch (outside click), but must not take focus.
        overlay.isFocusable = false
        overlay.isFocusableInTouchMode = false

        tvTitle.text = title
        val hasTitle = !title.isNullOrBlank()
        tvTitle.visibility = if (hasTitle) View.VISIBLE else View.GONE
        if (!hasTitle) {
            (content.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                lp.topMargin = 0
                content.layoutParams = lp
            }
        }

        content.removeAllViews()
        content.addView(
            contentView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        // Consume clicks inside the card so "outside click to dismiss" works reliably.
        card.isClickable = true
        card.setOnClickListener { /* consume */ }

        val focusReturn = FocusReturn()
        focusReturn.capture(activity.window?.decorView?.findFocus())

        val backCallback =
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (cancelable) dismissModal()
                }
            }
        activity.onBackPressedDispatcher.addCallback(backCallback)

        // Actions
        actionsRow.removeAllViews()
        actionsRow.visibility = if (actions.isEmpty()) View.GONE else View.VISIBLE
        var preferredFocusView: View? = null
        val actionViewsByRole = HashMap<PopupActionRole, View>(3)
        for ((idx, a) in actions.withIndex()) {
            val btn =
                LayoutInflater.from(dialogContext)
                .inflate(R.layout.item_popup_action, actionsRow, false)
            val tv = btn.findViewById<TextView>(R.id.tv_text)
            tv.text = a.text
            btn.setOnClickListener {
                a.onClick?.invoke()
                if (a.dismissOnClick) dismissModal()
            }
            val lp =
                (btn.layoutParams as? ViewGroup.MarginLayoutParams)
                    ?: ViewGroup.MarginLayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
            if (idx > 0) lp.marginStart = dp(dialogContext, 10f)
            btn.layoutParams = lp
            actionsRow.addView(btn)
            if (!actionViewsByRole.containsKey(a.role)) actionViewsByRole[a.role] = btn
        }

        if (preferredActionRole != null) {
            preferredFocusView = actionViewsByRole[preferredActionRole]
        }
        if (preferredFocusView == null) preferredFocusView = actionViewsByRole[PopupActionRole.POSITIVE]
        if (preferredFocusView == null) preferredFocusView = actionViewsByRole[PopupActionRole.NEUTRAL]
        if (preferredFocusView == null) preferredFocusView = actionViewsByRole[PopupActionRole.NEGATIVE]
        if (preferredFocusView == null) preferredFocusView = actionsRow.getChildAt(0)

        overlay.setOnClickListener {
            if (cancelable) dismissModal()
        }

        // Width + insets.
        applyModalSizeAndInsets(dialogContext = dialogContext, modalRoot = overlay, card = card)

        // Focus trap: when a modal is visible, keep focus within it.
        val focusListener =
            ViewTreeObserver.OnGlobalFocusChangeListener { oldFocus, newFocus ->
                val entry = modalEntry
                if (entry == null) return@OnGlobalFocusChangeListener
                val modalRoot = entry.rootView

                if (!modalRoot.isAttachedToWindow) return@OnGlobalFocusChangeListener
                if (newFocus != null && FocusTreeUtils.isDescendantOf(newFocus, modalRoot)) {
                    entry.lastFocusedView = WeakReference(newFocus)
                    return@OnGlobalFocusChangeListener
                }
                if (newFocus == null) {
                    // Some devices clear focus temporarily while scrolling/recycling.
                    // Bring focus back on the next frame (best-effort).
                    if (oldFocus != null && FocusTreeUtils.isDescendantOf(oldFocus, modalRoot)) {
                        entry.lastFocusedView = WeakReference(oldFocus)
                    }
                    modalRoot.post {
                        if (modalEntry !== entry) return@post
                        restoreModalFocus(entry)
                    }
                    return@OnGlobalFocusChangeListener
                }

                // Bring focus back to the dialog (best-effort).
                modalRoot.post {
                    if (modalEntry !== entry) return@post
                    restoreModalFocus(entry)
                }
            }

        contentRoot.viewTreeObserver.addOnGlobalFocusChangeListener(focusListener)

        // Show
        modalLayer.addView(
            root,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        onModalAttached?.invoke(overlay)

        // Animate in.
        overlay.alpha = 0f
        card.alpha = 0f
        card.scaleX = 0.96f
        card.scaleY = 0.96f
        overlay.animate().alpha(1f).setDuration(160L).start()
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180L).start()

        // Focus after layout.
        if (autoFocus) {
            val focusTarget = preferredFocusView ?: findFirstFocusableDescendant(card)
            focusTarget?.post {
                focusTarget.requestFocus()
            }
        }

        modalEntry =
            ModalEntry(
                rootView = overlay,
                cancelable = cancelable,
                preferredFocusView = preferredFocusView,
                lastFocusedView = null,
                focusReturn = focusReturn,
                backCallback = backCallback,
                focusListener = focusListener,
                onDismiss = onDismiss,
            )

        return object : PopupHandle {
            override val isShowing: Boolean
                get() = modalEntry?.rootView === overlay && overlay.isAttachedToWindow

            override fun dismiss() {
                if (Looper.myLooper() != Looper.getMainLooper()) {
                    mainHandler.post { dismiss() }
                    return
                }
                if (modalEntry?.rootView !== overlay) return
                dismissModal()
            }
        }
    }

    fun dismissModal() {
        checkMainThread()
        dismissModalInternal(animate = true, restoreFocus = true)
    }

    fun showNotice(
        view: View,
        durationMs: Long,
    ): PopupHandle {
        checkMainThread()

        val layer = noticeLayer
        dismissNoticeInternal(animate = false)

        view.isClickable = true
        view.isFocusable = false
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        view.setOnClickListener { dismissNotice() }

        val lp =
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                val marginH = dp(view.context, 24f)
                val marginB = dp(view.context, 32f) + systemBarsInsets.bottom
                setMargins(marginH, 0, marginH, marginB)
            }

        layer.addView(view, lp)
        layer.setTag(R.id.tag_popup_notice_view, view)

        val hideRunnable = Runnable { dismissNotice() }
        layer.setTag(R.id.tag_popup_notice_hide_runnable, hideRunnable)
        mainHandler.postDelayed(hideRunnable, durationMs)

        // Animate in.
        val startOffsetPx = dp(view.context, 16f).toFloat()
        view.alpha = 0f
        view.translationY = startOffsetPx
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(180L)
            .start()

        return object : PopupHandle {
            override val isShowing: Boolean
                get() = layer.getTag(R.id.tag_popup_notice_view) === view && view.isAttachedToWindow

            override fun dismiss() {
                if (Looper.myLooper() != Looper.getMainLooper()) {
                    mainHandler.post { dismiss() }
                    return
                }
                if (layer.getTag(R.id.tag_popup_notice_view) !== view) return
                dismissNotice()
            }
        }
    }

    fun dismissNotice() {
        checkMainThread()
        dismissNoticeInternal(animate = true)
    }

    private fun dismissNoticeInternal(animate: Boolean) {
        checkMainThread()
        val layer = noticeLayer
        val hide = layer.getTag(R.id.tag_popup_notice_hide_runnable) as? Runnable
        if (hide != null) mainHandler.removeCallbacks(hide)

        val current = layer.getTag(R.id.tag_popup_notice_view) as? View
        layer.setTag(R.id.tag_popup_notice_view, null)
        layer.setTag(R.id.tag_popup_notice_hide_runnable, null)
        if (current == null) return

        current.animate().cancel()
        if (!animate) {
            runCatching { layer.removeView(current) }
            return
        }

        val endOffsetPx = dp(current.context, 12f).toFloat()
        current.animate()
            .alpha(0f)
            .translationY(endOffsetPx)
            .setDuration(160L)
            .withEndAction {
                if (current.parent === layer) {
                    runCatching { layer.removeView(current) }
                }
            }
            .start()
    }

    fun applyInsets(insets: WindowInsetsCompat) {
        systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
        modalEntry?.rootView?.let { modalRoot ->
            val card = modalRoot.findViewById<View>(R.id.card) ?: return@let
            applyModalSizeAndInsets(dialogContext = modalRoot.context, modalRoot = modalRoot, card = card)
        }

        (noticeLayer.getTag(R.id.tag_popup_notice_view) as? View)?.let { notice ->
            (notice.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
                val marginH = dp(notice.context, 24f)
                val marginB = dp(notice.context, 32f) + systemBarsInsets.bottom
                if (lp.leftMargin != marginH || lp.rightMargin != marginH || lp.bottomMargin != marginB) {
                    lp.leftMargin = marginH
                    lp.rightMargin = marginH
                    lp.bottomMargin = marginB
                    notice.layoutParams = lp
                }
            }
        }
    }

    private fun applyModalSizeAndInsets(dialogContext: android.content.Context, modalRoot: View, card: View) {
        val bottom = max(systemBarsInsets.bottom, imeInsets.bottom)
        modalRoot.setPadding(systemBarsInsets.left, systemBarsInsets.top, systemBarsInsets.right, bottom)

        val dm = dialogContext.resources.displayMetrics
        val maxWidthPx = dp(dialogContext, 600f)
        val targetWidthPx = (dm.widthPixels * 0.90f).toInt().coerceAtMost(maxWidthPx).coerceAtLeast(1)
        (card.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
            if (lp.width != targetWidthPx) {
                lp.width = targetWidthPx
                card.layoutParams = lp
            }
        }
    }

    private fun dp(context: android.content.Context, value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics).toInt()

    private fun findFirstFocusableDescendant(root: View): View? {
        if (root.isShown && root.isFocusable) return root
        val vg = root as? ViewGroup ?: return null
        for (i in 0 until vg.childCount) {
            val child = vg.getChildAt(i) ?: continue
            findFirstFocusableDescendant(child)?.let { return it }
        }
        return null
    }

    private fun restoreModalFocus(entry: ModalEntry) {
        val modalRoot = entry.rootView
        if (!modalRoot.isAttachedToWindow) return

        val last = entry.lastFocusedView?.get()
        if (last != null && last.isAttachedToWindow && last.isShown && last.isFocusable && last.requestFocus()) return

        val desired = entry.preferredFocusView
        if (desired != null && desired.isAttachedToWindow && desired.isShown && desired.isFocusable && desired.requestFocus()) return

        val cardRoot = modalRoot.findViewById<View>(R.id.card) ?: modalRoot
        if (findFirstFocusableDescendant(cardRoot)?.requestFocus() == true) return
    }

    private fun dismissModalInternal(
        animate: Boolean,
        restoreFocus: Boolean,
    ) {
        checkMainThread()
        val entry = modalEntry ?: return
        modalEntry = null

        runCatching { entry.backCallback.remove() }
        runCatching { contentRoot.viewTreeObserver.removeOnGlobalFocusChangeListener(entry.focusListener) }

        val root = entry.rootView
        val card = root.findViewById<View>(R.id.card) ?: root

        root.animate().cancel()
        card.animate().cancel()

        if (!animate) {
            runCatching { (root.parent as? ViewGroup)?.removeView(root) }
            entry.onDismiss?.invoke()
            if (restoreFocus) entry.focusReturn.restoreAndClear()
            return
        }

        // Animate out then remove.
        root.animate().alpha(0f).setDuration(140L).start()
        card.animate()
            .alpha(0f)
            .scaleX(0.98f)
            .scaleY(0.98f)
            .setDuration(140L)
            .withEndAction {
                runCatching { (root.parent as? ViewGroup)?.removeView(root) }
                entry.onDismiss?.invoke()
                if (restoreFocus) entry.focusReturn.restoreAndClear()
            }
            .start()
    }

    private fun checkMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "PopupHost must be used on main thread." }
    }

    fun hasModalView(): Boolean {
        return modalLayer.childCount > 0
    }

    fun consumeBackLikeKeyEventIfNeeded(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val isBackLike =
            keyCode == KeyEvent.KEYCODE_BACK ||
                keyCode == KeyEvent.KEYCODE_ESCAPE ||
                keyCode == KeyEvent.KEYCODE_BUTTON_B

        if (!isBackLike) return false

        if (event.action == KeyEvent.ACTION_UP && consumeBackLikeKeyUp) {
            consumeBackLikeKeyUp = false
            return true
        }
        if (event.action != KeyEvent.ACTION_DOWN) return false

        if (!hasModalView()) return false

        // Consume the matching UP even if the modal is already dismissed (or dismissing).
        consumeBackLikeKeyUp = true

        val entry = modalEntry
        if (entry != null && entry.cancelable) dismissModal()
        return true
    }

    companion object {
        fun from(activity: ComponentActivity): PopupHost {
            val contentRoot = activity.findViewById<FrameLayout>(android.R.id.content)
            val existing = contentRoot.getTag(R.id.tag_popup_host) as? PopupHost
            if (existing != null) return existing

            val hostView =
                FrameLayout(activity).apply {
                    layoutParams =
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT,
                        )
                    clipChildren = false
                    clipToPadding = false
                    isClickable = false
                    isFocusable = false
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }

            val noticeLayer =
                FrameLayout(activity).apply {
                    layoutParams =
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT,
                        )
                    clipChildren = false
                    clipToPadding = false
                    isClickable = false
                    isFocusable = false
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }

            val modalLayer =
                FrameLayout(activity).apply {
                    layoutParams =
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT,
                        )
                    clipChildren = false
                    clipToPadding = false
                    isClickable = false
                    isFocusable = false
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }

            hostView.addView(noticeLayer)
            hostView.addView(modalLayer)
            contentRoot.addView(hostView)

            val host = PopupHost(activity = activity, contentRoot = contentRoot, hostView = hostView, noticeLayer = noticeLayer, modalLayer = modalLayer)
            contentRoot.setTag(R.id.tag_popup_host, host)

            ViewCompat.setOnApplyWindowInsetsListener(hostView) { _, insets ->
                host.applyInsets(insets)
                insets
            }
            ViewCompat.requestApplyInsets(hostView)

            return host
        }

        fun peek(activity: ComponentActivity): PopupHost? {
            val contentRoot = activity.findViewById<FrameLayout>(android.R.id.content)
            return contentRoot.getTag(R.id.tag_popup_host) as? PopupHost
        }
    }
}
