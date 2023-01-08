package org.totschnig.myexpenses.test.espresso

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers
import org.hamcrest.Matchers.containsString
import org.junit.Rule
import org.junit.Test
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.activity.ManageCurrencies
import org.totschnig.myexpenses.model.Account
import org.totschnig.myexpenses.model.AccountType
import org.totschnig.myexpenses.model.Money
import org.totschnig.myexpenses.model.Transaction
import org.totschnig.myexpenses.testutils.BaseUiTest
import org.totschnig.myexpenses.viewmodel.data.Currency.Companion.create
import java.math.BigDecimal


class ManageCurrenciesTest : BaseUiTest<ManageCurrencies>() {
    @get:Rule
    var scenarioRule = ActivityScenarioRule(ManageCurrencies::class.java)

    @Test
    fun changeOfFractionDigitsWithUpdateShouldKeepTransactionSum() {
        testHelper(true)
    }

    @Test
    fun changeOfFractionDigitsWithoutUpdateShouldChangeTransactionSum() {
        testHelper(false)
    }

    private fun testHelper(withUpdate: Boolean) {
        val appComponent = app.appComponent
        val currencyContext = appComponent.currencyContext()
        val currencyUnit = currencyContext[CURRENCY_CODE]
        val account = Account("TEST ACCOUNT", currencyUnit, 5000L, "", AccountType.CASH, Account.DEFAULT_COLOR)
        account.save()
        waitForAdapter()
        try {
            val op = Transaction.getNewInstance(account.id)
            op.amount = Money(currencyUnit, -1200L)
            op.save()
            val before = account.totalBalance
            assertThat(before.amountMajor).isEqualByComparingTo(BigDecimal(38))
            val currency = create(CURRENCY_CODE, targetContext)
            onData(Matchers.`is`(currency))
                    .inAdapterView(withId(android.R.id.list)).perform(click())
            onView(withId(R.id.edt_currency_fraction_digits))
                    .perform(replaceText("3"), closeSoftKeyboard())
            Thread.sleep(1000)
            if (withUpdate) {
                onView(withId(R.id.checkBox)).perform(click())
            }
            onView(withId(android.R.id.button1)).perform(click())
            onData(Matchers.`is`(currency))
                .inAdapterView(withId(android.R.id.list)).check(matches(withText(containsString("3"))))
            val after = Account.getInstanceFromDb(account.id).totalBalance
            if (withUpdate) {
                assertThat(after.amountMajor).isEqualByComparingTo(before.amountMajor)
                assertThat((after.amountMinor)).isEqualTo(before.amountMinor * 10)
            } else {
                assertThat(after.amountMajor).isEqualByComparingTo(before.amountMajor.divide(BigDecimal(10)))
                assertThat((after.amountMinor)).isEqualTo(before.amountMinor)
            }
        } finally {
            Account.delete(account.id)
            currencyContext.storeCustomFractionDigits(CURRENCY_CODE, 2)
        }
    }

    override val testScenario: ActivityScenario<ManageCurrencies>
        get() = scenarioRule.scenario
    override val listId: Int
        get() = android.R.id.list

    companion object {
        private const val CURRENCY_CODE = "EUR"
    }
}