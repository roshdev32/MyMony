package org.totschnig.myexpenses.adapter

import android.content.Context
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseExpandableListAdapter
import android.widget.ExpandableListView
import android.widget.ImageView
import android.widget.TextView
import androidx.core.util.Pair
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.model.Account
import org.totschnig.myexpenses.model.CurrencyContext
import org.totschnig.myexpenses.sync.json.AccountMetaData

class SyncBackendAdapter(
    context: Context,
    private val currencyContext: CurrencyContext,
    private var syncAccounts: List<Pair<String, Boolean>>
) : BaseExpandableListAdapter() {
    enum class SyncState {
        SYNCED_TO_THIS, SYNCED_TO_OTHER, UNSYNCED, UNKNOWN, ERROR
    }

    private val accountMetaDataMap = SparseArray<List<Result<AccountMetaData>>?>()
    private val layoutInflater = LayoutInflater.from(context)
    private var localAccountInfo: Map<String, String?>? = null

    fun getMetaData(groupPosition: Int, childPosititon: Int) = getChild(groupPosition, childPosititon).getOrNull()

    override fun getChild(groupPosition: Int, childPosititon: Int) =
        getChildList(groupPosition)!![childPosititon]

    private fun getChildList(groupPosition: Int) = accountMetaDataMap[groupPosition]

    override fun getChildId(groupPosition: Int, childPosition: Int) = childPosition.toLong()

    override fun getChildView(
        groupPosition: Int, childPosition: Int,
        isLastChild: Boolean, convertView: View?, parent: ViewGroup
    ): View {
        val result = convertView ?: layoutInflater.inflate(R.layout.sync_account_row, parent, false)
        val accountMetaData = getChild(groupPosition, childPosition)
        val syncStateView = result.findViewById<ImageView>(R.id.state)
        val labelTextView = result.findViewById<TextView>(R.id.label)
        accountMetaData.onSuccess {
            labelTextView.text = it.toString()
            result.findViewById<View>(R.id.color1)
                .setBackgroundColor(it.color())
            when (getSyncState(groupPosition, childPosition)) {
                SyncState.UNKNOWN, SyncState.ERROR -> syncStateView.visibility = View.GONE
                SyncState.SYNCED_TO_THIS -> {
                    syncStateView.visibility = View.VISIBLE
                    syncStateView.setImageResource(R.drawable.ic_sync)
                }
                SyncState.UNSYNCED, SyncState.SYNCED_TO_OTHER -> {
                    syncStateView.visibility = View.VISIBLE
                    syncStateView.setImageResource(R.drawable.ic_action_sync_unlink)
                }
            }
        }.onFailure {
            syncStateView.visibility = View.GONE
            labelTextView.text = it.message
        }
        return result
    }

    override fun getChildrenCount(groupPosition: Int): Int {
        val childList = getChildList(groupPosition)
        return childList?.size ?: 0
    }

    override fun getGroup(groupPosition: Int): Pair<String, Boolean> {
        return syncAccounts[groupPosition]
    }

    fun getBackendLabel(groupPosition: Int): String {
        return getGroup(groupPosition).first
    }

    fun isEncrypted(packedPosition: Long): Boolean {
        val groupPosition = ExpandableListView.getPackedPositionGroup(packedPosition)
        return getGroup(groupPosition).second
    }

    override fun getGroupCount(): Int {
        return syncAccounts.size
    }

    override fun getGroupId(groupPosition: Int): Long {
        return groupPosition.toLong()
    }

    override fun getGroupView(
        groupPosition: Int, isExpanded: Boolean,
        convertView: View?, parent: ViewGroup
    ): View {
        val result = convertView ?: layoutInflater.inflate(R.layout.sync_backend_row, parent, false)
        val group = getGroup(groupPosition)
        val headerTitle = group.first
        result.findViewById<TextView>(R.id.label).text = headerTitle
        result.findViewById<View>(R.id.state).visibility =
            if (group.second) View.VISIBLE else View.GONE
        return result
    }

    override fun hasStableIds(): Boolean {
        return false
    }

    override fun isChildSelectable(groupPosition: Int, childPosition: Int): Boolean {
        return true
    }

    fun setAccountList(accountList: List<Pair<String, Boolean>>) {
        syncAccounts = accountList
        accountMetaDataMap.clear()
        notifyDataSetChanged()
    }

    fun setAccountMetadata(
        groupPosition: Int,
        accountMetaDataList: List<Result<AccountMetaData>>?
    ) {
        accountMetaDataMap.put(groupPosition, accountMetaDataList)
        notifyDataSetChanged()
    }

    fun hasAccountMetadata(groupPosition: Int): Boolean {
        return accountMetaDataMap[groupPosition] != null
    }

    fun setLocalAccountInfo(uuid2syncMap: Map<String, String?>?) {
        localAccountInfo = uuid2syncMap
        notifyDataSetChanged()
    }

    fun getSyncState(packedPosition: Long): SyncState {
        return getSyncState(
            ExpandableListView.getPackedPositionGroup(packedPosition),
            ExpandableListView.getPackedPositionChild(packedPosition)
        )
    }

    private fun getSyncState(groupPosition: Int, childPosition: Int): SyncState {
        val syncAccount = getBackendLabel(groupPosition)
        val result = getChild(groupPosition, childPosition)
        result.onSuccess { accountMetaData ->
            val uuid = accountMetaData.uuid()
            localAccountInfo?.let {
                if (it.containsKey(uuid)) {
                    return when(it[uuid]) {
                        syncAccount -> SyncState.SYNCED_TO_THIS
                        null -> SyncState.UNSYNCED
                        else -> SyncState.SYNCED_TO_OTHER
                    }
                }
            }
        }
        return if (result.isSuccess) {
            SyncState.UNKNOWN
        } else {
            SyncState.ERROR
        }
    }

    fun getAccountForSync(packedPosition: Long): Account? {
        val groupPosition = ExpandableListView.getPackedPositionGroup(packedPosition)
        val result = getChild(
            groupPosition,
            ExpandableListView.getPackedPositionChild(packedPosition)
        )
        result.onSuccess { accountMetaData ->
            return accountMetaData.toAccount(currencyContext, getBackendLabel(groupPosition))
        }
        return null
    }

    fun getSyncAccountName(packedPosition: Long): String {
        val groupPosition = ExpandableListView.getPackedPositionGroup(packedPosition)
        return getBackendLabel(groupPosition)
    }

}