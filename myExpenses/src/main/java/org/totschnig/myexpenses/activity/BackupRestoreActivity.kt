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
import android.text.TextUtils
import androidx.activity.viewModels
import com.google.android.material.snackbar.Snackbar
import eltos.simpledialogfragment.SimpleDialog.OnDialogResultListener
import eltos.simpledialogfragment.form.Input
import eltos.simpledialogfragment.form.SimpleFormDialog
import icepick.State
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.dialog.BackupSourcesDialogFragment
import org.totschnig.myexpenses.dialog.ConfirmationDialogFragment
import org.totschnig.myexpenses.dialog.ConfirmationDialogFragment.ConfirmationDialogListener
import org.totschnig.myexpenses.dialog.DialogUtils
import org.totschnig.myexpenses.dialog.DialogUtils.CalendarRestoreStrategyChangedListener
import org.totschnig.myexpenses.preference.AccountPreference
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.preference.requireString
import org.totschnig.myexpenses.util.PermissionHelper
import org.totschnig.myexpenses.util.Utils
import org.totschnig.myexpenses.util.crashreporting.CrashHandler
import org.totschnig.myexpenses.util.io.FileUtils
import org.totschnig.myexpenses.util.safeMessage
import org.totschnig.myexpenses.viewmodel.BackupViewModel
import org.totschnig.myexpenses.viewmodel.BackupViewModel.BackupState
import org.totschnig.myexpenses.viewmodel.BackupViewModel.BackupState.Running
import org.totschnig.myexpenses.viewmodel.RestoreViewModel.Companion.KEY_FILE_PATH
import org.totschnig.myexpenses.viewmodel.RestoreViewModel.Companion.KEY_PASSWORD
import org.totschnig.myexpenses.viewmodel.RestoreViewModel.Companion.KEY_RESTORE_PLAN_STRATEGY

class BackupRestoreActivity : RestoreActivity(), ConfirmationDialogListener,
    OnDialogResultListener {
    private val backupViewModel: BackupViewModel by viewModels()

    @JvmField
    @State
    var taskResult = RESULT_OK
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireApplication().appComponent.inject(backupViewModel)
        backupViewModel.getBackupState().observe(this) { backupState: BackupState ->
            val onDismissed: Snackbar.Callback = object : Snackbar.Callback() {
                override fun onDismissed(transientBottomBar: Snackbar, event: Int) {
                    if (event == DISMISS_EVENT_SWIPE || event == DISMISS_EVENT_ACTION) {
                        setResult(taskResult)
                        finish()
                    }
                }
            }
            when (backupState) {
                is BackupState.Prepared -> backupState.appDir.onSuccess {
                    if (supportFragmentManager.findFragmentByTag(FRAGMENT_TAG_CONFIRM_BACKUP) == null) {
                        val isProtected =
                            !TextUtils.isEmpty(prefHandler.getString(PrefKey.EXPORT_PASSWORD, null))
                        val message = StringBuilder().append(
                            getString(
                                R.string.warning_backup,
                                FileUtils.getPath(this, it.uri)
                            )
                        )
                            .append(" ")
                        if (isProtected) {
                            message.append(getString(R.string.warning_backup_protected)).append(" ")
                        } else if (prefHandler.getBoolean(
                                PrefKey.PROTECTION_LEGACY,
                                false
                            ) || prefHandler.getBoolean(
                                PrefKey.PROTECTION_DEVICE_LOCK_SCREEN,
                                false
                            )
                        ) {
                            message.append(unencryptedBackupWarning()).append(" ")
                        }
                        message.append(getString(R.string.continue_confirmation))
                        ConfirmationDialogFragment.newInstance(Bundle().apply {
                            putInt(
                                ConfirmationDialogFragment.KEY_TITLE,
                                if (isProtected) R.string.dialog_title_backup_protected else R.string.menu_backup
                            )
                            putString(
                                ConfirmationDialogFragment.KEY_MESSAGE,
                                message.toString()
                            )
                            putInt(
                                ConfirmationDialogFragment.KEY_COMMAND_POSITIVE,
                                R.id.BACKUP_COMMAND
                            )
                            putInt(
                                ConfirmationDialogFragment.KEY_ICON,
                                if (isProtected) R.drawable.ic_lock else 0
                            )
                            val withSync = prefHandler.getString(
                                PrefKey.AUTO_BACKUP_CLOUD,
                                AccountPreference.SYNCHRONIZATION_NONE
                            )
                            if (withSync != AccountPreference.SYNCHRONIZATION_NONE) {
                                putString(
                                    ConfirmationDialogFragment.KEY_CHECKBOX_LABEL,
                                    getString(R.string.backup_save_to_sync_backend, withSync)
                                )
                                putBoolean(
                                    ConfirmationDialogFragment.KEY_CHECKBOX_INITIALLY_CHHECKED,
                                    prefHandler.getBoolean(
                                        PrefKey.SAVE_TO_SYNC_BACKEND_CHECKED,
                                        false
                                    )
                                )
                            }
                        })
                            .show(supportFragmentManager, FRAGMENT_TAG_CONFIRM_BACKUP)
                    }
                }.onFailure {
                    abort(it.safeMessage)
                }
                is Running -> showSnackBarIndefinite(R.string.menu_backup)
                is BackupState.Completed -> backupState.result.onSuccess { (file, path, extraData) ->
                    if (supportFragmentManager.findFragmentByTag(FRAGMENT_TAG_CONFIRM_PURGE) == null) {
                        var message = getString(R.string.backup_success, path)
                        if (prefHandler.getBoolean(PrefKey.PERFORM_SHARE, false)) {
                            val uris = ArrayList<Uri>()
                            uris.add(file.uri)
                            shareViewModel.share(
                                this, uris,
                                prefHandler.requireString(PrefKey.SHARE_TARGET, "").trim(),
                                "application/zip"
                            )
                        }
                        extraData.fold(
                            ifLeft = { purgeList ->
                                if (purgeList.isEmpty()) {
                                    showDismissibleSnackBar(message, onDismissed)
                                } else {
                                    dismissSnackBar()
                                    ConfirmationDialogFragment.newInstance(Bundle().apply {
                                        putInt(
                                            ConfirmationDialogFragment.KEY_TITLE,
                                            R.string.dialog_title_purge_backups
                                        )
                                        putString(
                                            ConfirmationDialogFragment.KEY_MESSAGE,
                                            message + "\n" + getString(R.string.purge_backups) + "\n" +
                                                    purgeList.joinToString("\n") {
                                                        " • " + (it.name ?: it.uri.toString())
                                                    }
                                        )
                                        putInt(
                                            ConfirmationDialogFragment.KEY_COMMAND_POSITIVE,
                                            R.id.PURGE_BACKUPS_COMMAND
                                        )
                                        putInt(
                                            ConfirmationDialogFragment.KEY_POSITIVE_BUTTON_LABEL,
                                            R.string.menu_delete
                                        )
                                    })
                                        .show(supportFragmentManager, FRAGMENT_TAG_CONFIRM_PURGE)
                                }
                            },
                            ifRight = { list ->
                                message += BackupViewModel.purgeResult2Message(this, list)
                                showDismissibleSnackBar(message, onDismissed)
                            }
                        )
                    }
                }.onFailure {
                    showDismissibleSnackBar(it.safeMessage, onDismissed)
                }
                is BackupState.Purged -> backupState.result.onSuccess {
                    showDismissibleSnackBar(
                        resources.getQuantityString(
                            R.plurals.purge_backup_success,
                            it,
                            it
                        ), onDismissed
                    )
                }.onFailure {
                    CrashHandler.report(it)
                    showDismissibleSnackBar(it.safeMessage, onDismissed)
                }
            }
        }
        if (savedInstanceState != null) {
            return
        }
        when (intent.action ?: "") {
            ACTION_BACKUP -> {
                backupViewModel.prepare()
            }
            ACTION_RESTORE, Intent.ACTION_VIEW -> {
                BackupSourcesDialogFragment.newInstance(intent.data).show(
                    supportFragmentManager, FRAGMENT_TAG_RESTORE_SOURCE
                )
            }
        }
    }

    private fun abort(message: String) {
        showMessage(message)
    }

    private fun showRestoreDialog(bundle: Bundle) {
        bundle.putInt(ConfirmationDialogFragment.KEY_TITLE, R.string.pref_restore_title)
        val message = (getString(
            R.string.warning_restore,
            DialogUtils.getDisplayName(bundle.getParcelable(KEY_FILE_PATH))
        )
                + " " + getString(R.string.continue_confirmation))
        bundle.putString(
            ConfirmationDialogFragment.KEY_MESSAGE,
            message
        )
        bundle.putInt(
            ConfirmationDialogFragment.KEY_COMMAND_POSITIVE,
            R.id.RESTORE_COMMAND
        )
        ConfirmationDialogFragment.newInstance(bundle).show(
            supportFragmentManager,
            "RESTORE"
        )
    }

    private fun buildRestoreArgs(fileUri: Uri, restorePlanStrategy: Int) = Bundle().apply {
        putInt(KEY_RESTORE_PLAN_STRATEGY, restorePlanStrategy)
        putParcelable(KEY_FILE_PATH, fileUri)
    }

    override fun shouldKeepProgress(taskId: Int) = true

    override fun onPostRestoreTask(result: Result<Unit>) {
        super.onPostRestoreTask(result)
        result.onSuccess {
            taskResult = RESULT_RESTORE_OK
        }
    }

    val calledExternally: Boolean
        get() = Intent.ACTION_VIEW == intent.action

    private val calledFromOnboarding: Boolean
        get() = callingActivity?.let {
            Utils.getSimpleClassNameFromComponentName(it)
        } == OnboardingActivity::class.java.simpleName

    fun onSourceSelected(mUri: Uri, restorePlanStrategy: Int) {
        val args = buildRestoreArgs(mUri, restorePlanStrategy)
        backupViewModel.isEncrypted(mUri).observe(this) { result ->
            result.onFailure {
                showDismissibleSnackBar(it.safeMessage, object : Snackbar.Callback() {
                    override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                        finish()
                    }
                })
            }.onSuccess {
                if (it) {
                    SimpleFormDialog.build().msg(R.string.backup_is_encrypted)
                        .fields(
                            Input.password(KEY_PASSWORD)
                                .text(prefHandler.getString(PrefKey.EXPORT_PASSWORD, "")).required()
                        )
                        .extra(args)
                        .show(this, DIALOG_TAG_PASSWORD)
                } else {
                    if (calledFromOnboarding) {
                        doRestore(args)
                    } else {
                        showRestoreDialog(args)
                    }
                }
            }
        }
    }

    override fun onResult(dialogTag: String, which: Int, extras: Bundle): Boolean {
        if (DIALOG_TAG_PASSWORD == dialogTag) {
            if (which == OnDialogResultListener.BUTTON_POSITIVE) {
                if (calledFromOnboarding) {
                    doRestore(extras)
                } else {
                    showRestoreDialog(extras)
                }
            } else {
                abort()
            }
            return true
        }
        return false
    }

    override fun onPositive(args: Bundle, checked: Boolean) {
        val command = args.getInt(ConfirmationDialogFragment.KEY_COMMAND_POSITIVE)
        if (command == R.id.BACKUP_COMMAND) {
            prefHandler.putBoolean(PrefKey.SAVE_TO_SYNC_BACKEND_CHECKED, checked)
            backupViewModel.doBackup(checked)
        } else if (command == R.id.RESTORE_COMMAND) {
            doRestore(args)
        } else if (command == R.id.PURGE_BACKUPS_COMMAND) {
            backupViewModel.purgeBackups()
        }
    }

    override fun onNegative(args: Bundle) {
        abort()
    }

    fun abort() {
        setResult(RESULT_CANCELED)
        finish()
    }

    override fun onDismissOrCancel(args: Bundle) {
        abort()
    }

    override fun onProgressDialogDismiss() {
        if (calledExternally) {
            restartAfterRestore()
        } else {
            setResult(taskResult)
            finish()
        }
    }

    override fun onMessageDialogDismissOrCancel() {
        abort()
    }

    override fun onPermissionsDenied(requestCode: Int, perms: List<String>) {
        super.onPermissionsDenied(requestCode, perms)
        if (requestCode == PermissionHelper.PERMISSIONS_REQUEST_WRITE_CALENDAR) {
            (supportFragmentManager.findFragmentByTag(FRAGMENT_TAG_RESTORE_SOURCE) as? CalendarRestoreStrategyChangedListener)
                ?.onCalendarPermissionDenied()
        }
    }

    override val snackBarContainerId: Int = android.R.id.content

    companion object {
        const val FRAGMENT_TAG_RESTORE_SOURCE = "RESTORE_SOURCE"
        const val FRAGMENT_TAG_CONFIRM_BACKUP = "CONFIRM_BACKUP"
        const val FRAGMENT_TAG_CONFIRM_PURGE = "CONFIRM_PURGE"
        private const val DIALOG_TAG_PASSWORD = "PASSWORD"
        const val ACTION_BACKUP = "BACKUP"
        const val ACTION_RESTORE = "RESTORE"
    }
}