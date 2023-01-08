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

package org.totschnig.myexpenses.model;

import static android.content.ContentProviderOperation.newUpdate;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_ACCOUNTID;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_AMOUNT;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_CODE;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_COLOR;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_CRITERION;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_CURRENCY;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_DESCRIPTION;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_EXCHANGE_RATE;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_EXCLUDE_FROM_TOTALS;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_GROUPING;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_LABEL;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_OPENING_BALANCE;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_PARENTID;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_ROWID;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_SEALED;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_SORT_DIRECTION;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_SORT_KEY;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_STATUS;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_SYNC_ACCOUNT_NAME;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_TRANSFER_ACCOUNT;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_TRANSFER_PEER;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_TYPE;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_UUID;
import static org.totschnig.myexpenses.provider.DatabaseConstants.STATUS_EXPORTED;
import static org.totschnig.myexpenses.provider.DatabaseConstants.TABLE_ACCOUNTS;
import static org.totschnig.myexpenses.provider.DatabaseConstants.TABLE_TRANSACTIONS;
import static org.totschnig.myexpenses.provider.DatabaseConstants.WHERE_NOT_SPLIT_PART;
import static org.totschnig.myexpenses.provider.DatabaseConstants.WHERE_NOT_VOID;
import static org.totschnig.myexpenses.provider.TransactionProvider.ACCOUNTS_TAGS_URI;

import android.accounts.AccountManager;
import android.content.ContentProviderOperation;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import org.apache.commons.lang3.StringUtils;
import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.di.AppComponent;
import org.totschnig.myexpenses.preference.PrefHandler;
import org.totschnig.myexpenses.preference.PrefKey;
import org.totschnig.myexpenses.provider.DatabaseConstants;
import org.totschnig.myexpenses.provider.DbUtils;
import org.totschnig.myexpenses.provider.TransactionProvider;
import org.totschnig.myexpenses.provider.filter.WhereFilter;
import org.totschnig.myexpenses.sync.GenericAccountService;
import org.totschnig.myexpenses.sync.SyncAdapter;
import org.totschnig.myexpenses.util.ShortcutHelper;
import org.totschnig.myexpenses.util.Utils;
import org.totschnig.myexpenses.util.licence.LicenceHandler;
import org.totschnig.myexpenses.viewmodel.data.Debt;
import org.totschnig.myexpenses.viewmodel.data.DistributionAccountInfo;
import org.totschnig.myexpenses.viewmodel.data.Tag;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Account represents an account stored in the database.
 * Accounts have label, opening balance, description and currency
 *
 * @author Michael Totschnig
 */
public class Account extends Model implements DistributionAccountInfo {

  public static final int EXPORT_HANDLE_DELETED_DO_NOTHING = -1;
  public static final int EXPORT_HANDLE_DELETED_UPDATE_BALANCE = 0;
  public static final int EXPORT_HANDLE_DELETED_CREATE_HELPER = 1;
  public final static long HOME_AGGREGATE_ID = Integer.MIN_VALUE;
  private final static Uri LINKED_TAGS_URI = ACCOUNTS_TAGS_URI;
  private final static String LINKED_TAGS_COLUMN = KEY_ACCOUNTID;

  private String label;

  public Money openingBalance;

  private CurrencyUnit currencyUnit;

  public String description;

  public int color;

  public boolean excludeFromTotals = false;

  private String syncAccountName;

  private SortDirection sortDirection = SortDirection.DESC;

  private Money criterion;

  /**
   * exchange rate comparing major units
   */
  private double exchangeRate = 1D;

  private boolean sealed;

  public String getSyncAccountName() {
    return syncAccountName;
  }

  public void setSyncAccountName(String syncAccountName) {
    this.syncAccountName = syncAccountName;
  }

  public double getExchangeRate() {
    return exchangeRate;
  }

  public void setExchangeRate(double exchangeRate) {
    this.exchangeRate = exchangeRate;
  }

  public CurrencyUnit getCurrencyUnit() {
    return currencyUnit;
  }

  public static String[] PROJECTION_BASE;

  static {
    buildProjection();
  }

  public static void buildProjection() {
    PROJECTION_BASE = new String[]{
        TABLE_ACCOUNTS + "." + KEY_ROWID + " AS " + KEY_ROWID,
        KEY_LABEL,
        TABLE_ACCOUNTS + "." + KEY_DESCRIPTION + " AS " + KEY_DESCRIPTION,
        KEY_OPENING_BALANCE,
        TABLE_ACCOUNTS + "." + KEY_CURRENCY + " AS " + KEY_CURRENCY,
        KEY_COLOR,
        TABLE_ACCOUNTS + "." + KEY_GROUPING + " AS " + KEY_GROUPING,
        KEY_TYPE,
        KEY_SORT_KEY,
        KEY_EXCLUDE_FROM_TOTALS,
        KEY_SYNC_ACCOUNT_NAME,
        KEY_UUID,
        KEY_SORT_DIRECTION,
        KEY_EXCHANGE_RATE,
        KEY_CRITERION,
        KEY_SEALED
    };
  }

  public static final Uri CONTENT_URI = TransactionProvider.ACCOUNTS_URI;

  private AccountType type;

  @NonNull
  private Grouping grouping = Grouping.MONTH;

  public static final int DEFAULT_COLOR = 0xff009688;

  /**
   * @param id id of account to be retrieved, if id == 0, the account with the lowest id will be fetched from db,
   *           if id < 0 we forward to AggregateAccount
   * @return Account object or null if no account with id exists in db
   * TODO: We should no longer allow calling this from the UI thread and consistently load account in the background
   */
  @WorkerThread
  public static Account getInstanceFromDb(long id) {
    return getInstanceFromDb(id, false);
  }


  private static Account getInstanceFromDb(long id, boolean openOnly) {
    if (id < 0)
      return AggregateAccount.getInstanceFromDb(id);
    Account account;
    String selection = TABLE_ACCOUNTS + "." + KEY_ROWID + " = ";
    if (id == 0) {
      selection += String.format("(SELECT min(%s) FROM %s%s)", KEY_ROWID, TABLE_ACCOUNTS,
          openOnly ? String.format(" WHERE %s = 0", KEY_SEALED) : "");
    } else {
      selection += id;
      if (openOnly) {
        selection += String.format(" AND %s = 0", KEY_SEALED);
      }
    }
    Cursor c = cr().query(
        CONTENT_URI, null, selection, null, null);
    if (c == null) {
      return null;
    }
    if (c.getCount() == 0) {
      c.close();
      return null;
    }
    c.moveToFirst();
    account = new Account(c);
    c.close();
    return account;
  }

  public static kotlin.Pair<Account, List<Tag>> getInstanceFromDbWithTags(long id) {
    Account t = getInstanceFromDb(id);
    return t == null ? null : new kotlin.Pair<>(t, loadTags(id));
  }

  @Nullable
  public static List<Tag> loadTags(long id) {
    return ModelWithLinkedTagsKt.loadTags(LINKED_TAGS_URI, LINKED_TAGS_COLUMN, id);
  }

  public boolean saveTags(@Nullable List<Tag> tags) {
    return ModelWithLinkedTagsKt.saveTags(LINKED_TAGS_URI, LINKED_TAGS_COLUMN, tags, getId());
  }

  private Uri buildExchangeRateUri() {
    return ContentUris.appendId(TransactionProvider.ACCOUNT_EXCHANGE_RATE_URI.buildUpon(), getId())
        .appendEncodedPath(currencyUnit.getCode())
        .appendEncodedPath(PrefKey.HOME_CURRENCY.getString(currencyUnit.getCode())).build();
  }

  private double adjustExchangeRate(double raw) {
    return Utils.adjustExchangeRate(raw, currencyUnit);
  }

  private void storeExchangeRate() {
    ContentValues exchangeRateValues = new ContentValues();
    int minorUnitDelta = Utils.getHomeCurrency().getFractionDigits() - currencyUnit.getFractionDigits();
    exchangeRateValues.put(KEY_EXCHANGE_RATE, exchangeRate * Math.pow(10, minorUnitDelta));
    cr().insert(buildExchangeRateUri(), exchangeRateValues);
  }

  private boolean hasForeignCurrency() {
    return !PrefKey.HOME_CURRENCY.getString(currencyUnit.getCode()).equals(currencyUnit.getCode());
  }

  /**
   * the account returned by this method is guaranteed not to be sealed
   */
  static Account getInstanceFromDbWithFallback(long id) {
    Account account = getInstanceFromDb(id, true);
    if (account == null && id > 0) {
      account = getInstanceFromDb(0, true);
    }
    return account;
  }

  public static void checkSyncAccounts(Context context) {
    String[] validAccounts = GenericAccountService.getAccountNames(context);
    ContentValues values = new ContentValues(1);
    values.putNull(KEY_SYNC_ACCOUNT_NAME);
    String where = validAccounts.length > 0 ?
        KEY_SYNC_ACCOUNT_NAME + " NOT " + WhereFilter.Operation.IN.getOp(validAccounts.length) :
        null;
    context.getContentResolver().update(TransactionProvider.ACCOUNTS_URI, values,
        where, validAccounts);
  }

  public static void delete(long id) throws RemoteException, OperationApplicationException {
    Account account = getInstanceFromDb(id);
    if (account == null) {
      return;
    }
    if (account.getSyncAccountName() != null) {
      AccountManager accountManager = AccountManager.get(MyApplication.getInstance());
      android.accounts.Account syncAccount = GenericAccountService.getAccount(account.getSyncAccountName());
      accountManager.setUserData(syncAccount, SyncAdapter.KEY_LAST_SYNCED_LOCAL(account.getId()), null);
      accountManager.setUserData(syncAccount, SyncAdapter.KEY_LAST_SYNCED_REMOTE(account.getId()), null);
    }
    ArrayList<ContentProviderOperation> ops = new ArrayList<>();
    account.updateTransferPeersForTransactionDelete(ops,
        buildTransactionRowSelect(null),
        new String[]{String.valueOf(account.getId())});
    ops.add(ContentProviderOperation.newDelete(
        CONTENT_URI.buildUpon().appendPath(String.valueOf(id)).build())
        .build());
    cr().applyBatch(TransactionProvider.AUTHORITY, ops);
    updateNewAccountEnabled();
    updateTransferShortcut();
  }

  /**
   * returns an empty Account instance
   */
  public Account() {
    this("", (long) 0, "");
  }

  /**
   * Account with currency from locale, of type CASH and with DEFAULT_COLOR
   *
   * @param label          the label
   * @param openingBalance the opening balance
   * @param description    the description
   */
  public Account(String label, long openingBalance, String description) {
    this(label, Utils.getHomeCurrency(), openingBalance, description, AccountType.CASH, DEFAULT_COLOR);
  }

  public Account(String label, CurrencyUnit currencyUnit, long openingBalance, AccountType accountType) {
    this(label, currencyUnit, openingBalance, "", accountType, DEFAULT_COLOR);
  }

  public Account(String label, CurrencyUnit currency, long openingBalance, String description,
                 AccountType type, int color) {
    this(label, currency, new Money(currency, openingBalance), description, type, color);
  }

  public Account(String label, CurrencyUnit currency, long openingBalance, String description, AccountType accountType) {
    this(label, currency, openingBalance, description, accountType, DEFAULT_COLOR);
  }

  public Account(String label, CurrencyUnit currencyUnit, Money openingBalance, String description,
                 AccountType type, int color) {
    this.setLabel(label);
    this.currencyUnit = currencyUnit;
    this.openingBalance = openingBalance;
    this.description = description;
    this.setType(type);
    this.color = color;
  }

  /**
   * @param c Cursor positioned at the row we want to extract into the object
   */
  public Account(Cursor c) {
    extract(c);
  }

  /**
   * extract information from Cursor and populate fields
   *
   * @param c a Cursor retrieved from {@link TransactionProvider#ACCOUNTS_URI}
   */

  protected void extract(Cursor c) {
    final CurrencyContext currencyContext = MyApplication.getInstance().getAppComponent().currencyContext();
    this.setId(c.getLong(c.getColumnIndexOrThrow(KEY_ROWID)));
    this.setLabel(c.getString(c.getColumnIndexOrThrow(KEY_LABEL)));
    this.description = c.getString(c.getColumnIndexOrThrow(KEY_DESCRIPTION));
    this.currencyUnit = currencyContext.get(c.getString(c.getColumnIndexOrThrow(KEY_CURRENCY)));
    this.openingBalance = new Money(this.currencyUnit,
        c.getLong(c.getColumnIndexOrThrow(KEY_OPENING_BALANCE)));
    try {
      this.setType(AccountType.valueOf(c.getString(c.getColumnIndexOrThrow(KEY_TYPE))));
    } catch (IllegalArgumentException ex) {
      this.setType(AccountType.CASH);
    }
    try {
      this.setGrouping(Grouping.valueOf(c.getString(c.getColumnIndexOrThrow(KEY_GROUPING))));
    } catch (IllegalArgumentException ignored) {
    }
    this.color = c.getInt(c.getColumnIndexOrThrow(KEY_COLOR));
    this.excludeFromTotals = c.getInt(c.getColumnIndexOrThrow(KEY_EXCLUDE_FROM_TOTALS)) != 0;
    this.sealed = c.getInt(c.getColumnIndexOrThrow(KEY_SEALED)) != 0;

    this.syncAccountName = c.getString(c.getColumnIndexOrThrow(KEY_SYNC_ACCOUNT_NAME));

    this.setUuid(c.getString(c.getColumnIndexOrThrow(KEY_UUID)));

    try {
      this.sortDirection = SortDirection.valueOf(c.getString(c.getColumnIndexOrThrow(KEY_SORT_DIRECTION)));
    } catch (IllegalArgumentException e) {
      this.sortDirection = SortDirection.DESC;
    }
    int columnIndexExchangeRate = c.getColumnIndex(KEY_EXCHANGE_RATE);
    if (columnIndexExchangeRate != -1) {
      this.exchangeRate = adjustExchangeRate(c.getDouble(columnIndexExchangeRate));
    }
    long criterion = DbUtils.getLongOr0L(c, KEY_CRITERION);
    if (criterion != 0) {
      this.criterion = new Money(this.currencyUnit, criterion);
    }
  }

  public void setCurrency(CurrencyUnit currencyUnit) throws IllegalArgumentException {
    this.currencyUnit = currencyUnit;
    openingBalance = new Money(this.currencyUnit, openingBalance.getAmountMajor());
  }

  /**
   * @return the sum of opening balance and all transactions for the account
   */
  @VisibleForTesting
  public Money getTotalBalance() {
    return new Money(currencyUnit,
        openingBalance.getAmountMinor() + getTransactionSum(null)
    );
  }

  /**
   * @return sum of all transactions
   */
  public long getTransactionSum(WhereFilter filter) {
    String selection = KEY_ACCOUNTID + " = ? AND " + WHERE_NOT_SPLIT_PART + " AND " + WHERE_NOT_VOID;
    String[] selectionArgs = new String[]{String.valueOf(getId())};
    if (filter != null && !filter.isEmpty()) {
      selection += " AND " + filter.getSelectionForParents(DatabaseConstants.VIEW_COMMITTED);
      selectionArgs = Utils.joinArrays(selectionArgs, filter.getSelectionArgs(false));
    }
    Cursor c = cr().query(Transaction.CONTENT_URI,
        new String[]{"sum(" + KEY_AMOUNT + ")"},
        selection,
        selectionArgs,
        null);
    c.moveToFirst();
    long result = c.getLong(0);
    c.close();
    return result;
  }

  public void markAsExported(WhereFilter filter) throws OperationApplicationException, RemoteException {
    ArrayList<ContentProviderOperation> ops = new ArrayList<>();
    Uri accountUri = Account.CONTENT_URI;
    Uri debtUri = Debt.Companion.getCONTENT_URI();
    ops.add(newUpdate(accountUri).withValue(KEY_SEALED, -1)
        .withSelection(KEY_SEALED + " = 1", null).build());
    ops.add(newUpdate(debtUri).withValue(KEY_SEALED, -1)
        .withSelection(KEY_SEALED + " = 1", null).build());
    String selection = KEY_ACCOUNTID + " = ? and " + KEY_PARENTID + " is null";
    String[] selectionArgs = new String[]{String.valueOf(getId())};
    if (filter != null && !filter.isEmpty()) {
      selection += " AND " + filter.getSelectionForParents(DatabaseConstants.TABLE_TRANSACTIONS);
      selectionArgs = Utils.joinArrays(selectionArgs, filter.getSelectionArgs(false));
    }
    ops.add(newUpdate(Transaction.CONTENT_URI)
        .withValue(KEY_STATUS, STATUS_EXPORTED).withSelection(selection, selectionArgs)
        .build());
    ops.add(newUpdate(accountUri).withValue(KEY_SEALED, 1)
        .withSelection(KEY_SEALED + " = -1", null).build());
    ops.add(newUpdate(debtUri).withValue(KEY_SEALED, 1)
        .withSelection(KEY_SEALED + " = -1", null).build());
    cr().applyBatch(TransactionProvider.AUTHORITY, ops);
  }

  public static String buildTransactionRowSelect(WhereFilter filter) {
    String rowSelect = "SELECT " + KEY_ROWID + " from " + TABLE_TRANSACTIONS + " WHERE " + KEY_ACCOUNTID + " = ?";
    if (filter != null && !filter.isEmpty()) {
      rowSelect += " AND " + filter.getSelectionForParents(DatabaseConstants.TABLE_TRANSACTIONS);
    }
    return rowSelect;
  }

  public void updateTransferPeersForTransactionDelete(
      ArrayList<ContentProviderOperation> ops, String rowSelect, String[] selectionArgs) {
    ops.add(newUpdate(Account.CONTENT_URI).withValue(KEY_SEALED, -1).withSelection(KEY_SEALED + " = 1", null).build());
    ContentValues args = new ContentValues();
    args.putNull(KEY_TRANSFER_ACCOUNT);
    args.putNull(KEY_TRANSFER_PEER);
    ops.add(newUpdate(Transaction.CONTENT_URI)
        .withValues(args)
        .withSelection(
            KEY_TRANSFER_PEER + " IN (" + rowSelect + ")",
            selectionArgs)
        .build());
    ops.add(newUpdate(Account.CONTENT_URI).withValue(KEY_SEALED, 1).withSelection(KEY_SEALED + " = -1", null).build());
  }

  /**
   * Saves the account, creating it new if necessary
   *
   * @return the id of the account. Upon creation it is returned from the database
   */
  public Uri save() {
    Uri uri;
    ensureCurrency(currencyUnit);
    ContentValues initialValues = new ContentValues();
    initialValues.put(KEY_LABEL, getLabel());
    initialValues.put(KEY_OPENING_BALANCE, openingBalance.getAmountMinor());
    initialValues.put(KEY_DESCRIPTION, description);
    initialValues.put(KEY_CURRENCY, currencyUnit.getCode());
    initialValues.put(KEY_TYPE, getType().name());
    initialValues.put(KEY_GROUPING, getGrouping().name());
    initialValues.put(KEY_COLOR, color);
    initialValues.put(KEY_SYNC_ACCOUNT_NAME, syncAccountName);
    initialValues.put(KEY_UUID, requireUuid());
    initialValues.put(KEY_EXCLUDE_FROM_TOTALS, excludeFromTotals);
    if (criterion != null) {
      initialValues.put(KEY_CRITERION, criterion.getAmountMinor());
    } else {
      initialValues.putNull(KEY_CRITERION);
    }

    if (getId() == 0) {
      uri = cr().insert(CONTENT_URI, initialValues);
      if (uri == null) {
        return null;
      }
      setId(ContentUris.parseId(uri));
    } else {
      uri = ContentUris.withAppendedId(CONTENT_URI, getId());
      if (cr().update(uri, initialValues, null, null) == 0) return null;
    }
    if (hasForeignCurrency()) {
      storeExchangeRate();
    }
    updateNewAccountEnabled();
    updateTransferShortcut();
    return uri;
  }

  private void ensureCurrency(CurrencyUnit currencyUnit) {
    Cursor cursor = cr().query(TransactionProvider.CURRENCIES_URI, new String[]{"count(*)"},
        KEY_CODE + " = ?", new String[]{currencyUnit.getCode()}, null);
    if (cursor == null) {
      throw new IllegalStateException("Unable to ensure currency (" + currencyUnit + "). Cursor is null");
    }
    cursor.moveToFirst();
    int result = cursor.getInt(0);
    cursor.close();
    switch (result) {
      case 0: {
        ContentValues contentValues = new ContentValues(2);
        contentValues.put(KEY_LABEL, currencyUnit.getCode());
        contentValues.put(KEY_CODE, currencyUnit.getCode());
        if (cr().insert(TransactionProvider.CURRENCIES_URI, contentValues) == null) {
          throw new IllegalStateException("Unable to ensure currency (" + currencyUnit + "). Insert failed");
        }
      }
      case 1: return;
      default: throw new IllegalStateException("Unable to ensure currency (" + currencyUnit + "). Inconsistent query result");
    }
  }

  public static int count(String selection, String[] selectionArgs) {
    Cursor cursor = cr().query(CONTENT_URI, new String[]{"count(*)"},
        selection, selectionArgs, null);
    if (cursor.getCount() == 0) {
      cursor.close();
      return 0;
    } else {
      cursor.moveToFirst();
      int result = cursor.getInt(0);
      cursor.close();
      return result;
    }
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    Account other = (Account) obj;
    if (color != other.color)
      return false;
    if (currencyUnit == null) {
      if (other.currencyUnit != null)
        return false;
    } else if (!currencyUnit.equals(other.currencyUnit))
      return false;
    if (description == null) {
      if (other.description != null)
        return false;
    } else if (!description.equals(other.description))
      return false;
    if (getId() != other.getId())
      return false;
    if (getLabel() == null) {
      if (other.getLabel() != null)
        return false;
    } else if (!getLabel().equals(other.getLabel()))
      return false;
    if (openingBalance == null) {
      if (other.openingBalance != null)
        return false;
    } else if (!openingBalance.equals(other.openingBalance))
      return false;
    if (criterion == null) {
      if (other.criterion != null)
        return false;
    } else if (!criterion.equals(other.criterion))
      return false;
    if (getType() != other.getType())
      return false;
    return true;
  }

  @Override
  public int hashCode() {
    int result = this.getLabel() != null ? this.getLabel().hashCode() : 0;
    result = 31 * result + (this.openingBalance != null ? this.openingBalance.hashCode() : 0);
    result = 31 * result + (this.currencyUnit != null ? this.currencyUnit.hashCode() : 0);
    result = 31 * result + (this.description != null ? this.description.hashCode() : 0);
    result = 31 * result + this.color;
    result = 31 * result + (this.excludeFromTotals ? 1 : 0);
    result = 31 * result + (this.getType() != null ? this.getType().hashCode() : 0);
    result = 31 * result + (this.getGrouping() != null ? this.getGrouping().hashCode() : 0);
    return result;
  }

  /**
   * Returns the first account which uses the passed in currency, order is undefined
   *
   * @param currency ISO 4217 currency code
   * @return id or -1 if not found
   */
  public static long findAnyByCurrency(String currency) {
    String selection = KEY_CURRENCY + " = ?";
    String[] selectionArgs = new String[]{currency};

    Cursor mCursor = cr().query(CONTENT_URI,
        new String[]{KEY_ROWID}, selection, selectionArgs, null);
    if (mCursor.getCount() == 0) {
      mCursor.close();
      return -1;
    } else {
      mCursor.moveToFirst();
      long result = mCursor.getLong(0);
      mCursor.close();
      return result;
    }
  }

  /**
   * Looks for an account with a label, that is not sealed. WARNING: If several accounts have the same label, this
   * method fill return the first account retrieved in the cursor, order is undefined
   *
   * @param label label of the account we want to retrieve
   * @return id or -1 if not found
   */
  public static long findAnyOpen(String label) {
    String selection = KEY_LABEL + " = ? AND  " + KEY_SEALED + " = 0";
    String[] selectionArgs = new String[]{label};

    Cursor mCursor = cr().query(CONTENT_URI,
        new String[]{KEY_ROWID}, selection, selectionArgs, null);
    if (mCursor.getCount() == 0) {
      mCursor.close();
      return -1;
    } else {
      mCursor.moveToFirst();
      long result = mCursor.getLong(0);
      mCursor.close();
      return result;
    }
  }

  public static long findByUuid(String uuid) {
    String selection = KEY_UUID + " = ?";
    String[] selectionArgs = new String[]{uuid};

    Cursor mCursor = cr().query(CONTENT_URI,
        new String[]{KEY_ROWID}, selection, selectionArgs, null);
    if (mCursor.getCount() == 0) {
      mCursor.close();
      return -1;
    } else {
      mCursor.moveToFirst();
      long result = mCursor.getLong(0);
      mCursor.close();
      return result;
    }
  }


  /**
   * return an Account or AggregateAccount that matches the one found in the cursor at the row it is
   * positioned at. Either the one found in the cache is returned or it is extracted from the cursor
   */
  public static Account fromCursor(Cursor cursor) {
    long accountId = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_ROWID));
    if (accountId < 0) {
      return new AggregateAccount(cursor);
    } else {
      return new Account(cursor);
    }
  }

  public void requestSync() {
    if (syncAccountName != null) {
      GenericAccountService.requestSync(syncAccountName,getUuid());
    }
  }

  public static void updateNewAccountEnabled() {
    boolean newAccountEnabled = true;
    final AppComponent appComponent = MyApplication.getInstance().getAppComponent();
    LicenceHandler licenceHandler = appComponent.licenceHandler();
    PrefHandler prefHandler = appComponent.prefHandler();
    if (!licenceHandler.hasAccessTo(ContribFeature.ACCOUNTS_UNLIMITED)) {
      if (count(null, null) >= ContribFeature.FREE_ACCOUNTS) {
        newAccountEnabled = false;
      }
    }
    prefHandler.putBoolean(PrefKey.NEW_ACCOUNT_ENABLED, newAccountEnabled);
  }

  public static void updateTransferShortcut() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
      ShortcutHelper.configureTransferShortcut(MyApplication.getInstance(), count(null, null) > 1);
    }
  }

  /**
   * @param withType true means, that the query is for either positive (income) or negative (expense) transactions
   *                 in that case, the merge transfer restriction must be skipped, since it is based on only
   *                 selecting the negative part of a transfer
   */
  public Uri getExtendedUriForTransactionList(boolean withType, boolean shortenComment) {
    return extendedUriForTransactionList(shortenComment);
  }

  public static Uri extendedUriForTransactionList(boolean shortenComment) {
    return shortenComment ? Transaction.EXTENDED_URI
            .buildUpon()
            .appendQueryParameter(TransactionProvider.QUERY_PARAMETER_SHORTEN_COMMENT, "1")
            .build() : Transaction.EXTENDED_URI;
  }

  public boolean isHomeAggregate() {
    return isHomeAggregate(getId());
  }

  public static boolean isHomeAggregate(long id) {
    return id == HOME_AGGREGATE_ID;
  }

  public boolean isAggregate() {
    return isAggregate(getId());
  }

  public static boolean isAggregate(long id) {
    return id < 0;
  }

  public String[] getExtendedProjectionForTransactionList() {
    return Transaction.PROJECTION_EXTENDED;
  }

  public String getSelectionForTransactionList() {
    return KEY_ACCOUNTID + " = ?";
  }

  public String[] getSelectionArgsForTransactionList() {
    return new String[]{String.valueOf(getId())};
  }

  final protected Uri.Builder getGroupingBaseUri(Grouping grouping) {
    return Transaction.CONTENT_URI.buildUpon().appendPath(TransactionProvider.URI_SEGMENT_GROUPS).appendPath(grouping.name());
  }

  public Uri.Builder getGroupingUri() {
    return getGroupingUri(grouping);
  }

  public Uri.Builder getGroupingUri(Grouping grouping) {
    return getGroupingBaseUri(grouping).appendQueryParameter(KEY_ACCOUNTID, String.valueOf(getId()));
  }

  public AccountType getType() {
    return type;
  }

  public void setType(AccountType type) {
    this.type = type;
  }

  @NonNull
  public Grouping getGrouping() {
    return grouping;
  }

  public void setGrouping(@NonNull Grouping grouping) {
    this.grouping = grouping;
  }

  public SortDirection getSortDirection() {
    return sortDirection;
  }

  protected void setSortDirection(SortDirection sortDirection) {
    this.sortDirection = sortDirection;
  }

  public String getLabel() {
    return label;
  }

  public String getLabelForScreenTitle(Context context) {
    return label;
  }

  public void setLabel(String label) {
    this.label = StringUtils.strip(label);
  }

  public void setCriterion(BigDecimal criterion) {
    if (criterion.compareTo(BigDecimal.ZERO) != 0) {
      this.criterion = new Money(currencyUnit, criterion);
    } else {
      this.criterion = null;
    }
  }

  public void setCriterion(Money criterion) {
    this.criterion = criterion;
  }

  @Nullable
  public Money getCriterion() {
    return criterion;
  }

  public boolean isSealed() {
    return sealed;
  }

  @NonNull
  @Override
  public CurrencyUnit getCurrency() {
    return currencyUnit;
  }

  @Override
  public int getColor() {
    return -1;
  }

  @NonNull
  @Override
  public String label(@NonNull Context context) {
    return label;
  }

  @Override
  public long getAccountId() {
    return getId();
  }
}
