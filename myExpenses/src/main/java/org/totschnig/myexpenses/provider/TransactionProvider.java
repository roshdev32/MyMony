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

package org.totschnig.myexpenses.provider;

import static org.totschnig.myexpenses.model.AggregateAccount.AGGREGATE_HOME_CURRENCY_CODE;
import static org.totschnig.myexpenses.model.AggregateAccount.GROUPING_AGGREGATE;
import static org.totschnig.myexpenses.provider.DatabaseConstants.*;
import static org.totschnig.myexpenses.provider.DbConstantsKt.budgetAllocation;
import static org.totschnig.myexpenses.provider.DbConstantsKt.budgetSelect;
import static org.totschnig.myexpenses.provider.DbConstantsKt.categoryTreeSelect;
import static org.totschnig.myexpenses.provider.DbConstantsKt.categoryTreeWithBudget;
import static org.totschnig.myexpenses.provider.DbConstantsKt.categoryTreeWithMappedObjects;
import static org.totschnig.myexpenses.provider.DbConstantsKt.checkForSealedAccount;
import static org.totschnig.myexpenses.provider.DbConstantsKt.transactionMappedObjectQuery;
import static org.totschnig.myexpenses.provider.DbUtils.suggestNewCategoryColor;
import static org.totschnig.myexpenses.provider.MoreDbUtilsKt.groupByForPaymentMethodQuery;
import static org.totschnig.myexpenses.provider.MoreDbUtilsKt.havingForPaymentMethodQuery;
import static org.totschnig.myexpenses.provider.MoreDbUtilsKt.mapPaymentMethodProjection;
import static org.totschnig.myexpenses.provider.MoreDbUtilsKt.tableForPaymentMethodQuery;
import static org.totschnig.myexpenses.util.PermissionHelper.PermissionGroup.CALENDAR;

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import org.totschnig.myexpenses.BuildConfig;
import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.model.Account;
import org.totschnig.myexpenses.model.CrStatus;
import org.totschnig.myexpenses.model.Grouping;
import org.totschnig.myexpenses.model.Model;
import org.totschnig.myexpenses.model.PaymentMethod;
import org.totschnig.myexpenses.model.Sort;
import org.totschnig.myexpenses.model.Template;
import org.totschnig.myexpenses.model.Transaction;
import org.totschnig.myexpenses.preference.PrefKey;
import org.totschnig.myexpenses.provider.filter.WhereFilter;
import org.totschnig.myexpenses.sync.json.TransactionChange;
import org.totschnig.myexpenses.util.PlanInfoCursorWrapper;
import org.totschnig.myexpenses.util.Utils;
import org.totschnig.myexpenses.util.crashreporting.CrashHandler;
import org.totschnig.myexpenses.util.io.FileCopyUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class TransactionProvider extends BaseTransactionProvider {

  public static final String AUTHORITY = BuildConfig.APPLICATION_ID;
  public static final Uri ACCOUNTS_URI =
      Uri.parse("content://" + AUTHORITY + "/accounts");
  //when we need the accounts cursor without the current balance
  //we do not want the cursor to be reloaded when a transaction is added
  //hence we access it through a different URI
  public static final Uri ACCOUNTS_BASE_URI =
      Uri.parse("content://" + AUTHORITY + "/accountsbase");
  public static final Uri ACCOUNTS_AGGREGATE_URI =
      Uri.parse("content://" + AUTHORITY + "/accounts/aggregates");
  //returns accounts with aggregate accounts, limited to id and label
  public static final Uri ACCOUNTS_MINIMAL_URI =
      Uri.parse("content://" + AUTHORITY + "/accountsMinimal");
  public static final Uri TRANSACTIONS_URI =
      Uri.parse("content://" + AUTHORITY + "/transactions");
  public static final Uri UNCOMMITTED_URI =
      Uri.parse("content://" + AUTHORITY + "/transactions/uncommitted");
  public static final Uri TEMPLATES_URI =
      Uri.parse("content://" + AUTHORITY + "/templates");
  public static final Uri TEMPLATES_UNCOMMITTED_URI =
      Uri.parse("content://" + AUTHORITY + "/templates/uncommitted");
  public static final Uri CATEGORIES_URI =
      Uri.parse("content://" + AUTHORITY + "/categories");
  public static final Uri AGGREGATES_COUNT_URI =
      Uri.parse("content://" + AUTHORITY + "/accounts/aggregatesCount");
  public static final Uri PAYEES_URI =
      Uri.parse("content://" + AUTHORITY + "/payees");
  public static final Uri METHODS_URI =
      Uri.parse("content://" + AUTHORITY + "/methods");
  public static final Uri MAPPED_METHODS_URI =
      Uri.parse("content://" + AUTHORITY + "/methods_transactions");
  public static final Uri ACCOUNTTYPES_METHODS_URI =
      Uri.parse("content://" + AUTHORITY + "/accounttypes_methods");
  public static final Uri SQLITE_SEQUENCE_TRANSACTIONS_URI =
      Uri.parse("content://" + AUTHORITY + "/sqlite_sequence/" + TABLE_TRANSACTIONS);
  public static final Uri PLAN_INSTANCE_STATUS_URI =
      Uri.parse("content://" + AUTHORITY + "/planinstance_transaction");

  public static Uri PLAN_INSTANCE_SINGLE_URI(long templateId, long instanceId) {
    return ContentUris.appendId(ContentUris.appendId(
        TransactionProvider.PLAN_INSTANCE_STATUS_URI.buildUpon(), templateId), instanceId)
        .build();
  }

  public static final Uri CURRENCIES_URI =
      Uri.parse("content://" + AUTHORITY + "/currencies");
  public static final Uri TRANSACTIONS_SUM_URI =
      Uri.parse("content://" + AUTHORITY + "/transactions/sumsForAccounts");
  public static final Uri EVENT_CACHE_URI =
      Uri.parse("content://" + AUTHORITY + "/eventcache");
  public static final Uri DEBUG_SCHEMA_URI =
      Uri.parse("content://" + AUTHORITY + "/debug_schema");
  public static final Uri STALE_IMAGES_URI =
      Uri.parse("content://" + AUTHORITY + "/stale_images");
  public static final Uri MAPPED_TRANSFER_ACCOUNTS_URI =
      Uri.parse("content://" + AUTHORITY + "/transfer_account_transactions");
  public static final Uri CHANGES_URI = Uri.parse("content://" + AUTHORITY + "/changes");

  public static final Uri SETTINGS_URI = Uri.parse("content://" + AUTHORITY + "/settings");

  public static final Uri AUTOFILL_URI = Uri.parse("content://" + AUTHORITY + "/autofill");
  /**
   * select info from DB without table, e.g. CategoryList#DATEINFO_CURSOR
   * or set control flags like sync_state
   */
  public static final Uri DUAL_URI =
      Uri.parse("content://" + AUTHORITY + "/dual");

  public static final Uri ACCOUNT_EXCHANGE_RATE_URI =
      Uri.parse("content://" + AUTHORITY + "/account_exchangerates");

  public static final Uri ACCOUNT_GROUPINGS_URI =
      Uri.parse("content://" + AUTHORITY + "/account_groupings");

  public static final Uri BUDGETS_URI = Uri.parse("content://" + AUTHORITY + "/budgets");

  public static final Uri BUDGET_ALLOCATIONS_URI = Uri.parse("content://" + AUTHORITY + "/budgets/allocations");

  public static final Uri TAGS_URI = Uri.parse("content://" + AUTHORITY + "/tags");

  public static final Uri TRANSACTIONS_TAGS_URI = Uri.parse("content://" + AUTHORITY + "/transactions/tags");

  public static final Uri TEMPLATES_TAGS_URI = Uri.parse("content://" + AUTHORITY + "/templates/tags");

  public static final Uri ACCOUNTS_TAGS_URI = Uri.parse("content://" + AUTHORITY + "/accounts/tags");

  public static final Uri DEBTS_URI = Uri.parse("content://" + AUTHORITY + "/debts");

  public static final String URI_SEGMENT_MOVE = "move";
  public static final String URI_SEGMENT_TOGGLE_CRSTATUS = "toggleCrStatus";
  public static final String URI_SEGMENT_UNDELETE = "undelete";
  public static final String URI_SEGMENT_INCREASE_USAGE = "increaseUsage";
  public static final String URI_SEGMENT_GROUPS = "groups";
  public static final String URI_SEGMENT_UNCOMMITTED = "uncommitted";
  public static final String URI_SEGMENT_CHANGE_FRACTION_DIGITS = "changeFractionDigits";
  public static final String URI_SEGMENT_TYPE_FILTER = "typeFilter";
  public static final String URI_SEGMENT_LAST_EXCHANGE = "lastExchange";
  public static final String URI_SEGMENT_SWAP_SORT_KEY = "swapSortKey";
  public static final String URI_SEGMENT_UNSPLIT = "unsplit";
  public static final String URI_SEGMENT_LINK_TRANSFER = "link_transfer";
  public static final String URI_SEGMENT_SORT_DIRECTION = "sortDirection";
  //"1" merge all currency aggregates, < 0 only return one specific aggregate
  public static final String QUERY_PARAMETER_MERGE_CURRENCY_AGGREGATES = "mergeCurrencyAggregates";
  public static final String QUERY_PARAMETER_FULL_PROJECTION_WITH_SUMS = "fullProjectionWithSums";
  //uses full projection with sums for each account
  public static final Uri ACCOUNTS_FULL_URI = ACCOUNTS_URI.buildUpon()
          .appendQueryParameter(QUERY_PARAMETER_FULL_PROJECTION_WITH_SUMS, "1").build();
  public static final String QUERY_PARAMETER_EXTENDED = "extended";
  public static final String QUERY_PARAMETER_DISTINCT = "distinct";
  public static final String QUERY_PARAMETER_GROUP_BY = "groupBy";
  public static final String QUERY_PARAMETER_MARK_VOID = "markVoid";
  //"1" from production, "2" from test
  public static final String QUERY_PARAMETER_WITH_PLAN_INFO = "withPlanInfo";
  public static final String QUERY_PARAMETER_INIT = "init";
  public static final String QUERY_PARAMETER_CALLER_IS_SYNCADAPTER = "caller_is_syncadapter";
  public static final String QUERY_PARAMETER_MERGE_TRANSFERS = "mergeTransfers";
  private static final String QUERY_PARAMETER_SYNC_BEGIN = "syncBegin";
  private static final String QUERY_PARAMETER_SYNC_END = "syncEnd";
  public static final String QUERY_PARAMETER_WITH_START = "withStart";
  public static final String QUERY_PARAMETER_SECTIONS = "sections";
  public static final String QUERY_PARAMETER_GROUPED_BY_TYPE = "groupedByType";
  public static final String QUERY_PARAMETER_AGGREGATE_TYPES = "aggregateTypes";
  public static final String QUERY_PARAMETER_WITH_COUNT = "count";
  public static final String QUERY_PARAMETER_WITH_INSTANCE = "withInstance";
  public static final String QUERY_PARAMETER_HIERARCHICAL = "hierarchical";
  public static final String QUERY_PARAMETER_CATEGORY_SEPARATOR = "categorySeparator";
  public static final String QUERY_PARAMETER_SHORTEN_COMMENT = "shortenComment";
  /**
   * 1 -> mapped objects for each row
   * 2 -> aggregate sums for all mapped objects
   */
  public static final String QUERY_PARAMETER_MAPPED_OBJECTS = "mappedObjects";

  /**
   * Transfers are included into in and out sums, instead of reported in extra field
   */
  public static final String QUERY_PARAMETER_INCLUDE_TRANSFERS = "includeTransfers";

  /**
   * Colon separated list of account types
   */
  public static final String QUERY_PARAMETER_ACCOUNTY_TYPE_LIST = "accountTypeList";
  public static final String METHOD_INIT = "init";
  public static final String METHOD_BULK_START = "bulkStart";
  public static final String METHOD_BULK_END = "bulkEnd";
  public static final String METHOD_SORT_ACCOUNTS = "sort_accounts";
  public static final String METHOD_SETUP_CATEGORIES = "setup_categories";
  public static final String METHOD_RESET_EQUIVALENT_AMOUNTS = "reset_equivalent_amounts";
  public static final String METHOD_CHECK_CORRUPTED_DATA_987 = "checkCorruptedData";

  public static final String KEY_RESULT = "result";

  private static final UriMatcher URI_MATCHER;
  //Basic tables
  private static final int TRANSACTIONS = 1;
  private static final int TRANSACTION_ID = 2;
  private static final int CATEGORIES = 3;
  private static final int ACCOUNTS = 4;
  private static final int ACCOUNTS_BASE = 5;
  private static final int ACCOUNT_ID = 6;
  private static final int PAYEES = 7;
  private static final int METHODS = 8;
  private static final int METHOD_ID = 9;
  private static final int ACCOUNTTYPES_METHODS = 10;
  private static final int TEMPLATES = 11;
  private static final int TEMPLATE_ID = 12;
  private static final int CATEGORY_ID = 13;
  private static final int PAYEE_ID = 15;
  private static final int METHODS_FILTERED = 16;
  private static final int TEMPLATES_INCREASE_USAGE = 17;
  private static final int SQLITE_SEQUENCE_TABLE = 19;
  private static final int AGGREGATE_ID = 20;
  private static final int UNCOMMITTED = 21;
  private static final int TRANSACTIONS_GROUPS = 22;
  private static final int TRANSACTIONS_SUMS = 24;
  private static final int TRANSACTION_MOVE = 25;
  private static final int PLANINSTANCE_TRANSACTION_STATUS = 26;
  private static final int CURRENCIES = 27;
  private static final int AGGREGATES_COUNT = 28;
  private static final int TRANSACTION_TOGGLE_CRSTATUS = 29;
  private static final int MAPPED_METHODS = 31;
  private static final int DUAL = 32;
  private static final int CURRENCIES_CHANGE_FRACTION_DIGITS = 33;
  private static final int EVENT_CACHE = 34;
  private static final int DEBUG_SCHEMA = 35;
  private static final int STALE_IMAGES = 36;
  private static final int STALE_IMAGES_ID = 37;
  private static final int TRANSACTION_UNDELETE = 38;
  private static final int TRANSACTIONS_LASTEXCHANGE = 39;
  private static final int ACCOUNTS_SWAP_SORT_KEY = 40;
  private static final int MAPPED_TRANSFER_ACCOUNTS = 41;
  private static final int CHANGES = 42;
  private static final int SETTINGS = 43;
  private static final int TEMPLATES_UNCOMMITTED = 44;
  private static final int ACCOUNT_ID_GROUPING = 45;
  private static final int ACCOUNT_ID_SORTDIRECTION = 46;
  private static final int AUTOFILL = 47;
  private static final int ACCOUNT_EXCHANGE_RATE = 48;
  private static final int UNSPLIT = 49;
  private static final int BUDGETS = 50;
  private static final int BUDGET_ID = 51;
  private static final int BUDGET_CATEGORY = 52;
  private static final int CURRENCIES_CODE = 53;
  private static final int ACCOUNTS_MINIMAL = 54;
  private static final int TAGS = 55;
  private static final int TRANSACTIONS_TAGS = 56;
  private static final int TAG_ID = 57;
  private static final int TEMPLATES_TAGS = 58;
  private static final int UNCOMMITTED_ID = 59;
  private static final int PLANINSTANCE_STATUS_SINGLE = 60;
  private static final int TRANSACTION_LINK_TRANSFER = 61;
  private static final int ACCOUNTS_TAGS = 62;
  private static final int DEBTS = 63;
  private static final int DEBT_ID = 64;
  private static final int BUDGET_ALLOCATIONS = 65;

  private boolean bulkInProgress = false;

  public static String aggregateFunction(boolean safeMode) {
    return safeMode ? "total" : "sum";
  }

  @Override
  public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
                      @Nullable String[] selectionArgs, @Nullable String sortOrder) {
    SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
    SQLiteDatabase db;
    db = getTransactionDatabase().getReadableDatabase();

    Cursor c;

    String groupBy = uri.getQueryParameter(QUERY_PARAMETER_GROUP_BY);
    String having = null;
    String limit = null;

    String aggregateFunction = aggregateFunction(prefHandler.getBoolean(PrefKey.DB_SAFE_MODE, false));

    String accountSelector;
    int uriMatch = URI_MATCHER.match(uri);
    final Context wrappedContext = wrappedContext();
    switch (uriMatch) {
      case TRANSACTIONS: {
        String mappedObjects = uri.getQueryParameter(QUERY_PARAMETER_MAPPED_OBJECTS);
        if (mappedObjects != null) {
          String sql = transactionMappedObjectQuery(selection);
          c = measureAndLogQuery(db, uri, selection, sql, selectionArgs);
          return c;
        }
        boolean extended = uri.getQueryParameter(QUERY_PARAMETER_EXTENDED) != null;
        qb.setTables(extended ? VIEW_EXTENDED : VIEW_COMMITTED);
        if (uri.getQueryParameter(QUERY_PARAMETER_DISTINCT) != null) {
          qb.setDistinct(true);
        }
        if (sortOrder == null) {
          sortOrder = KEY_DATE + " DESC";
        }
        if (projection == null) {
          projection = extended ? Transaction.PROJECTION_EXTENDED : Transaction.PROJECTION_BASE;
        }
        if (uri.getQueryParameter(QUERY_PARAMETER_SHORTEN_COMMENT) != null) {
          projection = Companion.shortenComment(projection);
        }
        if (uri.getQueryParameter(QUERY_PARAMETER_MERGE_TRANSFERS) != null) {
          String mergeTransferSelection = KEY_TRANSFER_PEER + " IS NULL OR " + IS_SAME_CURRENCY +
                  " != 1 OR " + KEY_AMOUNT + " < 0";
          selection = selection == null ? mergeTransferSelection :
                  selection + " AND (" + mergeTransferSelection + ")";
        }
        break;
      }
      case UNCOMMITTED:
        qb.setTables(VIEW_UNCOMMITTED);
        if (projection == null)
          projection = Transaction.PROJECTION_BASE;
        break;
      case TRANSACTION_ID:
        qb.setTables(VIEW_ALL);
        qb.appendWhere(KEY_ROWID + "=" + uri.getPathSegments().get(1));
        break;
      case TRANSACTIONS_SUMS: {
        String accountSelectionQuery = null;
        boolean groupByType = uri.getQueryParameter(QUERY_PARAMETER_GROUPED_BY_TYPE) != null;
        boolean aggregateTypes = uri.getQueryParameter(QUERY_PARAMETER_AGGREGATE_TYPES) != null;
        accountSelector = uri.getQueryParameter(KEY_ACCOUNTID);
        if (accountSelector == null) {
          accountSelector = uri.getQueryParameter(KEY_CURRENCY);
          if (accountSelector != null) {
            accountSelectionQuery = " IN " +
                "(SELECT " + KEY_ROWID + " FROM " + TABLE_ACCOUNTS + " WHERE " + KEY_CURRENCY + " = ? AND " +
                KEY_EXCLUDE_FROM_TOTALS + "=0)";
          }
        } else {
          accountSelectionQuery = " = ?";
        }
        qb.appendWhere(WHERE_TRANSACTION);

        if (groupByType) {
          groupBy = KEY_TYPE;
        } else if (!aggregateTypes) {
          //expenses only
          qb.appendWhere(" AND " + KEY_AMOUNT + " < 0");
        }
        String amountCalculation;
        qb.setTables(VIEW_WITH_ACCOUNT);
        if (accountSelector != null) {
          selectionArgs = Utils.joinArrays(new String[]{accountSelector}, selectionArgs);
          qb.appendWhere(" AND " + KEY_ACCOUNTID + accountSelectionQuery);
          amountCalculation = KEY_AMOUNT;
        } else {
          amountCalculation = DatabaseConstants.getAmountHomeEquivalent(VIEW_WITH_ACCOUNT);
        }
        String sumExpression = aggregateFunction + "(" + amountCalculation + ")";
        if (groupByType) sumExpression = "abs(" + sumExpression + ")";
        final String sumColumn = sumExpression + " as  " + KEY_SUM;
        projection = groupByType ? new String[]{KEY_AMOUNT + " > 0 as " + KEY_TYPE, sumColumn} : new String[]{sumColumn};
        break;
      }
      case TRANSACTIONS_GROUPS: {
        String accountSelectionQuery = "";
        accountSelector = uri.getQueryParameter(KEY_ACCOUNTID);
        if (accountSelector == null) {
          accountSelector = uri.getQueryParameter(KEY_CURRENCY);
          if (accountSelector != null) {
            accountSelectionQuery = KEY_CURRENCY + " = ? AND ";
          }
          accountSelectionQuery += KEY_EXCLUDE_FROM_TOTALS + " = 0";
        } else {
          accountSelectionQuery = KEY_ACCOUNTID + " = ?";
        }
        boolean forHome = accountSelector == null;

        Grouping group;
        try {
          group = Grouping.valueOf(uri.getPathSegments().get(2));
        } catch (IllegalArgumentException e) {
          group = Grouping.NONE;
        }

        // the start value is only needed for WEEK and DAY
        boolean withStart = uri.getQueryParameter(QUERY_PARAMETER_WITH_START) != null && (group == Grouping.WEEK || group == Grouping.DAY);
        boolean includeTransfers = uri.getQueryParameter(QUERY_PARAMETER_INCLUDE_TRANSFERS) != null;
        String yearExpression;
        switch (group) {
          case WEEK:
            yearExpression = getYearOfWeekStart();
            break;
          case MONTH:
            yearExpression = getYearOfMonthStart();
            break;
          default:
            yearExpression = YEAR;
        }
        groupBy = KEY_YEAR + "," + KEY_SECOND_GROUP;
        String secondDef = "";

        final boolean sectionsOnly = uri.getQueryParameter(QUERY_PARAMETER_SECTIONS) != null;
        switch (group) {
          case NONE:
            yearExpression = "1";
            secondDef = "1";
            break;
          case DAY:
            secondDef = DAY;
            break;
          case WEEK:
            secondDef = getWeek();
            break;
          case MONTH:
            secondDef = sectionsOnly ? MONTH_PLAIN : getMonth();
            break;
          case YEAR:
            secondDef = "0";
            groupBy = KEY_YEAR;
            break;
        }
        qb.setTables(VIEW_WITH_ACCOUNT);
        int projectionSize;
        if (sectionsOnly) {
          projectionSize = 2;
        } else {
          projectionSize = 5;
          if (withStart) {
            projectionSize += 1;
          }
          if (!includeTransfers) {
            projectionSize += 1;
          }
        }
        projection = new String[projectionSize];
        int index = 0;
        projection[index++] = yearExpression + " AS " + KEY_YEAR;
        projection[index++] = secondDef + " AS " + KEY_SECOND_GROUP;
        if (!sectionsOnly) {
          projection[index++] = includeTransfers ? getInAggregate(forHome, aggregateFunction) : getIncomeAggregate(forHome, aggregateFunction);
          projection[index++] = includeTransfers ? getOutAggregate(forHome, aggregateFunction) : getExpenseAggregate(forHome, aggregateFunction);
          if (!includeTransfers) {
            //for the Grand total account transfer calculation is neither possible (adding amounts in
            //different currencies) nor necessary (should result in 0)
            projection[index++] = (forHome ? "0" : getTransferSum(aggregateFunction)) + " AS " + KEY_SUM_TRANSFERS;
          }
          projection[index++] = MAPPED_CATEGORIES;
          if (withStart) {
            projection[index] = (group == Grouping.WEEK ? getWeekStartJulian() : DAY_START_JULIAN)
                + " AS " + KEY_GROUP_START;
          }
        }
        selection = accountSelectionQuery
            + (selection != null ? " AND " + selection : "");
        if (accountSelector != null) {
          selectionArgs = Utils.joinArrays(
              new String[]{accountSelector},
              selectionArgs);
        }
        break;
      }
      case CATEGORIES: {
        String mappedObjects = uri.getQueryParameter(QUERY_PARAMETER_MAPPED_OBJECTS);
        if (mappedObjects != null) {
          String sql = categoryTreeWithMappedObjects(selection, projection, mappedObjects.equals("2"));
          c = measureAndLogQuery(db, uri, selection, sql, selectionArgs);
          return c;
        }
        if (uri.getQueryParameter(QUERY_PARAMETER_HIERARCHICAL) != null) {
          final boolean withBudget = projection != null && Arrays.asList(projection).contains(KEY_BUDGET);
          String sql = withBudget ? categoryTreeWithBudget(sortOrder, selection, projection, uri.getQueryParameter(KEY_YEAR), uri.getQueryParameter(KEY_SECOND_GROUP)) :
                  categoryTreeSelect(sortOrder, selection, projection, null, null,
                  uri.getQueryParameter(QUERY_PARAMETER_CATEGORY_SEPARATOR));
          c = measureAndLogQuery(db, uri, selection, sql, selectionArgs);
          c.setNotificationUri(getContext().getContentResolver(), uri);
          return c;
        } else {
          qb.setTables(TABLE_CATEGORIES);
          qb.appendWhere(KEY_ROWID + " != " + SPLIT_CATID);
          if (projection == null) {
            projection = new String[]{KEY_ROWID, KEY_LABEL, KEY_PARENTID};
          }
          break;
        }
      }
      case CATEGORY_ID:
        qb.setTables(TABLE_CATEGORIES);
        qb.appendWhere(KEY_ROWID + "=" + uri.getPathSegments().get(1));
        break;
      case ACCOUNTS:
      case ACCOUNTS_BASE:
      case ACCOUNTS_MINIMAL:
        final boolean minimal = uriMatch == ACCOUNTS_MINIMAL;
        final boolean withSums = Objects.equals(uri.getQueryParameter(QUERY_PARAMETER_FULL_PROJECTION_WITH_SUMS), "1");
        final String mergeAggregate = minimal ? "1" : uri.getQueryParameter(QUERY_PARAMETER_MERGE_CURRENCY_AGGREGATES);
        if (sortOrder == null) {
          sortOrder = minimal ? KEY_LABEL : Sort.Companion.preferredOrderByForAccounts(PrefKey.SORT_ORDER_ACCOUNTS, prefHandler, Sort.LABEL);
        }
        if (mergeAggregate != null || withSums) {
          if (projection != null) {
            CrashHandler.throwOrReport(
                    "When calling accounts cursor with sums or aggregates, projection is ignored ", TAG
            );
          }
          String sql = buildAccountQuery(qb, minimal, mergeAggregate, selection, sortOrder);
          c = measureAndLogQuery(db, uri, selection, sql, selectionArgs);
          c.setNotificationUri(getContext().getContentResolver(), uri);
          return c;
        } else {
          qb.setTables(getAccountsWithExchangeRate());
          if (projection == null)
            projection = Account.PROJECTION_BASE;
          break;
        }

      case AGGREGATE_ID:
        String currencyId = uri.getPathSegments().get(2);
        if (Integer.parseInt(currencyId) == Account.HOME_AGGREGATE_ID) {
          String grouping = prefHandler.getString(GROUPING_AGGREGATE, "NONE");
          qb.setTables(TABLE_ACCOUNTS);
          projection = new String[]{
              Account.HOME_AGGREGATE_ID + " AS " + KEY_ROWID,
              "'' AS " + KEY_LABEL,
              "'' AS " + KEY_DESCRIPTION,
              aggregateFunction + "(" + KEY_OPENING_BALANCE + " * " + DatabaseConstants.getExchangeRate(TABLE_ACCOUNTS, KEY_ROWID)
                  + ") AS " + KEY_OPENING_BALANCE,
              "'" + AGGREGATE_HOME_CURRENCY_CODE + "' AS " + KEY_CURRENCY,
              "-1 AS " + KEY_COLOR,
              "'" + grouping + "' AS " + KEY_GROUPING,
              "'DESC' AS " + KEY_SORT_DIRECTION,
              "'AGGREGATE' AS " + KEY_TYPE,
              "-1 AS " + KEY_SORT_KEY,
              "0 AS " + KEY_EXCLUDE_FROM_TOTALS,
              "null AS " + KEY_SYNC_ACCOUNT_NAME,
              "null AS " + KEY_UUID,
              "0 AS " + KEY_CRITERION,
              "max(" + KEY_SEALED + ") AS " + KEY_SEALED};
        } else {
          qb.setTables(TABLE_CURRENCIES);
          String accountSelect = "from " + TABLE_ACCOUNTS + " where " + KEY_CURRENCY + " = " + KEY_CODE + " AND " + KEY_EXCLUDE_FROM_TOTALS + " = 0";
          projection = new String[]{
              "0 - " + TABLE_CURRENCIES + "." + KEY_ROWID + "  AS " + KEY_ROWID,//we use negative ids for aggregate accounts
              KEY_CODE + " AS " + KEY_LABEL,
              "'' AS " + KEY_DESCRIPTION,
              "(select " + aggregateFunction + "(" + KEY_OPENING_BALANCE
                  + ") " + accountSelect + ") AS " + KEY_OPENING_BALANCE,
              KEY_CODE + " AS " + KEY_CURRENCY,
              "-1 AS " + KEY_COLOR,
              TABLE_CURRENCIES + "." + KEY_GROUPING,
              "'DESC' AS " + KEY_SORT_DIRECTION,
              "'AGGREGATE' AS " + KEY_TYPE,
              "-1 AS " + KEY_SORT_KEY,
              "0 AS " + KEY_EXCLUDE_FROM_TOTALS,
              "null AS " + KEY_SYNC_ACCOUNT_NAME,
              "null AS " + KEY_UUID,
              "0 AS " + KEY_CRITERION,
              "(select max(" + KEY_SEALED + ") from " + TABLE_ACCOUNTS + " where " + KEY_CURRENCY + " = " + KEY_CODE + ") AS " + KEY_SEALED};
          qb.appendWhere(TABLE_CURRENCIES + "." + KEY_ROWID + "= abs(" + currencyId + ")");
        }
        break;
      case ACCOUNT_ID:
        qb.setTables(getAccountsWithExchangeRate());
        qb.appendWhere(KEY_ROWID + "=" + uri.getPathSegments().get(1));
        break;
      case AGGREGATES_COUNT:
        qb.setTables(TABLE_ACCOUNTS);
        groupBy = "currency";
        having = "count(*) > 1";
        projection = new String[]{"count(*)"};
        break;
      case PAYEES:
        qb.setTables(TABLE_PAYEES);
        if (sortOrder == null) {
          sortOrder = KEY_PAYEE_NAME;
        }
        if (projection == null)
          projection = Companion.getPAYEE_PROJECTION();
        break;
      case MAPPED_TRANSFER_ACCOUNTS:
        qb.setTables(TABLE_ACCOUNTS + " JOIN " + TABLE_TRANSACTIONS + " ON (" + KEY_TRANSFER_ACCOUNT + " = " + TABLE_ACCOUNTS + "." + KEY_ROWID + ")");
        projection = new String[]{"DISTINCT " + TABLE_ACCOUNTS + "." + KEY_ROWID, KEY_LABEL};
        if (sortOrder == null) {
          sortOrder = KEY_LABEL;
        }
        break;
      case METHODS:
        qb.setTables(tableForPaymentMethodQuery(projection));
        groupBy = groupByForPaymentMethodQuery(projection);
        having = havingForPaymentMethodQuery(projection);
        if (projection == null) {
          projection = PaymentMethod.PROJECTION(wrappedContext);
        } else {
          projection = mapPaymentMethodProjection(projection, wrappedContext);
        }
        if (sortOrder == null) {
          sortOrder = PaymentMethod.localizedLabelSqlColumn(wrappedContext, KEY_LABEL) + " COLLATE LOCALIZED";
        }
        break;
      case MAPPED_METHODS:
        String localizedLabel = PaymentMethod.localizedLabelSqlColumn(wrappedContext, KEY_LABEL);
        qb.setTables(TABLE_METHODS + " JOIN " + TABLE_TRANSACTIONS + " ON (" + KEY_METHODID + " = " + TABLE_METHODS + "." + KEY_ROWID + ")");
        projection = new String[]{"DISTINCT " + TABLE_METHODS + "." + KEY_ROWID, localizedLabel + " AS " + KEY_LABEL};
        if (sortOrder == null) {
          sortOrder = localizedLabel + " COLLATE LOCALIZED";
        }
        break;
      case METHOD_ID:
        qb.setTables(TABLE_METHODS);
        if (projection == null)
          projection = PaymentMethod.PROJECTION(wrappedContext);
        qb.appendWhere(KEY_ROWID + "=" + uri.getPathSegments().get(1));
        break;
      case METHODS_FILTERED:
        localizedLabel = PaymentMethod.localizedLabelSqlColumn(wrappedContext, KEY_LABEL);
        qb.setTables(TABLE_METHODS + " JOIN " + TABLE_ACCOUNTTYES_METHODS + " ON (" + KEY_ROWID + " = " + KEY_METHODID + ")");
        projection = new String[]{KEY_ROWID, localizedLabel + " AS " + KEY_LABEL, KEY_IS_NUMBERED};
        String paymentType = uri.getPathSegments().get(2);
        String typeSelect;
        switch (paymentType) {
          case "1": {
            typeSelect = "> -1";
            break;
          }
          case "-1": {
            typeSelect = "< 1";
            break;
          }
          default:
            typeSelect = "= 0";
        }
        selection = String.format("%s.%s %s", TABLE_METHODS, KEY_TYPE, typeSelect);
        String[] accountTypes = uri.getQueryParameter(QUERY_PARAMETER_ACCOUNTY_TYPE_LIST).split(";");

        selection += " and " + TABLE_ACCOUNTTYES_METHODS + ".type " + WhereFilter.Operation.IN.getOp(accountTypes.length);
        selectionArgs = accountTypes;
        if (sortOrder == null) {
          sortOrder = localizedLabel + " COLLATE LOCALIZED";
        }
        groupBy = KEY_ROWID;
        having = "count(*) = " + accountTypes.length;
        break;
      case ACCOUNTTYPES_METHODS:
        qb.setTables(TABLE_ACCOUNTTYES_METHODS);
        break;
      case TEMPLATES:
        String instanceId = uri.getQueryParameter(QUERY_PARAMETER_WITH_INSTANCE);
        if (instanceId == null) {
          qb.setTables(VIEW_TEMPLATES_EXTENDED);
          if (projection == null) {
            projection = extendProjectionWithSealedCheck(Template.PROJECTION_EXTENDED, VIEW_TEMPLATES_EXTENDED);
          }
        } else {
          qb.setTables(String.format(Locale.ROOT, "%1$s LEFT JOIN %2$s ON %1$s.%3$s = %4$s AND %5$s = %6$s LEFT JOIN %7$s ON %7$s.%3$s = %2$s.%8$s",
              VIEW_TEMPLATES_EXTENDED, TABLE_PLAN_INSTANCE_STATUS, KEY_ROWID, KEY_TEMPLATEID, KEY_INSTANCEID, instanceId,
              TABLE_TRANSACTIONS, KEY_TRANSACTIONID));
          if (projection != null) {
            report("When calling templates cursor with QUERY_PARAMETER_WITH_INSTANCE, projection is ignored ");
          }
          projection = new String[]{KEY_TITLE, KEY_INSTANCEID, KEY_TRANSACTIONID, KEY_COLOR, KEY_CURRENCY,
              String.format(Locale.ROOT, "coalesce(%1$s.%2$s, %3$s.%2$s) AS %2$s", TABLE_TRANSACTIONS, KEY_AMOUNT, VIEW_TEMPLATES_EXTENDED),
              VIEW_TEMPLATES_EXTENDED + "." + KEY_ROWID + " AS " + KEY_ROWID, KEY_SEALED};
        }

        break;
      case TEMPLATES_UNCOMMITTED:
        qb.setTables(VIEW_TEMPLATES_UNCOMMITTED);
        if (projection == null)
          projection = Template.PROJECTION_BASE;
        break;
      case TEMPLATE_ID:
        qb.setTables(VIEW_TEMPLATES_ALL);
        qb.appendWhere(KEY_ROWID + "=" + uri.getPathSegments().get(1));
        if (projection == null) {
          projection = extendProjectionWithSealedCheck(Template.PROJECTION_EXTENDED, VIEW_TEMPLATES_ALL);
        }
        break;
      case SQLITE_SEQUENCE_TABLE:
        qb.setTables("SQLITE_SEQUENCE");
        projection = new String[]{"seq"};
        selection = "name = ?";
        selectionArgs = new String[]{uri.getPathSegments().get(1)};
        break;
      case PLANINSTANCE_TRANSACTION_STATUS:
        qb.setTables(TABLE_PLAN_INSTANCE_STATUS);
        break;
      case PLANINSTANCE_STATUS_SINGLE:
        qb.setTables(String.format(Locale.ROOT, "%1$s LEFT JOIN %2$s ON %3$s = %4$s", TABLE_PLAN_INSTANCE_STATUS, TABLE_TRANSACTIONS, KEY_ROWID, KEY_TRANSACTIONID));
        qb.appendWhere(String.format(Locale.ROOT, "%s = %s AND %s = %s", KEY_TEMPLATEID,
            uri.getPathSegments().get(1), KEY_INSTANCEID, uri.getPathSegments().get(2)));
        projection = new String[]{KEY_TRANSACTIONID, KEY_AMOUNT};
        break;
      //only called from unit test
      case CURRENCIES:
        if (projection == null) {
          projection = new String[] {
              KEY_ROWID, KEY_CODE, KEY_GROUPING, KEY_LABEL, KEY_USAGES
          };
          qb.setTables(CURRENCIES_USAGES_TABLE_EXPRESSION);
        } else {
          qb.setTables(TABLE_CURRENCIES);
        }
        break;
      case DUAL:
        qb.setTables("sqlite_master");
        return qb.query(db, projection, selection, selectionArgs, null,
            null, null, "1");
      case EVENT_CACHE:
        qb.setTables(TABLE_EVENT_CACHE);
        break;
      case DEBUG_SCHEMA:
        qb.setTables("sqlite_master");
        return qb.query(
            db,
            new String[]{"name", "sql"},
            "type = 'table'",
            null, null, null, null);
      case STALE_IMAGES:
        qb.setTables(TABLE_STALE_URIS);
        if (projection == null)
          projection = new String[]{"rowid as _id", KEY_PICTURE_URI};
        break;
      case STALE_IMAGES_ID:
        qb.setTables(TABLE_STALE_URIS);
        qb.appendWhere("rowid = " + uri.getPathSegments().get(1));
        projection = new String[]{KEY_PICTURE_URI};
        break;
      case TRANSACTIONS_LASTEXCHANGE:
        String currency1 = uri.getPathSegments().get(2);
        String currency2 = uri.getPathSegments().get(3);
        selection = "(SELECT " + KEY_CURRENCY + " FROM " + TABLE_ACCOUNTS +
            " WHERE " + KEY_ROWID + " = " + KEY_ACCOUNTID + ") = ? AND " +
            "(SELECT " + KEY_CURRENCY + " FROM " + TABLE_ACCOUNTS + " WHERE " + KEY_ROWID + " = " +
            "(SELECT " + KEY_ACCOUNTID + " FROM " + TABLE_TRANSACTIONS + " WHERE " + KEY_ROWID +
            " = " + VIEW_COMMITTED + "." + KEY_TRANSFER_PEER + ")) = ?";
        selectionArgs = new String[]{currency1, currency2};
        projection = new String[]{
            "'" + currency1 + "'", // we pass the currency codes back so that the receiver
            "'" + currency2 + "'", // can check if the data is still relevant for him
            "abs(" + KEY_AMOUNT + ")",
            "abs((SELECT " + KEY_AMOUNT + " FROM " + TABLE_TRANSACTIONS + " WHERE " + KEY_ROWID +
                " = " + VIEW_COMMITTED + "." + KEY_TRANSFER_PEER + "))"
        };
        sortOrder = KEY_DATE + " DESC";
        limit = "1";
        qb.setTables(VIEW_COMMITTED);
        break;
      case CHANGES:
        selection = KEY_ACCOUNTID + " = ? AND " + KEY_SYNC_SEQUENCE_LOCAL + " = ?";
        selectionArgs = new String[]{uri.getQueryParameter(KEY_ACCOUNTID), uri.getQueryParameter(KEY_SYNC_SEQUENCE_LOCAL)};
        qb.setTables(VIEW_CHANGES_EXTENDED);
        if (projection == null) {
          projection = TransactionChange.PROJECTION;
        }
        break;
      case SETTINGS: {
        qb.setTables(TABLE_SETTINGS);
        break;
      }
      case AUTOFILL:
        qb.setTables(VIEW_EXTENDED);
        selection = KEY_ROWID + "= (SELECT max(" + KEY_ROWID + ") FROM " + TABLE_TRANSACTIONS
            + " WHERE " + WHERE_NOT_SPLIT + " AND " + KEY_PAYEEID + " = ?)";
        selectionArgs = new String[]{uri.getPathSegments().get(1)};
        break;
      case ACCOUNT_EXCHANGE_RATE:
        qb.setTables(TABLE_ACCOUNT_EXCHANGE_RATES);
        qb.appendWhere(KEY_ACCOUNTID + "=" + uri.getPathSegments().get(1));
        qb.appendWhere(" AND " + KEY_CURRENCY_SELF + "='" + uri.getPathSegments().get(2) + "'");
        qb.appendWhere(" AND " + KEY_CURRENCY_OTHER + "='" + uri.getPathSegments().get(3) + "'");
        projection = new String[]{KEY_EXCHANGE_RATE};
        break;
      case BUDGETS:
        qb.setTables(getBudgetTableJoin());
        break;
      case BUDGET_CATEGORY: {
        if (projection == null) {
          String sql = budgetAllocation(uri);
          c = measureAndLogQuery(db, uri, null, sql, null);
          return c;
        } else {
          qb.setTables(TABLE_BUDGET_ALLOCATIONS);
          qb.appendWhere(budgetSelect(uri));
          break;
        }
      }
      case TAGS:
        boolean withCount = uri.getQueryParameter(QUERY_PARAMETER_WITH_COUNT) != null;
        qb.setTables(withCount ? TABLE_TAGS + " LEFT JOIN " + TABLE_TRANSACTIONS_TAGS + " ON (" + KEY_ROWID + " = " + KEY_TAGID + ")" : TABLE_TAGS);
        if (withCount) {
          projection = new String[]{KEY_ROWID, KEY_LABEL, String.format("count(%s) AS %s", KEY_TAGID, KEY_COUNT)};
          groupBy = KEY_ROWID;
        }
        break;
      case TRANSACTIONS_TAGS:
        qb.setTables(TABLE_TRANSACTIONS_TAGS + " LEFT JOIN " + TABLE_TAGS + " ON (" + KEY_TAGID + " = " + KEY_ROWID + ")");
        break;
      case TEMPLATES_TAGS:
        qb.setTables(TABLE_TEMPLATES_TAGS + " LEFT JOIN " + TABLE_TAGS + " ON (" + KEY_TAGID + " = " + KEY_ROWID + ")");
        break;
      case ACCOUNTS_TAGS:
        qb.setTables(TABLE_ACCOUNTS_TAGS + " LEFT JOIN " + TABLE_TAGS + " ON (" + KEY_TAGID + " = " + KEY_ROWID + ")");
        break;
      case DEBTS: {
        String transactionId = uri.getQueryParameter(KEY_TRANSACTIONID);
        if (transactionId != null) {
          qb.appendWhere("not exists(SELECT 1 FROM " +
              TABLE_TRANSACTIONS +
              " WHERE " +
              KEY_DEBT_ID +
              " IS NOT NULL AND " +
              KEY_PARENTID +
              " = " +
              transactionId +
              ")");
        }
        if (projection == null) {
          projection = Companion.debtProjection(transactionId);
        }
        qb.setTables(DEBT_PAYEE_JOIN);
        break;
      }
      case DEBT_ID: {
        if (projection == null) {
          projection = Companion.debtProjection(null);
        }
        qb.setTables(DEBT_PAYEE_JOIN);
        qb.appendWhere(TABLE_DEBTS + "." + KEY_ROWID + "=" + uri.getPathSegments().get(1));
        break;
      }
      default:
        throw unknownUri(uri);
    }

    c = measureAndLogQuery(qb, uri, db, projection, selection, selectionArgs, groupBy, having, sortOrder, limit);

    final String withPlanInfo = uri.getQueryParameter(QUERY_PARAMETER_WITH_PLAN_INFO);
    if (uriMatch == TEMPLATES && withPlanInfo != null) {
      c = new PlanInfoCursorWrapper(getContext(), c, sortOrder == null, withPlanInfo.equals("2") || CALENDAR.hasPermission(getContext()));
    }
    c.setNotificationUri(getContext().getContentResolver(), uri);
    return c;
  }

  private Context wrappedContext() {
    return userLocaleProvider.wrapContext(getContext());
  }

  @Override
  public String getType(@NonNull Uri uri) {
    return null;
  }

  private IllegalArgumentException unknownUri(@NonNull Uri uri) {
    return new IllegalArgumentException("Unknown URL " + uri);
  }

  @Override
  public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
    setDirty(true);
    log("INSERT Uri: %s, values: %s", uri, values);
    SQLiteDatabase db = getTransactionDatabase().getWritableDatabase();
    long id;
    String newUri;
    int uriMatch = URI_MATCHER.match(uri);
    switch (uriMatch) {
      case TRANSACTIONS:
      case UNCOMMITTED:
        id = db.insertOrThrow(TABLE_TRANSACTIONS, null, values);
        newUri = TRANSACTIONS_URI + "/" + id;
        break;
      case ACCOUNTS:
        id = db.insertOrThrow(TABLE_ACCOUNTS, null, values);
        newUri = ACCOUNTS_URI + "/" + id;
        break;
      case METHODS:
        id = db.insertOrThrow(TABLE_METHODS, null, values);
        newUri = METHODS_URI + "/" + id;
        break;
      case ACCOUNTTYPES_METHODS:
        id = db.insertOrThrow(TABLE_ACCOUNTTYES_METHODS, null, values);
        //we are not interested in accessing individual entries in this table, but have to return a uri
        newUri = ACCOUNTTYPES_METHODS_URI + "/" + id;
        break;
      case TEMPLATES:
        id = db.insertOrThrow(TABLE_TEMPLATES, null, values);
        newUri = TEMPLATES_URI + "/" + id;
        break;
      case CATEGORIES:
        Long parentId = values.getAsLong(KEY_PARENTID);
        if (parentId == null && !values.containsKey(KEY_COLOR)) {
          values.put(KEY_COLOR, suggestNewCategoryColor(db));
        }
        id = db.insertOrThrow(TABLE_CATEGORIES, null, values);
        newUri = CATEGORIES_URI + "/" + id;
        break;
      case PAYEES:
        id = db.insertOrThrow(TABLE_PAYEES, null, values);
        newUri = PAYEES_URI + "/" + id;
        break;
      case PLANINSTANCE_TRANSACTION_STATUS: {
        long templateId = values.getAsLong(KEY_TEMPLATEID);
        long instancId = values.getAsLong(KEY_INSTANCEID);
        db.insertWithOnConflict(TABLE_PLAN_INSTANCE_STATUS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        Uri changeUri = Uri.parse(PLAN_INSTANCE_STATUS_URI + "/" + templateId + "/" + instancId);
        notifyChange(changeUri, false);
        return changeUri;
      }
      case EVENT_CACHE:
        id = db.insertOrThrow(TABLE_EVENT_CACHE, null, values);
        newUri = EVENT_CACHE_URI + "/" + id;
        break;
      case STALE_IMAGES:
        id = db.insertOrThrow(TABLE_STALE_URIS, null, values);
        newUri = STALE_IMAGES_URI + "/" + id;
        break;
      case ACCOUNT_EXCHANGE_RATE:
        values.put(KEY_ACCOUNTID, uri.getPathSegments().get(1));
        values.put(KEY_CURRENCY_SELF, uri.getPathSegments().get(2));
        values.put(KEY_CURRENCY_OTHER, uri.getPathSegments().get(3));
        id = db.insertWithOnConflict(TABLE_ACCOUNT_EXCHANGE_RATES, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        newUri = uri.toString();
        break;
      case DUAL: {
        if ("1".equals(uri.getQueryParameter(QUERY_PARAMETER_SYNC_BEGIN))) {
          id = pauseChangeTrigger(db);
          newUri = TABLE_SYNC_STATE + "/" + id;
        } else {
          throw unknownUri(uri);
        }
        break;
      }
      case SETTINGS: {
        id = db.replace(TABLE_SETTINGS, null, values);
        newUri = SETTINGS_URI + "/" + id;
        break;
      }
      case BUDGETS: {
        long budget = values.getAsLong(KEY_BUDGET);
        values.remove(KEY_BUDGET);
        id = db.insertOrThrow(TABLE_BUDGETS, null, values);
        ContentValues budgetInitialAmount = new ContentValues(2);
        budgetInitialAmount.put(KEY_BUDGETID, id);
        budgetInitialAmount.put(KEY_BUDGET, budget);
        budgetInitialAmount.put(KEY_CATID, 0);
        db.insertOrThrow(TABLE_BUDGET_ALLOCATIONS, null, budgetInitialAmount);
        newUri = BUDGETS_URI + "/" + id;
        break;
      }
      case CURRENCIES: {
        try {
          id = db.insertOrThrow(TABLE_CURRENCIES, null, values);
        } catch (SQLiteConstraintException e) {
          return null;
        }
        newUri = CURRENCIES_URI + "/" + id;
        break;
      }
      case TAGS: {
        id = db.insertOrThrow(TABLE_TAGS, null, values);
        newUri = TAGS_URI + "/" + id;
        break;
      }
      case TRANSACTIONS_TAGS: {
        db.insertWithOnConflict(TABLE_TRANSACTIONS_TAGS, null, values, SQLiteDatabase.CONFLICT_IGNORE);
        //the table does not have primary ids, we return the base uri
        notifyChange(uri, callerIsNotSyncAdatper(uri));
        return TRANSACTIONS_TAGS_URI;
      }
      case TEMPLATES_TAGS: {
        db.insertWithOnConflict(TABLE_TEMPLATES_TAGS, null, values, SQLiteDatabase.CONFLICT_IGNORE);
        //the table does not have primary ids, we return the base uri
        notifyChange(uri, false);
        return TEMPLATES_TAGS_URI;
      }
      case ACCOUNTS_TAGS: {
        db.insertWithOnConflict(TABLE_ACCOUNTS_TAGS, null, values, SQLiteDatabase.CONFLICT_IGNORE);
        //the table does not have primary ids, we return the base uri
        notifyChange(uri, false);
        return ACCOUNTS_TAGS_URI;
      }
      case DEBTS: {
        id = db.insertOrThrow(TABLE_DEBTS, null, values);
        newUri = DEBTS_URI + "/" + id;
        break;
      }
      default:
        throw unknownUri(uri);
    }
    notifyChange(uri, uriMatch == TRANSACTIONS && callerIsNotSyncAdatper(uri));
    //the accounts cursor contains aggregates about transactions
    //we need to notify it when transactions change
    if (uriMatch == TRANSACTIONS) {
      notifyChange(ACCOUNTS_URI, false);
      notifyChange(DEBTS_URI, false);
      notifyChange(UNCOMMITTED_URI, false);
    } else if (uriMatch == ACCOUNTS) {
      notifyChange(ACCOUNTS_BASE_URI, false);
    } else if (uriMatch == TEMPLATES) {
      notifyChange(TEMPLATES_UNCOMMITTED_URI, false);
    } else if (uriMatch == DEBTS) {
      notifyChange(PAYEES_URI, false);
    } else if (uriMatch == UNCOMMITTED) {
      notifyChange(DEBTS_URI, false);
    }
    return id > 0 ? Uri.parse(newUri) : null;
  }

  @Override
  public int delete(@NonNull Uri uri, String where, String[] whereArgs) {
    setDirty(true);
    log("Delete for URL: %s", uri);
    SQLiteDatabase db = getTransactionDatabase().getWritableDatabase();
    int count;
    String segment;
    int uriMatch = URI_MATCHER.match(uri);
    switch (uriMatch) {
      case TRANSACTIONS:
      case UNCOMMITTED:
        count = db.delete(TABLE_TRANSACTIONS, where, whereArgs);
        break;
      case TRANSACTION_ID:
        //maybe TODO ?: where and whereArgs are ignored
        segment = uri.getPathSegments().get(1);
        //when we are deleting a transfer whose peer is part of a split, we cannot delete the peer,
        //because the split would be left in an invalid state, hence we transform the peer to a normal split part
        //first we find out the account label
        db.beginTransaction();
        try {
          ContentValues args = new ContentValues();
          args.putNull(KEY_TRANSFER_ACCOUNT);
          args.putNull(KEY_TRANSFER_PEER);
          db.update(TABLE_TRANSACTIONS,
              args,
              KEY_TRANSFER_PEER + " = ? AND " + KEY_PARENTID + " IS NOT null",
              new String[]{segment});
          //we delete the transaction, its children and its transfer peer, and transfer peers of its children
          if (uri.getQueryParameter(QUERY_PARAMETER_MARK_VOID) == null) {
            //we delete the parent separately, so that the changes trigger can correctly record the parent uuid
            count = db.delete(TABLE_TRANSACTIONS, WHERE_DEPENDENT, new String[]{segment, segment});
            count += db.delete(TABLE_TRANSACTIONS, WHERE_SELF_OR_PEER, new String[]{segment, segment});
          } else {
            ContentValues v = new ContentValues();
            v.put(KEY_CR_STATUS, CrStatus.VOID.name());
            count = db.update(TABLE_TRANSACTIONS, v, WHERE_SELF_OR_DEPENDENT, new String[]{segment, segment, segment});
          }
          db.setTransactionSuccessful();
        } finally {
          db.endTransaction();
        }
        break;
      case TEMPLATES:
        count = db.delete(TABLE_TEMPLATES, where, whereArgs);
        break;
      case TEMPLATE_ID:
        count = db.delete(TABLE_TEMPLATES,
            KEY_ROWID + " = " + uri.getLastPathSegment() + prefixAnd(where), whereArgs);
        break;
      case ACCOUNTTYPES_METHODS:
        count = db.delete(TABLE_ACCOUNTTYES_METHODS, where, whereArgs);
        break;
      case ACCOUNTS:
        count = db.delete(TABLE_ACCOUNTS, where, whereArgs);
        break;
      case ACCOUNT_ID:
        count = db.delete(TABLE_ACCOUNTS,
            KEY_ROWID + " = " + uri.getLastPathSegment() + prefixAnd(where), whereArgs);
        //update aggregate cursor
        //getContext().getContentResolver().notifyChange(AGGREGATES_URI, null);
        break;
      case CATEGORIES:
        count = db.delete(TABLE_CATEGORIES, KEY_ROWID + " != " + SPLIT_CATID + prefixAnd(where),
            whereArgs);
        break;
      case CATEGORY_ID:
        String lastPathSegment = uri.getLastPathSegment();
        if (Long.parseLong(lastPathSegment) == SPLIT_CATID) throw new IllegalArgumentException("split category can not be deleted");
        count = db.delete(TABLE_CATEGORIES,
            KEY_ROWID + " = " + lastPathSegment + prefixAnd(where), whereArgs);
        break;
      case PAYEE_ID:
        count = db.delete(TABLE_PAYEES,
            KEY_ROWID + " = " + uri.getLastPathSegment() + prefixAnd(where), whereArgs);
        break;
      case METHOD_ID:
        count = db.delete(TABLE_METHODS,
            KEY_ROWID + " = " + uri.getLastPathSegment() + prefixAnd(where), whereArgs);
        break;
      case PLANINSTANCE_TRANSACTION_STATUS:
        count = db.delete(TABLE_PLAN_INSTANCE_STATUS, where, whereArgs);
        break;
      case PLANINSTANCE_STATUS_SINGLE:
        count = db.delete(TABLE_PLAN_INSTANCE_STATUS,
            String.format(Locale.ROOT, "%s = ? AND %s = ?", KEY_TEMPLATEID, KEY_INSTANCEID),
            new String[]{uri.getPathSegments().get(1), uri.getPathSegments().get(2)});
        notifyChange(uri, false);
        return count;
      case EVENT_CACHE:
        count = db.delete(TABLE_EVENT_CACHE, where, whereArgs);
        break;
      case STALE_IMAGES_ID:
        segment = uri.getPathSegments().get(1);
        count = db.delete(TABLE_STALE_URIS, "rowid=" + segment, null);
        break;
      case STALE_IMAGES:
        count = db.delete(TABLE_STALE_URIS, where, whereArgs);
        break;
      case CHANGES:
        count = db.delete(TABLE_CHANGES, where, whereArgs);
        break;
      case SETTINGS:
        count = db.delete(TABLE_SETTINGS, where, whereArgs);
        break;
      case BUDGET_ID:
        count = db.delete(TABLE_BUDGETS, KEY_ROWID + " = " + uri.getLastPathSegment() + prefixAnd(where), whereArgs);
        break;
      case PAYEES:
        count = db.delete(TABLE_PAYEES, where, whereArgs);
        break;
      case TAG_ID:
        count = db.delete(TABLE_TAGS,
            KEY_ROWID + " = " + uri.getLastPathSegment() + prefixAnd(where), whereArgs);
        break;
      case DUAL: {
        if ("1".equals(uri.getQueryParameter(QUERY_PARAMETER_SYNC_END))) {
          count = resumeChangeTrigger(db);
        } else {
          throw unknownUri(uri);
        }
        break;
      }
      case CURRENCIES_CODE: {
        String currency = uri.getLastPathSegment();
        if (Utils.isKnownCurrency(currency)) {
          throw new IllegalArgumentException("Can only delete custom currencies");
        }
        try {
          count = db.delete(TABLE_CURRENCIES, String.format("%s = '%s'%s", KEY_CODE,
              currency, prefixAnd(where)), whereArgs);
        } catch (SQLiteConstraintException e) {
          return 0;
        }
        break;
      }
      case TRANSACTIONS_TAGS: {
        count = db.delete(TABLE_TRANSACTIONS_TAGS, where, whereArgs);
        break;
      }
      case TEMPLATES_TAGS: {
        count = db.delete(TABLE_TEMPLATES_TAGS, where, whereArgs);
        break;
      }
      case ACCOUNTS_TAGS: {
        count = db.delete(TABLE_ACCOUNTS_TAGS, where, whereArgs);
        break;
      }
      case DEBT_ID: {
        count = db.delete(TABLE_DEBTS,
            KEY_ROWID + " = " + uri.getLastPathSegment() + prefixAnd(where), whereArgs);
        break;
      }
      default:
        throw unknownUri(uri);
    }
    if (uriMatch == TRANSACTIONS || uriMatch == TRANSACTION_ID) {
      notifyChange(TRANSACTIONS_URI, callerIsNotSyncAdatper(uri));
      notifyChange(ACCOUNTS_URI, false);
      notifyChange(DEBTS_URI, false);
      notifyChange(UNCOMMITTED_URI, false);
    } else {
      if (uriMatch == ACCOUNTS || uriMatch == ACCOUNT_ID) {
        notifyChange(ACCOUNTS_BASE_URI, false);
      }
      if (uriMatch == TEMPLATES || uriMatch == TEMPLATE_ID) {
        notifyChange(TEMPLATES_UNCOMMITTED_URI, false);
      }
      if (uriMatch == DEBT_ID) {
        notifyChange(PAYEES_URI, false);
      } else if (uriMatch == UNCOMMITTED) {
        notifyChange(DEBTS_URI, false);
      }
      notifyChange(uri, false);
    }
    return count;
  }

  private String prefixAnd(String where) {
    if (!TextUtils.isEmpty(where)) {
      return " AND (" + where + ')';
    } else {
      return "";
    }
  }

  @Override
  public int update(@NonNull Uri uri, ContentValues values, String where,
                    String[] whereArgs) {
    setDirty(true);
    SQLiteDatabase db = getTransactionDatabase().getWritableDatabase();
    String segment; // contains rowId
    int count;
    int uriMatch = URI_MATCHER.match(uri);
    Cursor c;
    log("UPDATE Uri: %s, values: %s", uri, values);
    switch (uriMatch) {
      case TRANSACTIONS:
      case UNCOMMITTED:
        count = db.update(TABLE_TRANSACTIONS, values, where, whereArgs);
        break;
      case TRANSACTION_ID:
      case UNCOMMITTED_ID:
        count = db.update(TABLE_TRANSACTIONS, values,
            KEY_ROWID + " = " + uri.getLastPathSegment() + prefixAnd(where),
            whereArgs);
        break;
      case TRANSACTION_UNDELETE:
        segment = uri.getPathSegments().get(1);
        whereArgs = new String[]{segment, segment, segment};
        ContentValues v = new ContentValues();
        v.put(KEY_CR_STATUS, CrStatus.UNRECONCILED.name());
        count = db.update(TABLE_TRANSACTIONS, v, WHERE_SELF_OR_DEPENDENT, whereArgs);
        break;
      case ACCOUNTS:
        count = db.update(TABLE_ACCOUNTS, values, where, whereArgs);
        break;
      case ACCOUNT_ID:
        count = db.update(TABLE_ACCOUNTS, values,
            KEY_ROWID + " = " + uri.getLastPathSegment() + prefixAnd(where), whereArgs);
        break;
      case TEMPLATES:
        count = db.update(TABLE_TEMPLATES, values, where, whereArgs);
        break;
      case TEMPLATE_ID:
        count = db.update(TABLE_TEMPLATES, values,
            KEY_ROWID + " = " + uri.getLastPathSegment() + prefixAnd(where), whereArgs);
        break;
      case PAYEE_ID:
        count = db.update(TABLE_PAYEES, values,
            KEY_ROWID + " = " + uri.getLastPathSegment() + prefixAnd(where), whereArgs);
        notifyChange(TRANSACTIONS_URI, false);
        break;
      case CATEGORIES:
        throw new UnsupportedOperationException("Bulk update of categories is not supported");
      case CATEGORY_ID:
        if (values.containsKey(KEY_LABEL) && values.containsKey(KEY_PARENTID))
          throw new UnsupportedOperationException("Simultaneous update of label and parent is not supported");
        if (values.containsKey(KEY_PARENTID)) {
          Long parentId = values.getAsLong(KEY_PARENTID);
          if (parentId == null && !values.containsKey(KEY_COLOR)) {
            values.put(KEY_COLOR, suggestNewCategoryColor(db));
          }
          if (parentId != null) {
            values.putNull(KEY_COLOR);
          }
        }
        segment = uri.getLastPathSegment();
        count = db.update(TABLE_CATEGORIES, values, KEY_ROWID + " = " + segment + prefixAnd(where),
            whereArgs);
        break;
      case METHOD_ID:
        count = db.update(TABLE_METHODS, values, KEY_ROWID + " = " + uri.getLastPathSegment() + prefixAnd(where),
            whereArgs);
        break;
      case TEMPLATES_INCREASE_USAGE:
        db.execSQL("UPDATE " + TABLE_TEMPLATES + " SET " + KEY_USAGES + " = " + KEY_USAGES + " + 1, " +
            KEY_LAST_USED + " = strftime('%s', 'now') WHERE " + KEY_ROWID + " = " + uri.getPathSegments().get(1));
        count = 1;
        break;
      //   when we move a transaction to a new target we apply two checks
      //1) we do not move a transfer to its own transfer_account
      //2) we check if the transactions method_id is also available in the target account, if not we set it to null
      case TRANSACTION_MOVE:
        segment = uri.getPathSegments().get(1);
        String target = uri.getPathSegments().get(3);
        db.execSQL("UPDATE " + TABLE_TRANSACTIONS +
                " SET " +
                KEY_ACCOUNTID + " = ?, " +
                KEY_METHODID + " = " +
                " CASE " +
                " WHEN exists " +
                " (SELECT 1 FROM " + TABLE_ACCOUNTTYES_METHODS +
                " WHERE " + KEY_TYPE + " = " +
                " (SELECT " + KEY_TYPE + " FROM " + TABLE_ACCOUNTS +
                " WHERE " + DatabaseConstants.KEY_ROWID + " = ?) " +
                " AND " + KEY_METHODID + " = " + TABLE_TRANSACTIONS + "." + KEY_METHODID + ")" +
                " THEN " + KEY_METHODID +
                " ELSE null " +
                " END " +
                " WHERE " + DatabaseConstants.KEY_ROWID + " = ? " +
                " AND ( " + KEY_TRANSFER_ACCOUNT + " IS NULL OR " + KEY_TRANSFER_ACCOUNT + "  != ? )",
            new String[]{target, target, segment, target});
        count = 1;
        break;
      case TRANSACTION_TOGGLE_CRSTATUS:
        db.execSQL("UPDATE " + TABLE_TRANSACTIONS +
                " SET " + KEY_CR_STATUS +
                " = CASE " + KEY_CR_STATUS +
                " WHEN '" + "CLEARED" + "'" +
                " THEN '" + "UNRECONCILED" + "'" +
                " WHEN '" + "UNRECONCILED" + "'" +
                " THEN '" + "CLEARED" + "'" +
                " ELSE " + KEY_CR_STATUS +
                " END" +
                " WHERE " + DatabaseConstants.KEY_ROWID + " = ? ",
            new String[]{uri.getPathSegments().get(1)});
        count = 1;
        break;
      case CURRENCIES_CHANGE_FRACTION_DIGITS:
        synchronized (MyApplication.getInstance()) {
          List<String> segments = uri.getPathSegments();
          segment = segments.get(2);
          String[] bindArgs = new String[]{segment};
          int oldValue = currencyContext.get(segment).getFractionDigits();
          int newValue = Integer.parseInt(segments.get(3));
          if (oldValue == newValue) {
            return 0;
          }
          c = db.query(
              TABLE_ACCOUNTS,
              new String[]{"count(*)"},
              KEY_CURRENCY + "=?",
              bindArgs, null, null, null);
          count = 0;
          if (c.getCount() != 0) {
            c.moveToFirst();
            count = c.getInt(0);
          }
          c.close();
          String operation = oldValue < newValue ? "*" : "/";
          int factor = (int) Math.pow(10, Math.abs(oldValue - newValue));
          if (count != 0) {
            MoreDbUtilsKt.safeUpdateWithSealed(db, () -> {
              db.execSQL("UPDATE " + TABLE_ACCOUNTS + " SET " + KEY_OPENING_BALANCE + "="
                      + KEY_OPENING_BALANCE + operation + factor + " WHERE " + KEY_CURRENCY + "=?",
                  bindArgs);
              db.execSQL("UPDATE " + TABLE_TRANSACTIONS + " SET " + KEY_AMOUNT + "="
                      + KEY_AMOUNT + operation + factor + " WHERE " + KEY_ACCOUNTID
                      + " IN (SELECT " + KEY_ROWID + " FROM " + TABLE_ACCOUNTS + " WHERE " + KEY_CURRENCY + "=?)",
                  bindArgs);

              db.execSQL("UPDATE " + TABLE_TEMPLATES + " SET " + KEY_AMOUNT + "="
                      + KEY_AMOUNT + operation + factor + " WHERE " + KEY_ACCOUNTID
                      + " IN (SELECT " + KEY_ROWID + " FROM " + TABLE_ACCOUNTS + " WHERE " + KEY_CURRENCY + "=?)",
                  bindArgs);
              currencyContext.storeCustomFractionDigits(segment, newValue);
            });
          } else {
            currencyContext.storeCustomFractionDigits(segment, newValue);
          }
        }
        break;
      case ACCOUNTS_SWAP_SORT_KEY:
        String sortKey1 = uri.getPathSegments().get(2);
        String sortKey2 = uri.getPathSegments().get(3);
        db.execSQL("UPDATE " + TABLE_ACCOUNTS + " SET " + KEY_SORT_KEY + " = CASE " + KEY_SORT_KEY +
                " WHEN ? THEN ? WHEN ? THEN ? END WHERE " + KEY_SORT_KEY + " in (?,?);",
            new String[]{sortKey1, sortKey2, sortKey2, sortKey1, sortKey1, sortKey2});
        count = 2;
        break;
      case CHANGES:
        if ("1".equals(uri.getQueryParameter(QUERY_PARAMETER_INIT))) {
          String[] accountIdBindArgs = {uri.getQueryParameter(KEY_ACCOUNTID)};
          db.beginTransaction();
          try {
            db.delete(TABLE_CHANGES, KEY_ACCOUNTID + " = ?", accountIdBindArgs);
            c = db.query(TABLE_TRANSACTIONS, new String[]{KEY_ROWID}, "(" + KEY_UUID + " IS NULL OR (" + KEY_TRANSFER_PEER + " IS NOT NULL AND (SELECT "+ KEY_UUID + " from "+ TABLE_TRANSACTIONS +  " peer where " + KEY_TRANSFER_PEER + " = "  + TABLE_TRANSACTIONS + "." + KEY_ROWID + ") is null )) AND ("
                + KEY_TRANSFER_PEER + " IS NULL OR " + KEY_ROWID + " < " + KEY_TRANSFER_PEER + ")", null, null, null, null);
            if (c.moveToFirst()) {
              MoreDbUtilsKt.safeUpdateWithSealed(db, () -> {
                while (!c.isAfterLast()) {
                  String idString = c.getString(0);
                  db.execSQL("UPDATE " + TABLE_TRANSACTIONS + " SET " + KEY_UUID + " = ? WHERE " + KEY_ROWID + " = ? OR " + KEY_TRANSFER_PEER + " = ?",
                          new String[]{Model.generateUuid(), idString, idString});
                  c.moveToNext();
                }
              });
            }
            c.close();
            db.execSQL("INSERT INTO " + TABLE_CHANGES + "("
                    + KEY_TYPE + ", "
                    + KEY_SYNC_SEQUENCE_LOCAL + ", "
                    + KEY_UUID + ", "
                    + KEY_PARENT_UUID + ", "
                    + KEY_COMMENT + ", "
                    + KEY_DATE + ", "
                    + KEY_AMOUNT + ", "
                    + KEY_ORIGINAL_AMOUNT + ", "
                    + KEY_ORIGINAL_CURRENCY + ", "
                    + KEY_EQUIVALENT_AMOUNT + ", "
                    + KEY_CATID + ", "
                    + KEY_ACCOUNTID + ","
                    + KEY_PAYEEID + ", "
                    + KEY_TRANSFER_ACCOUNT + ", "
                    + KEY_METHODID + ","
                    + KEY_CR_STATUS + ", "
                    + KEY_REFERENCE_NUMBER + ", "
                    + KEY_PICTURE_URI
                    + ") SELECT "
                    + "'" + TransactionChange.Type.created.name() + "', "
                    + " 1, "
                    + KEY_UUID + ", "
                    + "CASE WHEN " + KEY_PARENTID + " IS NULL THEN NULL ELSE " +
                    "(SELECT " + KEY_UUID + " FROM " + TABLE_TRANSACTIONS + " parent where "
                    + KEY_ROWID + " = " + TABLE_TRANSACTIONS + "." + KEY_PARENTID + ") END, "
                    + KEY_COMMENT + ", "
                    + KEY_DATE + ", "
                    + KEY_AMOUNT + ", "
                    + KEY_ORIGINAL_AMOUNT + ", "
                    + KEY_ORIGINAL_CURRENCY + ", "
                    + KEY_EQUIVALENT_AMOUNT + ", "
                    + KEY_CATID + ", "
                    + KEY_ACCOUNTID + ", "
                    + KEY_PAYEEID + ", "
                    + KEY_TRANSFER_ACCOUNT + ", "
                    + KEY_METHODID + ","
                    + KEY_CR_STATUS + ", "
                    + KEY_REFERENCE_NUMBER + ", "
                    + KEY_PICTURE_URI
                    + " FROM " + TABLE_TRANSACTIONS + " WHERE " + KEY_ACCOUNTID + " = ?",
                accountIdBindArgs);
            ContentValues currentSyncIncrease = new ContentValues(1);
            currentSyncIncrease.put(KEY_SYNC_SEQUENCE_LOCAL, 1);
            db.update(TABLE_ACCOUNTS, currentSyncIncrease, KEY_ROWID + " = ?", accountIdBindArgs);
            db.setTransactionSuccessful();
          } catch (Exception e) {
            CrashHandler.report(e, TAG);
            throw e;
          } finally {
            db.endTransaction();
          }
          count = 1;
        } else {
          count = db.update(TABLE_CHANGES, values, where, whereArgs);
          break;
        }
        break;
      case ACCOUNT_ID_GROUPING: {
        segment = uri.getPathSegments().get(1);
        long id = Long.parseLong(segment);
        boolean isAggregate = id < 0;
        ContentValues contentValues = new ContentValues(1);
        contentValues.put(KEY_GROUPING, uri.getPathSegments().get(2));
        count = db.update(isAggregate ? TABLE_CURRENCIES : TABLE_ACCOUNTS, contentValues,
            KEY_ROWID + " = ?", new String[]{String.valueOf(Math.abs(id))});
        break;
      }
      case ACCOUNT_ID_SORTDIRECTION: {
        segment = uri.getPathSegments().get(1);
        ContentValues contentValues = new ContentValues(1);
        contentValues.put(KEY_SORT_DIRECTION, uri.getPathSegments().get(3));
        count = db.update(TABLE_ACCOUNTS, contentValues, KEY_ROWID + " = ?", new String[]{segment});
        break;
      }
      case UNSPLIT: {
        String uuid = values.getAsString(KEY_UUID);

        final String subselectTemplate = String.format("(SELECT %%1$s FROM %s WHERE %s = ?)", TABLE_TRANSACTIONS, KEY_UUID);
        String crStatusSubSelect = String.format(Locale.ROOT, subselectTemplate, KEY_CR_STATUS);
        String payeeIdSubSelect = String.format(Locale.ROOT, subselectTemplate, KEY_PAYEEID);
        String rowIdSubSelect = String.format(Locale.ROOT, subselectTemplate, KEY_ROWID);
        String accountIdSubSelect = String.format(Locale.ROOT, subselectTemplate, KEY_ACCOUNTID);

        try {
          db.beginTransaction();
          pauseChangeTrigger(db);
          //parts are promoted to independence
          db.execSQL(String.format(Locale.ROOT, "UPDATE %s SET %s = null, %s = %s, %s = %s WHERE %s = %s ",
              TABLE_TRANSACTIONS, KEY_PARENTID, KEY_CR_STATUS, crStatusSubSelect, KEY_PAYEEID, payeeIdSubSelect, KEY_PARENTID, rowIdSubSelect),
              new String[]{uuid, uuid, uuid});
          //Change is recorded
          if (callerIsNotSyncAdatper(uri)) {
            db.execSQL(String.format(Locale.ROOT, "INSERT INTO %1$s (%2$s, %3$s, %4$s, %5$s) SELECT '%6$s', %7$s, %4$s, ? FROM %8$s WHERE %7$s = %9$s AND %10$s IS NOT NULL",
                TABLE_CHANGES, KEY_TYPE, KEY_ACCOUNTID, KEY_SYNC_SEQUENCE_LOCAL, KEY_UUID,
                TransactionChange.Type.unsplit.name(), KEY_ROWID, TABLE_ACCOUNTS, accountIdSubSelect, KEY_SYNC_ACCOUNT_NAME), new String[]{uuid, uuid});
          }
          //parent is deleted
          count = db.delete(TABLE_TRANSACTIONS, KEY_UUID + " = ?", new String[]{uuid});
          resumeChangeTrigger(db);
          db.setTransactionSuccessful();
        } finally {
          db.endTransaction();
        }
        break;
      }
      case BUDGET_ID: {
        count = db.update(TABLE_BUDGETS, values,
            KEY_ROWID + " = " + uri.getLastPathSegment() + prefixAnd(where), whereArgs);
        break;
      }
      case BUDGET_CATEGORY: {
        count = budgetCategoryUpsert(db, uri, values);
        break;
      }
      case BUDGET_ALLOCATIONS: {
        count = db.update(TABLE_BUDGET_ALLOCATIONS, values, where, whereArgs);
        break;
      }
      case CURRENCIES_CODE: {
        final String currency = uri.getLastPathSegment();
        count = db.update(TABLE_CURRENCIES, values, String.format("%s = '%s'%s", KEY_CODE,
            currency, prefixAnd(where)), whereArgs);
        break;
      }
      case TAG_ID: {
        count = db.update(TABLE_TAGS, values,
            KEY_ROWID + " = " + uri.getLastPathSegment() + prefixAnd(where), whereArgs);
        break;
      }
      case TRANSACTION_LINK_TRANSFER: {
        count = MoreDbUtilsKt.linkTransfers(db, uri.getPathSegments().get(2), values.getAsString(KEY_UUID), callerIsNotSyncAdatper(uri));
        break;
      }
      case DEBTS:
        count = db.update(TABLE_DEBTS, values, where, whereArgs);
        break;
      case DEBT_ID: {
        count = db.update(TABLE_DEBTS, values,
            KEY_ROWID + " = " + uri.getLastPathSegment() + prefixAnd(where), whereArgs);
        break;
      }
      default:
        throw unknownUri(uri);
    }
    if (uriMatch == TRANSACTIONS || uriMatch == TRANSACTION_ID || uriMatch == ACCOUNTS || uriMatch == ACCOUNT_ID ||
        uriMatch == CURRENCIES_CHANGE_FRACTION_DIGITS || uriMatch == TRANSACTION_UNDELETE ||
        uriMatch == TRANSACTION_MOVE || uriMatch == TRANSACTION_TOGGLE_CRSTATUS || uriMatch == TRANSACTION_LINK_TRANSFER) {
      notifyChange(TRANSACTIONS_URI, callerIsNotSyncAdatper(uri));
      notifyChange(ACCOUNTS_URI, false);
      notifyChange(DEBTS_URI, false);
      notifyChange(UNCOMMITTED_URI, false);
      notifyChange(CATEGORIES_URI, false);
    } else if (
      //we do not need to refresh cursors on the usage counters
        uriMatch != TEMPLATES_INCREASE_USAGE) {
      notifyChange(uri, false);
    }
    if (uriMatch == ACCOUNT_ID_GROUPING) {
      notifyChange(ACCOUNTS_URI, false);
    }
    if (uriMatch == CURRENCIES_CHANGE_FRACTION_DIGITS || uriMatch == TEMPLATES_INCREASE_USAGE) {
      notifyChange(TEMPLATES_URI, false);
    }
    if (uriMatch == TEMPLATES || uriMatch == TEMPLATE_ID) {
      notifyChange(TEMPLATES_UNCOMMITTED_URI, false);
    }
    if (uriMatch == BUDGET_CATEGORY) {
      notifyChange(CATEGORIES_URI, false);
    }
    if (uriMatch == BUDGET_ALLOCATIONS) {
      notifyChange(CATEGORIES_URI, false);
      notifyChange(BUDGETS_URI, false);
    }
    if (uriMatch == ACCOUNTS || uriMatch == ACCOUNT_ID) {
      notifyChange(ACCOUNTS_BASE_URI, false);
    }
    if (uriMatch == UNCOMMITTED_ID || uriMatch == UNCOMMITTED) {
      notifyChange(UNCOMMITTED_URI, false);
    }
    return count;
  }

  private void notifyChange(Uri uri, boolean syncToNetwork) {
    if (!bulkInProgress) {
      getContext().getContentResolver().notifyChange(uri, null,
          syncToNetwork && prefHandler.getBoolean(PrefKey.SYNC_CHANGES_IMMEDIATELY, true));
    }
  }

  private boolean callerIsNotSyncAdatper(Uri uri) {
    return uri.getQueryParameter(QUERY_PARAMETER_CALLER_IS_SYNCADAPTER) == null;
  }

  /**
   * Apply the given set of {@link ContentProviderOperation}, executing inside
   * a {@link SQLiteDatabase} transaction. All changes will be rolled back if
   * any single one fails.
   */
  @NonNull
  @Override
  public ContentProviderResult[] applyBatch(@NonNull ArrayList<ContentProviderOperation> operations)
      throws OperationApplicationException {
    final SQLiteDatabase db = getTransactionDatabase().getWritableDatabase();
    db.beginTransaction();
    try {
      final int numOperations = operations.size();
      final ContentProviderResult[] results = new ContentProviderResult[numOperations];
      for (int i = 0; i < numOperations; i++) {
        final ContentProviderOperation contentProviderOperation = operations.get(i);
        try {
          results[i] = contentProviderOperation.apply(this, results, i);
        } catch (Exception e) {
          Map<String, String> customData = new HashMap<>();
          customData.put("i", String.valueOf(i));
          customData.put("operation", contentProviderOperation.toString());
          CrashHandler.report(e, customData, TAG);
          throw e;
        }
      }
      db.setTransactionSuccessful();
      return results;
    } finally {
      db.endTransaction();
    }
  }

  @Nullable
  @Override
  public Bundle call(@NonNull String method, @Nullable String arg, @Nullable Bundle extras) {
    switch (method) {
      case METHOD_INIT: {
        getTransactionDatabase().getReadableDatabase();
        break;
      }
      case METHOD_BULK_START: {
        bulkInProgress = true;
        break;
      }
      case METHOD_BULK_END: {
        bulkInProgress = false;
        notifyChange(TRANSACTIONS_URI, true);
        notifyChange(ACCOUNTS_URI, false);
        notifyChange(CATEGORIES_URI, false);
        notifyChange(PAYEES_URI, false);
        notifyChange(METHODS_URI, false);
        break;
      }
      case METHOD_SORT_ACCOUNTS: {
        final SQLiteDatabase db = getTransactionDatabase().getWritableDatabase();
        if (extras != null) {
          long[] sortedIds = extras.getLongArray(KEY_SORT_KEY);
          if (sortedIds != null) {
            ContentValues values = new ContentValues(1);
            for (int i = 0; i < sortedIds.length; i++) {
              values.put(KEY_SORT_KEY, i);
              db.update(TABLE_ACCOUNTS, values, KEY_ROWID + " = ?", new String[]{String.valueOf(sortedIds[i])});
            }
            notifyChange(ACCOUNTS_URI, false);
          }
        }
        break;
      }
      case METHOD_SETUP_CATEGORIES: {
        Bundle result = new Bundle(1);
        result.putSerializable(KEY_RESULT, MoreDbUtilsKt.setupDefaultCategories(getTransactionDatabase().getWritableDatabase(), wrappedContext().getResources()));
        notifyChange(CATEGORIES_URI, false);
        return result;
      }

      case METHOD_RESET_EQUIVALENT_AMOUNTS: {
        final SQLiteDatabase db = getTransactionDatabase().getWritableDatabase();
        Bundle result = new Bundle(1);
        MoreDbUtilsKt.safeUpdateWithSealed(db, () -> {
          ContentValues resetValues = new ContentValues(1);
          resetValues.putNull(KEY_EQUIVALENT_AMOUNT);
          result.putInt(KEY_RESULT, db.update(TABLE_TRANSACTIONS, resetValues, KEY_EQUIVALENT_AMOUNT + " IS NOT NULL", null));
        });
        return result;
      }
      case METHOD_CHECK_CORRUPTED_DATA_987: {
        return checkCorruptedData987();
      }
    }
    return null;
  }

  static {
    URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
    URI_MATCHER.addURI(AUTHORITY, "transactions", TRANSACTIONS);
    URI_MATCHER.addURI(AUTHORITY, "transactions/" + URI_SEGMENT_UNCOMMITTED, UNCOMMITTED);
    URI_MATCHER.addURI(AUTHORITY, "transactions/" + URI_SEGMENT_GROUPS + "/*", TRANSACTIONS_GROUPS);
    URI_MATCHER.addURI(AUTHORITY, "transactions/sumsForAccounts", TRANSACTIONS_SUMS);
    URI_MATCHER.addURI(AUTHORITY, "transactions/" + URI_SEGMENT_LAST_EXCHANGE + "/*/*", TRANSACTIONS_LASTEXCHANGE);
    URI_MATCHER.addURI(AUTHORITY, "transactions/#", TRANSACTION_ID);
    URI_MATCHER.addURI(AUTHORITY, "transactions/" + URI_SEGMENT_UNCOMMITTED + "/#", UNCOMMITTED_ID);
    URI_MATCHER.addURI(AUTHORITY, "transactions/#/" + URI_SEGMENT_MOVE + "/#", TRANSACTION_MOVE);
    URI_MATCHER.addURI(AUTHORITY, "transactions/#/" + URI_SEGMENT_TOGGLE_CRSTATUS, TRANSACTION_TOGGLE_CRSTATUS);
    URI_MATCHER.addURI(AUTHORITY, "transactions/#/" + URI_SEGMENT_UNDELETE, TRANSACTION_UNDELETE);
    //uses uuid in order to be usable from sync adapter
    URI_MATCHER.addURI(AUTHORITY, "transactions/" + URI_SEGMENT_UNSPLIT, UNSPLIT);
    URI_MATCHER.addURI(AUTHORITY, "categories", CATEGORIES);
    URI_MATCHER.addURI(AUTHORITY, "categories/#", CATEGORY_ID);
    URI_MATCHER.addURI(AUTHORITY, "accounts", ACCOUNTS);
    URI_MATCHER.addURI(AUTHORITY, "accountsbase", ACCOUNTS_BASE);
    URI_MATCHER.addURI(AUTHORITY, "accounts/#", ACCOUNT_ID);
    URI_MATCHER.addURI(AUTHORITY, "account_groupings/*/*", ACCOUNT_ID_GROUPING);
    URI_MATCHER.addURI(AUTHORITY, "accounts/#/" + URI_SEGMENT_SORT_DIRECTION + "/*", ACCOUNT_ID_SORTDIRECTION);
    URI_MATCHER.addURI(AUTHORITY, "payees", PAYEES);
    URI_MATCHER.addURI(AUTHORITY, "payees/#", PAYEE_ID);
    URI_MATCHER.addURI(AUTHORITY, "methods", METHODS);
    URI_MATCHER.addURI(AUTHORITY, "methods/#", METHOD_ID);
    //methods/typeFilter/{TransactionType}/{AccountType}
    //TransactionType: 1 Income, -1 Expense
    //AccountType: CASH BANK CCARD ASSET LIABILITY
    URI_MATCHER.addURI(AUTHORITY, "methods/" + URI_SEGMENT_TYPE_FILTER + "/*", METHODS_FILTERED);
    URI_MATCHER.addURI(AUTHORITY, "accounts/aggregatesCount", AGGREGATES_COUNT);
    URI_MATCHER.addURI(AUTHORITY, "accounttypes_methods", ACCOUNTTYPES_METHODS);
    URI_MATCHER.addURI(AUTHORITY, "templates", TEMPLATES);
    URI_MATCHER.addURI(AUTHORITY, "templates/uncommitted", TEMPLATES_UNCOMMITTED);
    URI_MATCHER.addURI(AUTHORITY, "templates/#", TEMPLATE_ID);
    URI_MATCHER.addURI(AUTHORITY, "templates/#/" + URI_SEGMENT_INCREASE_USAGE, TEMPLATES_INCREASE_USAGE);
    URI_MATCHER.addURI(AUTHORITY, "sqlite_sequence/*", SQLITE_SEQUENCE_TABLE);
    URI_MATCHER.addURI(AUTHORITY, "planinstance_transaction", PLANINSTANCE_TRANSACTION_STATUS);
    URI_MATCHER.addURI(AUTHORITY, "planinstance_transaction/#/#", PLANINSTANCE_STATUS_SINGLE);
    URI_MATCHER.addURI(AUTHORITY, "currencies", CURRENCIES);
    URI_MATCHER.addURI(AUTHORITY, "currencies/" + URI_SEGMENT_CHANGE_FRACTION_DIGITS + "/*/#", CURRENCIES_CHANGE_FRACTION_DIGITS);
    URI_MATCHER.addURI(AUTHORITY, "accounts/aggregates/*", AGGREGATE_ID);
    URI_MATCHER.addURI(AUTHORITY, "methods_transactions", MAPPED_METHODS);
    URI_MATCHER.addURI(AUTHORITY, "dual", DUAL);
    URI_MATCHER.addURI(AUTHORITY, "eventcache", EVENT_CACHE);
    URI_MATCHER.addURI(AUTHORITY, "debug_schema", DEBUG_SCHEMA);
    URI_MATCHER.addURI(AUTHORITY, "stale_images", STALE_IMAGES);
    URI_MATCHER.addURI(AUTHORITY, "stale_images/#", STALE_IMAGES_ID);
    URI_MATCHER.addURI(AUTHORITY, "accounts/" + URI_SEGMENT_SWAP_SORT_KEY + "/#/#", ACCOUNTS_SWAP_SORT_KEY);
    URI_MATCHER.addURI(AUTHORITY, "transfer_account_transactions", MAPPED_TRANSFER_ACCOUNTS);
    URI_MATCHER.addURI(AUTHORITY, "changes", CHANGES);
    URI_MATCHER.addURI(AUTHORITY, "settings", SETTINGS);
    URI_MATCHER.addURI(AUTHORITY, "autofill/#", AUTOFILL);
    URI_MATCHER.addURI(AUTHORITY, "account_exchangerates/#/*/*", ACCOUNT_EXCHANGE_RATE);
    URI_MATCHER.addURI(AUTHORITY, "budgets", BUDGETS);
    URI_MATCHER.addURI(AUTHORITY, "budgets/#", BUDGET_ID);
    URI_MATCHER.addURI(AUTHORITY, "budgets/#/#", BUDGET_CATEGORY);
    URI_MATCHER.addURI(AUTHORITY, "currencies/*", CURRENCIES_CODE);
    URI_MATCHER.addURI(AUTHORITY, "accountsMinimal", ACCOUNTS_MINIMAL);
    URI_MATCHER.addURI(AUTHORITY, "tags", TAGS);
    URI_MATCHER.addURI(AUTHORITY, "transactions/tags", TRANSACTIONS_TAGS);
    URI_MATCHER.addURI(AUTHORITY, "tags/#", TAG_ID);
    URI_MATCHER.addURI(AUTHORITY, "templates/tags", TEMPLATES_TAGS);
    URI_MATCHER.addURI(AUTHORITY, "transactions/" + URI_SEGMENT_LINK_TRANSFER + "/*", TRANSACTION_LINK_TRANSFER);
    URI_MATCHER.addURI(AUTHORITY, "accounts/tags", ACCOUNTS_TAGS);
    URI_MATCHER.addURI(AUTHORITY, "debts", DEBTS);
    URI_MATCHER.addURI(AUTHORITY, "debts/#", DEBT_ID);
    URI_MATCHER.addURI(AUTHORITY, "budgets/allocations/", BUDGET_ALLOCATIONS);
  }

  /**
   * A test package can call this to get a handle to the database underlying TransactionProvider,
   * so it can insert test data into the database. The test case class is responsible for
   * instantiating the provider in a test context; ProviderTestCase2 does
   * this during the call to setUp()
   *
   * @return a handle to the database helper object for the provider's data.
   */
  @VisibleForTesting
  public TransactionDatabase getOpenHelperForTest() {
    return getTransactionDatabase();
  }

  public boolean restore(File backupFile) {
    File dataDir = new File(getInternalAppDir(), "databases");
    dataDir.mkdir();
    //line below gives app_databases instead of databases ???
    //File currentDb = new File(mCtx.getDir("databases", 0),mDatabaseName);
    File currentDb = new File(dataDir, getDatabaseName());
    boolean result = false;
    getTransactionDatabase().close();
    try {
      result = FileCopyUtils.copy(backupFile, currentDb);
    } finally {
      initOpenHelper();
    }
    return result;
  }

  public static ContentProviderOperation resumeChangeTrigger() {
    return ContentProviderOperation.newDelete(
        DUAL_URI.buildUpon()
            .appendQueryParameter(QUERY_PARAMETER_SYNC_END, "1").build())
        .build();
  }

  static int resumeChangeTrigger(SQLiteDatabase db) {
    return db.delete(TABLE_SYNC_STATE, null, null);
  }

  public static ContentProviderOperation pauseChangeTrigger() {
    return ContentProviderOperation.newInsert(
        DUAL_URI.buildUpon()
            .appendQueryParameter(QUERY_PARAMETER_SYNC_BEGIN, "1").build())
        .build();
  }

  static long pauseChangeTrigger(SQLiteDatabase db) {
    ContentValues values = new ContentValues(1);
    values.put(KEY_STATUS, "1");
    return db.insertOrThrow(TABLE_SYNC_STATE, null, values);
  }

  private String[] extendProjectionWithSealedCheck(String[] baseProjection, String baseTable) {
    int baseLength = baseProjection.length;
    String[] projection = new String[baseLength + 1];
    System.arraycopy(baseProjection, 0, projection, 0, baseLength);
    projection[baseLength] = checkForSealedAccount(baseTable, TABLE_TEMPLATES) + " AS " + KEY_SEALED;
    return projection;
  }

}
