package org.totschnig.myexpenses.ui

import android.content.Context
import android.content.ContextWrapper
import android.os.Parcelable
import android.util.AttributeSet
import android.view.View
import androidx.annotation.CallSuper
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.google.android.material.button.MaterialButton
import icepick.Icepick
import icepick.State
import org.totschnig.myexpenses.util.crashreporting.CrashHandler

abstract class ButtonWithDialog<T: DialogFragment> @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : MaterialButton(context, attrs, defStyleAttr) {

    abstract val fragmentTag: String

    @State
    @JvmField
    var dialogShown = false

    private fun showDialog() {
        val picker = buildDialog()
        attachListener(picker)
        picker.show((context as FragmentActivity).supportFragmentManager, fragmentTag)
        dialogShown = true
    }

    abstract fun buildDialog(): T

    override fun onSaveInstanceState(): Parcelable {
        return Icepick.saveInstanceState(this, super.onSaveInstanceState())
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        super.onRestoreInstanceState(Icepick.restoreInstanceState(this, state))
        update()
        if (dialogShown) {
            reAttachListener((context as FragmentActivity).supportFragmentManager)
        }
    }

    abstract fun update()

    abstract fun attachListener(dialogFragment: T)

    private fun reAttachListener(supportFragmentManager: FragmentManager) {
        (supportFragmentManager.findFragmentByTag(fragmentTag) as? T)?.apply {
            attachListener(this)
        } ?: kotlin.run {
            CrashHandler.report(Exception("reAttachListener failed"))
        }
    }

    protected val host: Host
        get() {
            var context = context
            while (context is ContextWrapper) {
                if (context is Host) {
                    return context
                }
                context = context.baseContext
            }
            throw IllegalStateException("Host context does not implement interface")
        }

    interface Host {
        fun onValueSet(view: View)
    }

    open fun onClick() {
        showDialog()
    }

    init {
        setOnClickListener { onClick() }
    }
}