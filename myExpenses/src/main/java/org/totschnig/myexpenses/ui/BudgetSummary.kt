package org.totschnig.myexpenses.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.core.view.isVisible
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.databinding.BudgetSummaryBinding
import org.totschnig.myexpenses.databinding.BudgetTotalTableBinding
import org.totschnig.myexpenses.model.Money
import org.totschnig.myexpenses.util.ColorUtils.getComplementColor
import org.totschnig.myexpenses.util.CurrencyFormatter
import org.totschnig.myexpenses.util.UiUtils
import org.totschnig.myexpenses.util.formatMoney
import org.totschnig.myexpenses.util.getBackgroundForAvailable
import org.totschnig.myexpenses.viewmodel.data.Budget
import kotlin.math.roundToInt

class BudgetSummary @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding = BudgetSummaryBinding.inflate(LayoutInflater.from(context), this)
    private val tableBinding = BudgetTotalTableBinding.bind(binding.root)

    fun bind(budget: Budget, spent: Long, allocated: Long, currencyFormatter: CurrencyFormatter) {
        binding.budgetProgressTotal.finishedStrokeColor = budget.color
        binding.budgetProgressTotal.unfinishedStrokeColor = getComplementColor(budget.color)
        tableBinding.totalBudget.text = currencyFormatter.formatMoney(Money(budget.currency, allocated))
        tableBinding.totalAmount.text = currencyFormatter.formatMoney(Money(budget.currency, -spent))
        val available = allocated - spent
        tableBinding.totalAvailable.text = currencyFormatter.formatMoney(Money(budget.currency, available))
        val onBudget = available >= 0
        tableBinding.totalAvailable.setBackgroundResource(getBackgroundForAvailable(onBudget))
        tableBinding.totalAvailable.setTextColor(context.resources.getColor(if (onBudget) R.color.colorIncome else R.color.colorExpense))
        val progress = if (allocated == 0L) 100 else (spent * 100f / allocated).roundToInt()
        UiUtils.configureProgress(binding.budgetProgressTotal, progress)
    }
}