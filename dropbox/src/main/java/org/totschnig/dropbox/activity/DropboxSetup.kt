package org.totschnig.dropbox.activity

import android.accounts.AccountManager
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.android.Auth
import icepick.State
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.activity.AbstractSyncSetup
import org.totschnig.myexpenses.activity.SyncBackendSetupActivity
import org.totschnig.myexpenses.provider.DatabaseConstants
import org.totschnig.myexpenses.sync.GenericAccountService
import org.totschnig.dropbox.sync.KEY_DBX_CREDENTIAL
import org.totschnig.myexpenses.util.distrib.DistributionHelper
import org.totschnig.dropbox.viewmodel.DropboxSetupViewModel
import timber.log.Timber

const val APP_KEY = "09ctg08r5gnsh5c"
const val ACTION_RE_AUTHENTICATE = "RE_AUTHENTICATE"

class DropboxSetup : AbstractSyncSetup<DropboxSetupViewModel>() {
    private var oauthStartPending: Boolean = false

    @JvmField
    @State
    var credentialSerialized: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            oauthStartPending = true
        }
    }

    override fun instantiateViewModel() = ViewModelProvider(this)[DropboxSetupViewModel::class.java]

    override fun onResume() {
        super.onResume()
        if (oauthStartPending) {
            val clientIdentifier = "MyExpenses/${DistributionHelper.versionName}"
            val requestConfig = DbxRequestConfig(clientIdentifier)
            Auth.startOAuth2PKCE(this, APP_KEY, requestConfig)
            oauthStartPending = false
        } else {
            Auth.getDbxCredential()?.also {
                credentialSerialized = it.toString()
                Timber.d("Token expires at: %d", it.expiresAt)
                if (intent.action == ACTION_RE_AUTHENTICATE) {
                    with(AccountManager.get(this)) {
                        setUserData(
                            GenericAccountService.getAccount(intent.getStringExtra(DatabaseConstants.KEY_SYNC_ACCOUNT_NAME)!!),
                            KEY_DBX_CREDENTIAL,
                            credentialSerialized
                        )
                    }
                    finish()
                } else {
                    viewModel.initWithCredentials(it)
                    if (!loadFinished) {
                        viewModel.query()
                    }
                }
            } ?: run { abort() }
        }
    }

    override fun buildSuccessIntent(folder: Pair<String, String>) = folder.second.let { name ->
        Intent().apply {
            putExtra(AccountManager.KEY_USERDATA, Bundle(1).apply {
                putString(GenericAccountService.KEY_SYNC_PROVIDER_URL, name)
                putString(KEY_DBX_CREDENTIAL, credentialSerialized)
            })
            putExtra(SyncBackendSetupActivity.KEY_SYNC_PROVIDER_ID, R.id.SYNC_BACKEND_DROPBOX)
            putExtra(AccountManager.KEY_ACCOUNT_NAME, name)
        }
    }

    override fun handleException(exception: Exception) = false
}