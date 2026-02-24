package blbl.cat3399.core.ui.popup

import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.LayoutInflater
import android.widget.TextView
import androidx.activity.ComponentActivity
import blbl.cat3399.R
import blbl.cat3399.core.ui.AppToast
import blbl.cat3399.core.ui.userScaledContext

object AppNotice {
    private const val DURATION_SHORT_MS: Long = 2000L
    private const val DURATION_LONG_MS: Long = 3500L

    private val mainHandler = Handler(Looper.getMainLooper())

    fun show(context: Context, text: CharSequence) {
        showInternal(context = context, text = text, durationMs = DURATION_SHORT_MS)
    }

    fun showLong(context: Context, text: CharSequence) {
        showInternal(context = context, text = text, durationMs = DURATION_LONG_MS)
    }

    private fun showInternal(context: Context, text: CharSequence, durationMs: Long) {
        if (text.isBlank()) return

        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { showInternal(context = context, text = text, durationMs = durationMs) }
            return
        }

        val activity = context.findActivity()
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            AppToast.show(context, text)
            return
        }

        val dialogContext = activity.userScaledContext()
        val view =
            LayoutInflater.from(dialogContext)
                .inflate(R.layout.view_popup_notice, null, false)

        val tvText = view.findViewById<TextView>(R.id.tv_text)
        tvText.text = text
        tvText.maxWidth =
            (dialogContext.resources.displayMetrics.widthPixels * 0.92f)
                .toInt()
                .coerceAtLeast(dp(dialogContext, 220f))

        PopupHost.from(activity).showNotice(view = view, durationMs = durationMs)
    }

    private fun dp(context: Context, value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics).toInt()

    private fun Context.findActivity(): ComponentActivity? {
        var current: Context? = this
        while (current is ContextWrapper) {
            if (current is ComponentActivity) return current
            current = current.baseContext
        }
        return current as? ComponentActivity
    }
}
