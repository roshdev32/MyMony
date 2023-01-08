package org.totschnig.myexpenses.viewmodel.data

import android.content.Context
import android.database.Cursor
import org.totschnig.myexpenses.model.CurrencyEnum
import org.totschnig.myexpenses.provider.DatabaseConstants
import org.totschnig.myexpenses.util.Utils
import java.io.Serializable
import java.util.*

data class Currency(val code: String, val displayName: String, val usages: Int = 0) : Serializable {
    val sortClass = when (code) {
        "XXX" -> 3
        "XAU", "XPD", "XPT", "XAG" -> 2
        else -> 1
    }

    override fun toString(): String {
        return displayName
    }

    override fun hashCode(): Int {
        return code.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Currency) return false

        if (code != other.code) return false

        return true
    }

    companion object {
        fun create(code: String, context: Context): Currency {
            return create(code, Utils.localeFromContext(context))
        }

        fun create(code: String, locale: Locale) = Currency(code, findDisplayName(code, locale))

        fun create(cursor: Cursor, locale: Locale): Currency {
            val code = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseConstants.KEY_CODE))
            val label = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseConstants.KEY_LABEL))
            val usages = cursor.getColumnIndex(DatabaseConstants.KEY_USAGES).takeIf { it != -1 }?.let {
                    cursor.getInt(it)
                }
            return Currency(code, label ?: findDisplayName(code, locale), usages ?: 0)
        }

        private fun findDisplayName(code: String, locale: Locale): String {
            try {
                return java.util.Currency.getInstance(code).getDisplayName(locale)
            } catch (ignored: IllegalArgumentException) {
            }
            try {
                return CurrencyEnum.valueOf(code).description
            } catch (ignored: IllegalArgumentException) {
            }
            return code
        }
    }
}