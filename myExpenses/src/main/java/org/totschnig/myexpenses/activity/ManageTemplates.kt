/*   This file is part of My Expenses.
 *   My Expenses is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   My Expenses is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with My Expenses.  If not, see <http://www.gnu.org/licenses/>.
*/
package org.totschnig.myexpenses.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import androidx.annotation.VisibleForTesting
import androidx.core.app.NavUtils
import androidx.core.app.TaskStackBuilder
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.contract.TransactionsContract.Transactions
import org.totschnig.myexpenses.dialog.ConfirmationDialogFragment.ConfirmationDialogListener
import org.totschnig.myexpenses.fragment.TemplatesList
import org.totschnig.myexpenses.model.ContribFeature
import org.totschnig.myexpenses.util.PermissionHelper
import org.totschnig.myexpenses.util.crashreporting.CrashHandler
import java.io.Serializable

class ManageTemplates : ProtectedFragmentActivity(), ConfirmationDialogListener, ContribIFace {
    var calledFromCalendarWithId = NOT_CALLED
        private set

    @VisibleForTesting
    override fun getCurrentFragment(): TemplatesList {
        return mListFragment
    }

    private lateinit var mListFragment: TemplatesList


    enum class HelpVariant {
        templates, plans, planner
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHelpVariant(HelpVariant.templates, true)
        setContentView(R.layout.manage_templates)
        setupToolbar(true)
        title = getString(R.string.menu_manage_plans)
        val uriString = intent.getStringExtra(CalendarContract.Events.CUSTOM_APP_URI)
        if (uriString != null) {
            val uriPath = Uri.parse(uriString).pathSegments
            try {
                calledFromCalendarWithId =
                    uriPath[uriPath.size - 1].toLong() //legacy uri had account_id/template_id
                if (calledFromCalendarWithId == 0L) { //ignore 0 that were introduced by legacy bug
                    calledFromCalendarWithId = NOT_CALLED
                }
            } catch (e: Exception) {
                CrashHandler.report(e)
            }
        }
        configureFloatingActionButton(R.string.menu_create_template)
        mListFragment =
            supportFragmentManager.findFragmentById(R.id.templates_list) as TemplatesList
    }

    override fun dispatchCommand(command: Int, tag: Any?): Boolean {
        if (super.dispatchCommand(command, tag)) {
            return true
        }
        when (command) {
            R.id.CREATE_COMMAND -> {
                startActivity(Intent(this, ExpenseEdit::class.java).apply {
                    putExtra(Transactions.OPERATION_TYPE, Transactions.TYPE_TRANSACTION)
                    putExtra(ExpenseEdit.KEY_NEW_TEMPLATE, true)
                })
                return true
            }
            R.id.DELETE_COMMAND_DO -> {
                finishActionMode()
                mListFragment.dispatchDeleteDo((tag as LongArray?)!!)
                return true
            }
            R.id.CANCEL_CALLBACK_COMMAND -> {
                finishActionMode()
                return true
            }
            else -> return false
        }
    }

    override fun doHome() {
        if (isTaskRoot) {
            val upIntent = NavUtils.getParentActivityIntent(this)
            // create new task
            TaskStackBuilder.create(this).addNextIntentWithParentStack(upIntent!!)
                .startActivities()
        } else {
            // Stay in same task
           super.doHome()
        }
    }

    private fun finishActionMode() {
        mListFragment.finishActionMode()
    }

    override fun onPermissionsGranted(requestCode: Int, perms: List<String>) {
        super.onPermissionsGranted(requestCode, perms)
        if (requestCode == PermissionHelper.PERMISSIONS_REQUEST_WRITE_CALENDAR) {
            mListFragment.loadData()
        }
    }

    override fun contribFeatureCalled(feature: ContribFeature, tag: Serializable?) {
        if (feature == ContribFeature.SPLIT_TRANSACTION) {
            if (tag is Long) {
                mListFragment.dispatchCreateInstanceEditDo((tag as Long?)!!)
            } else if (tag is LongArray) {
                mListFragment.dispatchCreateInstanceSaveDo((tag as LongArray?)!!)
            }
        }
    }

    companion object {
        const val NOT_CALLED = -1L
    }
}