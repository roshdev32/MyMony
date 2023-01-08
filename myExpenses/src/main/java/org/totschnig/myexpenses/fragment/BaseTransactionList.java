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

package org.totschnig.myexpenses.fragment;

import static org.totschnig.myexpenses.activity.ConstantsKt.EDIT_REQUEST;
import static org.totschnig.myexpenses.activity.ConstantsKt.FILTER_CATEGORY_REQUEST;
import static org.totschnig.myexpenses.activity.ConstantsKt.FILTER_PAYEE_REQUEST;
import static org.totschnig.myexpenses.activity.ConstantsKt.FILTER_TAGS_REQUEST;
import static org.totschnig.myexpenses.activity.ConstantsKt.MAP_ACCOUNT_REQUEST;
import static org.totschnig.myexpenses.activity.ConstantsKt.MAP_CATEGORY_REQUEST;
import static org.totschnig.myexpenses.activity.ConstantsKt.MAP_METHOD_REQUEST;
import static org.totschnig.myexpenses.activity.ConstantsKt.MAP_PAYEE_REQUEST;
import static org.totschnig.myexpenses.activity.ProtectedFragmentActivity.PROGRESS_TAG;
import static org.totschnig.myexpenses.dialog.ConfirmationDialogFragment.KEY_COMMAND_POSITIVE;
import static org.totschnig.myexpenses.dialog.ConfirmationDialogFragment.KEY_MESSAGE;
import static org.totschnig.myexpenses.dialog.ConfirmationDialogFragment.KEY_POSITIVE_BUTTON_LABEL;
import static org.totschnig.myexpenses.dialog.ConfirmationDialogFragment.KEY_TITLE_STRING;
import static org.totschnig.myexpenses.fragment.TagListKt.KEY_TAG_LIST;
import static org.totschnig.myexpenses.preference.PrefKey.NEW_SPLIT_TEMPLATE_ENABLED;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_ACCOUNTID;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_AMOUNT;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_CATID;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_CR_STATUS;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_CURRENCY;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_DATE;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_DAY;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_HAS_TRANSFERS;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_LABEL;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_MAPPED_CATEGORIES;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_MAPPED_METHODS;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_MAPPED_PAYEES;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_MAPPED_TAGS;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_METHODID;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_MONTH;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_PARENTID;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_PAYEEID;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_PAYEE_NAME;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_ROWID;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_SECOND_GROUP;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_SUM_EXPENSES;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_SUM_INCOME;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_SUM_TRANSFERS;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_TRANSFER_ACCOUNT;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_TRANSFER_PEER_PARENT;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_WEEK;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_YEAR;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_YEAR_OF_MONTH_START;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_YEAR_OF_WEEK_START;
import static org.totschnig.myexpenses.provider.DatabaseConstants.SPLIT_CATID;
import static org.totschnig.myexpenses.provider.filter.ConstantsKt.NULL_ITEM_ID;
import static org.totschnig.myexpenses.util.ColorUtils.getComplementColor;
import static org.totschnig.myexpenses.util.CurrencyFormatterKt.convAmount;
import static org.totschnig.myexpenses.util.DateUtilsKt.localDateTime2Epoch;
import static org.totschnig.myexpenses.util.MoreUiUtilsKt.addChipsBulk;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.net.Uri.Builder;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.InputType;
import android.text.TextUtils;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.LongSparseArray;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;
import androidx.viewbinding.ViewBinding;

import com.annimon.stream.Collectors;
import com.annimon.stream.IntStream;
import com.annimon.stream.Stream;
import com.github.lzyzsd.circleprogress.DonutProgress;

import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.activity.ExpenseEdit;
import org.totschnig.myexpenses.activity.MyExpenses;
import org.totschnig.myexpenses.activity.ProtectedFragmentActivity;
import org.totschnig.myexpenses.adapter.TransactionAdapter;
import org.totschnig.myexpenses.databinding.ExpensesListBinding;
import org.totschnig.myexpenses.databinding.HeaderBinding;
import org.totschnig.myexpenses.databinding.HeaderWithBudgetBinding;
import org.totschnig.myexpenses.di.AppComponent;
import org.totschnig.myexpenses.dialog.ConfirmationDialogFragment;
import org.totschnig.myexpenses.dialog.ProgressDialogFragment;
import org.totschnig.myexpenses.model.Account;
import org.totschnig.myexpenses.model.AccountType;
import org.totschnig.myexpenses.model.ContribFeature;
import org.totschnig.myexpenses.model.CrStatus;
import org.totschnig.myexpenses.model.CurrencyContext;
import org.totschnig.myexpenses.model.Grouping;
import org.totschnig.myexpenses.model.SortDirection;
import org.totschnig.myexpenses.model.Template;
import org.totschnig.myexpenses.model.Transaction;
import org.totschnig.myexpenses.model.Transfer;
import org.totschnig.myexpenses.preference.PrefHandler;
import org.totschnig.myexpenses.preference.PrefKey;
import org.totschnig.myexpenses.preference.PreferenceUtilsKt;
import org.totschnig.myexpenses.provider.DatabaseConstants;
import org.totschnig.myexpenses.provider.DbUtils;
import org.totschnig.myexpenses.provider.ProtectedCursorLoader;
import org.totschnig.myexpenses.provider.TransactionProvider;
import org.totschnig.myexpenses.provider.filter.CategoryCriteria;
import org.totschnig.myexpenses.provider.filter.CommentCriteria;
import org.totschnig.myexpenses.provider.filter.Criteria;
import org.totschnig.myexpenses.provider.filter.FilterPersistence;
import org.totschnig.myexpenses.provider.filter.PayeeCriteria;
import org.totschnig.myexpenses.provider.filter.TagCriteria;
import org.totschnig.myexpenses.provider.filter.WhereFilter;
import org.totschnig.myexpenses.ui.ExpansionHandle;
import org.totschnig.myexpenses.util.CurrencyFormatter;
import org.totschnig.myexpenses.util.MenuUtilsKt;
import org.totschnig.myexpenses.util.UiUtils;
import org.totschnig.myexpenses.util.Utils;
import org.totschnig.myexpenses.util.crashreporting.CrashHandler;
import org.totschnig.myexpenses.util.licence.LicenceHandler;
import org.totschnig.myexpenses.util.locale.UserLocaleProvider;
import org.totschnig.myexpenses.viewmodel.TransactionListViewModel;
import org.totschnig.myexpenses.viewmodel.data.DateInfo;
import org.totschnig.myexpenses.viewmodel.data.Tag;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import eltos.simpledialogfragment.SimpleDialog;
import eltos.simpledialogfragment.input.SimpleInputDialog;
import icepick.Icepick;
import icepick.State;
import kotlin.Pair;
import kotlin.Triple;
import se.emilsjolander.stickylistheaders.SectionIndexingStickyListHeadersAdapter;
import se.emilsjolander.stickylistheaders.StickyListHeadersListView;
import se.emilsjolander.stickylistheaders.StickyListHeadersListView.OnHeaderClickListener;
import timber.log.Timber;

public abstract class BaseTransactionList extends ContextualActionBarFragment implements
    LoaderManager.LoaderCallbacks<Cursor>, OnHeaderClickListener, SimpleDialog.OnDialogResultListener,
    SharedPreferences.OnSharedPreferenceChangeListener {

  public static final String NEW_TEMPLATE_DIALOG = "dialogNewTempl";
  public static final String FILTER_COMMENT_DIALOG = "dialogFilterCom";
  public static final String REMAP_DIALOG = "dialogRemap";
  public static final String KEY_COLUMN = "column";

  protected int getMenuResource() {
    return R.menu.transactionlist_context;
  }

  private static final int TRANSACTION_CURSOR = 0;
  private static final int SUM_CURSOR = 1;
  private static final int GROUPING_CURSOR = 2;
  private static final int SECTION_CURSOR = 3;

  public static final String KEY_FILTER = "filter";
  public static final String COMMENT_SEPARATOR = " / ";
  private MyGroupedAdapter mAdapter;
  protected boolean hasItems;
  private boolean mappedCategories;
  private boolean mappedPayees;
  private boolean mappedMethods;
  private boolean hasTransfers;
  private boolean hasTags;
  private boolean firstLoadCompleted;
  protected Cursor mTransactionsCursor;
  private Parcelable listState;

  private LoaderManager mManager;
  protected ExpensesListBinding binding;

  /**
   * maps header to an array that holds an array of following sums:
   * [0] incomeSum
   * [1] expenseSum
   * [2] transferSum
   * [3] previousBalance
   * [4] delta (incomSum - expenseSum + transferSum)
   * [5] interimBalance
   * [6] mappedCategories
   */
  protected final LongSparseArray<Long[]> headerData = new LongSparseArray<>();
  private String[] sections;
  private int[] sectionIds;
  /**
   * maps section to index to the position of first item in section
   */
  private SparseIntArray mSectionCache;

  /**
   * used to restore list selection when drawer is reopened
   */
  private SparseBooleanArray mCheckedListItems;

  protected int columnIndexYear;
  protected int columnIndexYearOfWeekStart;
  protected int columnIndexMonth;
  protected int columnIndexWeek;
  protected int columnIndexDay;
  protected int columnIndexPayee;
  protected int columnIndexCrStatus;
  protected int columnIndexYearOfMonthStart;
  protected int columnIndexLabel;
  protected int columnIndexAccountId;
  protected int columnIndexAmount;
  protected int columnIndexCurrency;
  private boolean indexesCalculated = false;
  protected Account mAccount;
  @Nullable
  protected List<Triple<Integer, Long, Boolean>> budgetAmounts = null;
  protected TransactionListViewModel viewModel;

  @Inject
  CurrencyFormatter currencyFormatter;
  @Inject
  PrefHandler prefHandler;
  @Inject
  CurrencyContext currencyContext;
  @Inject
  UserLocaleProvider userLocaleProvider;
  @Inject
  LicenceHandler licenceHandler;
  @Inject
  SharedPreferences settings;
  FilterPersistence filterPersistence;

  @State
  boolean shouldStartActionMode;

  public static Fragment newInstance(long accountId) {
    TransactionList pageFragment = new TransactionList();
    Bundle bundle = new Bundle();
    bundle.putLong(KEY_ACCOUNTID, accountId);
    pageFragment.setArguments(bundle);
    return pageFragment;
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Icepick.restoreInstanceState(this, savedInstanceState);
    setHasOptionsMenu(true);
    viewModel = new ViewModelProvider(this).get(TransactionListViewModel.class);
    final AppComponent appComponent = ((MyApplication) requireActivity().getApplication()).getAppComponent();
    appComponent.inject(this);
    appComponent.inject(viewModel);
    viewModel.account(getArguments().getLong(KEY_ACCOUNTID), false).observe(this, account -> {
      mAccount = account;
      shouldStartActionMode = mAccount != null && (mAccount.isAggregate() || !mAccount.isSealed());
      mAdapter.setAccount(mAccount);
      setGrouping();
      Utils.requireLoader(mManager, TRANSACTION_CURSOR, null, BaseTransactionList.this);
      Utils.requireLoader(mManager, SUM_CURSOR, null, BaseTransactionList.this);
      Utils.requireLoader(mManager, SECTION_CURSOR, null, BaseTransactionList.this);
    });
    viewModel.getBudgetAmount().observe(this, budget -> {
      this.budgetAmounts = budget;
      refresh(false);
    });
    viewModel.getCloneAndRemapProgress().observe(this, result -> {
      ProtectedFragmentActivity context = (ProtectedFragmentActivity) getActivity();
      if (context == null) return;
      ProgressDialogFragment progressDialog = ((ProgressDialogFragment) getParentFragmentManager().findFragmentByTag(PROGRESS_TAG));
      int totalProcessed = result.getFirst() + result.getSecond();
      if (progressDialog != null) {
        if (totalProcessed < progressDialog.getMax()) {
          progressDialog.setProgress(totalProcessed);
        } else {
          if (result.getSecond() == 0) {
            context.showSnackBar(R.string.clone_and_remap_result);
          } else {
            context.showSnackBar(String.format(Locale.ROOT, "%d out of %d failed", result.getSecond(), totalProcessed));
          }
          getParentFragmentManager().beginTransaction().remove(progressDialog).commit();
        }
      }
    });
    firstLoadCompleted = (savedInstanceState != null);
  }

  private void setGrouping() {
    mAdapter.refreshDateFormat();
    if (mAccount.getGrouping() != Grouping.NONE) {
      binding.list.setCollapsedHeaderIds(PreferenceUtilsKt.getLongList(prefHandler, collapsedHeaderIdsPrefKey()));
    }
    restartGroupingLoader();
  }

  private void restartGroupingLoader() {
    Utils.requireLoader(mManager, GROUPING_CURSOR, null, this);
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    mManager = LoaderManager.getInstance(this);
    filterPersistence = new FilterPersistence(prefHandler, prefNameForCriteria(), savedInstanceState, true, true);
    binding = ExpensesListBinding.inflate(inflater, container, false);
    if (mAdapter == null) {
      mAdapter = new MyGroupedAdapter(getActivity(), R.layout.expense_row, null, 0);
    }
    configureListView();
    registerForContextualActionBar(binding.list.getWrappedList());
    configureFilterCard();
    settings.registerOnSharedPreferenceChangeListener(this);
    return binding.getRoot();
  }

  @Override
  protected boolean shouldStartActionMode() {
    return shouldStartActionMode;
  }

  private void configureListView() {
    binding.list.setOnHeaderClickListener(this);
    binding.list.setDrawingListUnderStickyHeader(false);

    binding.list.setEmptyView(binding.empty);
    binding.list.setOnItemClickListener((a, v1, position, id) -> showDetails(id));
    binding.list.setOnScrollListener(new AbsListView.OnScrollListener() {
      private int currentState = 0;

      @Override
      public void onScrollStateChanged(AbsListView view, int scrollState) {
        if (scrollState == SCROLL_STATE_IDLE && currentState != scrollState && view.isFastScrollEnabled()) {
          view.postDelayed(() -> {
            if (currentState == SCROLL_STATE_IDLE) view.setFastScrollEnabled(false);
          }, 1000);
        }
        currentState = scrollState;
      }

      @Override
      public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        if (currentState == SCROLL_STATE_TOUCH_SCROLL) {

          if (!view.isFastScrollEnabled())
            view.setFastScrollEnabled(true);
        }
      }
    });
    binding.list.addFooterView(LayoutInflater.from(getActivity()).inflate(R.layout.group_divider, binding.list.getWrappedList(), false), null, false);
    binding.list.setAdapter(mAdapter);
  }

  protected abstract void showDetails(long id);

  protected abstract void showConfirmationDialog(Bundle bundle, String tag);

  protected void refresh(boolean invalidateMenu) {
    if (mAccount != null) { //if we are refreshed from onActivityResult, it might happen, that mAccount is not yet set (report 5c1754c8f8b88c29631ef140)
      mManager.restartLoader(TRANSACTION_CURSOR, null, this);
      mManager.restartLoader(GROUPING_CURSOR, null, this);
    }
    if (invalidateMenu) {
      requireActivity().invalidateOptionsMenu();
      configureFilterCard();
    }
  }

  @Override
  public void onDestroyView() {
    listState = binding.list.getWrappedList().onSaveInstanceState();
    settings.unregisterOnSharedPreferenceChangeListener(this);
    binding = null;
    super.onDestroyView();
  }

  abstract void checkSealed(long[] itemIds, Runnable onChecked);

  @Override
  public boolean dispatchCommandSingle(int command, ContextMenu.ContextMenuInfo info) {
    if (super.dispatchCommandSingle(command, info)) {
      return true;
    }
    AdapterContextMenuInfo acmi = (AdapterContextMenuInfo) info;
    MyExpenses ctx = (MyExpenses) requireActivity();
    mTransactionsCursor.moveToPosition(acmi.position);
    if (command == R.id.EDIT_COMMAND || command == R.id.CLONE_TRANSACTION_COMMAND) {
      final boolean isTransferPartPeer = DbUtils.getLongOrNull(mTransactionsCursor, KEY_TRANSFER_PEER_PARENT) != null;
      checkSealed(new long[]{acmi.id}, () -> {
        if (isTransferPartPeer) {
          ctx.showSnackBar(R.string.warning_splitpartcategory_context);
        } else {
          Intent i = new Intent(ctx, ExpenseEdit.class);
          i.putExtra(KEY_ROWID, acmi.id);
          if (command == R.id.CLONE_TRANSACTION_COMMAND) {
            i.putExtra(ExpenseEdit.KEY_CLONE, true);
          }
          ctx.startActivityForResult(i, EDIT_REQUEST);
        }
      });
      finishActionMode();
      return true;
    } else if (command == R.id.CREATE_TEMPLATE_COMMAND) {
      final boolean splitAtPosition = isSplitAtPosition(acmi.position);
      String label = mTransactionsCursor.getString(columnIndexPayee);
      if (TextUtils.isEmpty(label))
        label = mTransactionsCursor.getString(columnIndexLabel);
      String finalLabel = label;
      checkSealed(new long[]{acmi.id}, () -> {
        if (splitAtPosition && !prefHandler.getBoolean(NEW_SPLIT_TEMPLATE_ENABLED, true)) {
          ctx.showContribDialog(ContribFeature.SPLIT_TEMPLATE, null);
        } else {
          Bundle args = new Bundle();
          args.putLong(KEY_ROWID, acmi.id);
          SimpleInputDialog.build()
              .title(R.string.menu_create_template)
              .cancelable(false)
              .inputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
              .hint(R.string.title)
              .text(finalLabel)
              .extra(args)
              .pos(R.string.dialog_button_add)
              .neut()
              .show(this, NEW_TEMPLATE_DIALOG);
        }
      });
      return true;
    }
    return false;
  }

  private WhereFilter getFilter() {
    return filterPersistence.getWhereFilter();
  }

  @NonNull
  @Override
  public Loader<Cursor> onCreateLoader(int id, Bundle arg1) {
    CursorLoader cursorLoader;
    String selection = mAccount.getSelectionForTransactionList();
    String[] selectionArgs = mAccount.getSelectionArgsForTransactionList();
    switch (id) {
      case TRANSACTION_CURSOR:
        if (!getFilter().isEmpty()) {
          String selectionForParents = getFilter().getSelectionForParents(DatabaseConstants.VIEW_EXTENDED);
          if (!selectionForParents.equals("")) {
            if (!TextUtils.isEmpty(selection)) {
              selection += " AND ";
            }
            selection += selectionForParents;
            selectionArgs = Utils.joinArrays(selectionArgs, getFilter().getSelectionArgs(false));
          }
        }
        if (!TextUtils.isEmpty(selection)) {
          selection += " AND ";
        }
        selection += KEY_PARENTID + " is null";
        cursorLoader = new ProtectedCursorLoader(requireActivity(),
            mAccount.getExtendedUriForTransactionList(false, true),
            mAccount.getExtendedProjectionForTransactionList(),
            selection,
            selectionArgs, KEY_DATE + " " + mAccount.getSortDirection().name());
        break;
      case SUM_CURSOR:
        cursorLoader = new ProtectedCursorLoader(requireActivity(),
            TransactionProvider.TRANSACTIONS_URI.buildUpon().appendQueryParameter(TransactionProvider.QUERY_PARAMETER_MAPPED_OBJECTS, "1").build(),
            null,
            selection,
            selectionArgs, null);
        break;
      case GROUPING_CURSOR:
      case SECTION_CURSOR:
        selection = null;
        selectionArgs = null;
        Builder builder = mAccount.getGroupingUri(id == GROUPING_CURSOR ? mAccount.getGrouping() : Grouping.MONTH);
        if (!getFilter().isEmpty()) {
          selection = getFilter().getSelectionForParts(DatabaseConstants.VIEW_WITH_ACCOUNT);
          if (!selection.equals("")) {
            selectionArgs = getFilter().getSelectionArgs(true);
          }
        }
        String sortOrder = null;
        if (id == SECTION_CURSOR) {
          builder.appendQueryParameter(TransactionProvider.QUERY_PARAMETER_SECTIONS, "1");
          sortOrder = String.format("%1$s %3$s,%2$s %3$s",
              KEY_YEAR, KEY_SECOND_GROUP, mAccount.getSortDirection().name());
        }
        cursorLoader = new ProtectedCursorLoader(getActivity(),
            builder.build(),
            null, selection, selectionArgs, sortOrder);
        break;
      default: throw new IllegalStateException("No loader defined for id " + id);
    }
    return cursorLoader;
  }

  @Override
  public void onLoadFinished(@NonNull Loader<Cursor> arg0, Cursor c) {
    final int count = c == null ? 0 : c.getCount();
    switch (arg0.getId()) {
      case TRANSACTION_CURSOR:
        mTransactionsCursor = c;
        hasItems = count > 0;
        if (!indexesCalculated && c != null) {
          columnIndexYear = c.getColumnIndex(KEY_YEAR);
          columnIndexYearOfWeekStart = c.getColumnIndex(KEY_YEAR_OF_WEEK_START);
          columnIndexYearOfMonthStart = c.getColumnIndex(KEY_YEAR_OF_MONTH_START);
          columnIndexMonth = c.getColumnIndex(KEY_MONTH);
          columnIndexWeek = c.getColumnIndex(KEY_WEEK);
          columnIndexDay = c.getColumnIndex(KEY_DAY);
          columnIndexLabel = c.getColumnIndex(KEY_LABEL);
          columnIndexPayee = c.getColumnIndex(KEY_PAYEE_NAME);
          columnIndexCrStatus = c.getColumnIndex(KEY_CR_STATUS);
          columnIndexAccountId = c.getColumnIndex(KEY_ACCOUNTID);
          columnIndexAmount = c.getColumnIndex(KEY_AMOUNT);
          columnIndexCurrency = c.getColumnIndex(KEY_CURRENCY);
          indexesCalculated = true;
        }
        mAdapter.swapCursor(c);
        if (count > 0) {
          if (firstLoadCompleted) {
            binding.list.post(() -> {
              if (listState != null)
                if (binding != null) {
                  binding.list.getWrappedList().onRestoreInstanceState(listState);
                }
              listState = null;
            });
          } else {
            firstLoadCompleted = true;
            if (prefHandler.getBoolean(PrefKey.SCROLL_TO_CURRENT_DATE, false)) {
              final int currentPosition = findCurrentPosition(c);
              binding.list.post(() -> {
                if (binding != null) {
                  binding.list.setSelection(currentPosition);
                }
              });
            }
          }
        }
        invalidateCAB();
        break;
      case SUM_CURSOR:
        if (c != null) {
          c.moveToFirst();
          mappedCategories = c.getInt(c.getColumnIndexOrThrow(KEY_MAPPED_CATEGORIES)) > 0;
          mappedPayees = c.getInt(c.getColumnIndexOrThrow(KEY_MAPPED_PAYEES)) > 0;
          mappedMethods = c.getInt(c.getColumnIndexOrThrow(KEY_MAPPED_METHODS)) > 0;
          hasTransfers = c.getInt(c.getColumnIndexOrThrow(KEY_HAS_TRANSFERS)) > 0;
          hasTags = c.getInt(c.getColumnIndexOrThrow(KEY_MAPPED_TAGS)) > 0;
          requireActivity().invalidateOptionsMenu();
        }
        break;
      case GROUPING_CURSOR:
        headerData.clear();
        if (c != null) {
          int columnIndexGroupYear = c.getColumnIndex(KEY_YEAR);
          int columnIndexGroupSecond = c.getColumnIndex(KEY_SECOND_GROUP);
          int columnIndexGroupSumIncome = c.getColumnIndex(KEY_SUM_INCOME);
          int columnIndexGroupSumExpense = c.getColumnIndex(KEY_SUM_EXPENSES);
          int columnIndexGroupSumTransfer = c.getColumnIndex(KEY_SUM_TRANSFERS);
          int columnIndexGroupMappedCategories = c.getColumnIndex(KEY_MAPPED_CATEGORIES);

          if (c.moveToFirst()) {
            long previousBalance = mAccount.openingBalance.getAmountMinor();
            do {
              long sumIncome = c.getLong(columnIndexGroupSumIncome);
              long sumExpense = c.getLong(columnIndexGroupSumExpense);
              long sumTransfer = c.getLong(columnIndexGroupSumTransfer);
              long delta = sumIncome + sumExpense + sumTransfer;
              long interimBalance = previousBalance + delta;
              long mappedCategories = c.getLong(columnIndexGroupMappedCategories);
              headerData.put(calculateHeaderId(c.getInt(columnIndexGroupYear), c.getInt(columnIndexGroupSecond)),
                      new Long[]{sumIncome, sumExpense, sumTransfer, previousBalance, delta, interimBalance, mappedCategories});
              previousBalance = interimBalance;
            } while (c.moveToNext());
          }
        }
        //if the transactionsCursor has been loaded before the grouping cursor, we need to refresh
        //in order to have accurate grouping values
        if (mTransactionsCursor != null)
          mAdapter.notifyDataSetChanged();
        break;
      case SECTION_CURSOR:
        sections = new String[count];
        sectionIds = new int[count];
        mSectionCache = new SparseIntArray(count);
        if (c != null && c.moveToFirst()) {
          final Calendar cal = Calendar.getInstance();
          final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM yy", userLocaleProvider.getUserPreferredLocale());
          do {
            final int year = c.getInt(c.getColumnIndexOrThrow(KEY_YEAR));
            final int month = c.getInt(c.getColumnIndexOrThrow(KEY_SECOND_GROUP));
            cal.set(year, month, 1);
            final int position = c.getPosition();
            sections[position] = dateFormat.format(cal.getTime());
            sectionIds[position] = calculateHeaderId(year, month, Grouping.MONTH);
          } while (c.moveToNext());
        }
    }
  }

  private int findCurrentPosition(Cursor c) {
    int dateColumn = c.getColumnIndex(KEY_DATE);
    switch (mAccount.getSortDirection()) {
      case ASC:
        long startOfToday = localDateTime2Epoch(LocalDateTime.now().truncatedTo(ChronoUnit.DAYS));
        if (c.moveToLast()) {
          do {
            if (c.getLong(dateColumn) <= startOfToday) {
              return c.isLast() ? c.getPosition() : c.getPosition() + 1;
            }
          } while (c.moveToPrevious());
        }
        break;
      case DESC:
        long endOfDay = localDateTime2Epoch(LocalDateTime.now().truncatedTo(ChronoUnit.DAYS).plusDays(1));
        if (c.moveToFirst()) {
          do {
            if (c.getLong(dateColumn) < endOfDay) {
              return c.getPosition();
            }
          } while (c.moveToNext());
        }
    }
    return 0;
  }


  private int calculateHeaderId(int year, int second, Grouping grouping) {
    if (grouping.equals(Grouping.NONE)) {
      return 1;
    }
    return viewModel.calculateGroupId(year, second);
  }

  private int calculateHeaderId(int year, int second) {
    return calculateHeaderId(year, second, mAccount.getGrouping());
  }

  @Override
  public void onLoaderReset(@NonNull Loader<Cursor> arg0) {
    switch (arg0.getId()) {
      case TRANSACTION_CURSOR:
        mTransactionsCursor = null;
        mAdapter.swapCursor(null);
        hasItems = false;
        break;
      case SUM_CURSOR:
        mappedCategories = false;
        mappedPayees = false;
        mappedMethods = false;
        break;
    }
  }

  public boolean isFiltered() {
    return !getFilter().isEmpty();
  }

  public boolean hasItems() {
    return hasItems;
  }

  public boolean hasMappedCategories() {
    return mappedCategories;
  }

  abstract @Nullable Long resolveBudget(int headerId);

  private class MyGroupedAdapter extends TransactionAdapter implements SectionIndexingStickyListHeadersAdapter {
    private final LayoutInflater inflater;
    private final SparseBooleanArray sumLineState = new SparseBooleanArray();

    private MyGroupedAdapter(Context context, int layout, Cursor c, int flags) {
      super(context, layout, c, flags, currencyFormatter, prefHandler, currencyContext,
          id -> checkSealed(new long[]{id}, () -> viewModel.toggleCrStatus(id)));
      inflater = LayoutInflater.from(getActivity());
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
      if (!binding.list.isHeaderCollapsed(headerId(cursor)))
        super.bindView(view, context, cursor);
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
      if (binding.list.isHeaderCollapsed(headerId(cursor))) {
        return new View(context);
      } else {
        return super.newView(context, cursor, parent);
      }
    }

    private int headerId(Cursor cursor) {
      return calculateHeaderId(cursor.getInt(getColumnIndexForYear()), getSecond(cursor));
    }

    @Override
    public int getItemViewType(int position) {
      return binding.list.isHeaderCollapsed(getHeaderId(position)) ? 0 : 1;
    }

    @Override
    public int getViewTypeCount() {
      return 2;
    }

    @Override
    public View getHeaderView(int position, View convertView, ViewGroup parent) {
      HeaderViewHolder holder = null;
      final int headerId = getHeaderIdInt(position);
      final Long budget = licenceHandler.hasTrialAccessTo(ContribFeature.BUDGET) ? resolveBudget(headerId) : null;
      final boolean withBudget = BaseTransactionList.this.getFilter().isEmpty() && budget != null;

      if (convertView != null) {
        holder = (HeaderViewHolder) convertView.getTag();
        boolean holderHasBudget = holder.budgetProgress() != null;
        if (withBudget != holderHasBudget) holder = null;
      }
      if (holder == null) {
        ViewBinding binding = withBudget ? HeaderWithBudgetBinding.inflate(inflater, parent, false) : HeaderBinding.inflate(inflater, parent, false);
        convertView = binding.getRoot();
        holder = new HeaderViewHolder(binding);
        convertView.setTag(holder);
      }
      boolean sumLineVisibility = sumLineState.get(headerId, prefHandler.getBoolean(PrefKey.GROUP_HEADER, true));
      holder.sumLine().setVisibility(sumLineVisibility ? View.VISIBLE : View.GONE);
      HeaderViewHolder finalHolder = holder;
      holder.interimBalance().setOnClickListener(v -> {
        final boolean oldState = finalHolder.sumLine().getVisibility() == View.VISIBLE;
        sumLineState.put(headerId, !oldState);
        finalHolder.sumLine().setVisibility(oldState ? View.GONE : View.VISIBLE);
      });
      if (mAccount.getGrouping() != Grouping.NONE) {
        holder.headerIndicator().setVisibility(View.VISIBLE);
        holder.headerIndicator().setExpanded(!binding.list.isHeaderCollapsed(headerId));
        holder.headerIndicator().setOnClickListener(v -> finalHolder.headerIndicator().rotate(
            !binding.list.isHeaderCollapsed(headerId), expanded -> {
              if (binding != null) {
                if (expanded) {
                  binding.list.expand(headerId);
                  mAdapter.notifyDataSetChanged();
                  persistCollapsedHeaderIds();
                  finalHolder.dividerBottom().setVisibility(View.VISIBLE);
                } else {
                  binding.list.collapse(headerId);
                  persistCollapsedHeaderIds();
                  finalHolder.dividerBottom().setVisibility(View.GONE);
                }
              }
            }));
      } else {
        holder.headerIndicator().setVisibility(View.GONE);
      }

      Cursor c = getCursor();
      if (c != null) {
        c.moveToPosition(position);
        fillSums(holder, headerId, budget);
        holder.text().setText(mAccount.getGrouping().getDisplayTitle(getActivity(), c.getInt(getColumnIndexForYear()), getSecond(c),
            DateInfo.fromCursor(c), userLocaleProvider.getUserPreferredLocale()));
      }
      return convertView;
    }

    @SuppressLint("SetTextI18n")
    private void fillSums(HeaderViewHolder holder, int headerId, Long budget) {
      Long[] data = headerData != null ? headerData.get(headerId) : null;
      if (data != null) {
        holder.sumIncome().setText("⊕ " + convAmount(currencyFormatter, data[0], mAccount.getCurrencyUnit()));
        final long expensesSum = -data[1];
        holder.sumExpense().setText("⊖ " + convAmount(currencyFormatter, expensesSum, mAccount.getCurrencyUnit()));
        holder.sumTransfer().setText(Transfer.BI_ARROW + " " + convAmount(currencyFormatter,
            data[2], mAccount.getCurrencyUnit()));
        String formattedDelta = String.format("%s %s", Long.signum(data[4]) > -1 ? "+" : "-",
            convAmount(currencyFormatter, Math.abs(data[4]), mAccount.getCurrencyUnit()));
        convAmount(currencyFormatter, Math.abs(data[4]), mAccount.getCurrencyUnit());
        holder.interimBalance().setText(
            BaseTransactionList.this.getFilter().isEmpty() && !mAccount.isHomeAggregate() ? String.format("%s %s = %s",
                convAmount(currencyFormatter, data[3], mAccount.getCurrencyUnit()), formattedDelta,
                convAmount(currencyFormatter, data[5], mAccount.getCurrencyUnit())) :
                formattedDelta);
        final DonutProgress budgetProgress = holder.budgetProgress();
        if (budgetProgress != null) {
          if (budget != null) {
            int progress = budget == 0L ? 100 : Math.round(expensesSum * 100F / budget);
            UiUtils.configureProgress(budgetProgress, progress);
            budgetProgress.setFinishedStrokeColor(mAccount.color);
            budgetProgress.setUnfinishedStrokeColor(getComplementColor(mAccount.color));
            budgetProgress.setOnClickListener(v -> {
              ((ProtectedFragmentActivity) requireActivity()).contribFeatureRequested(ContribFeature.BUDGET,
                      new Pair<>(viewModel.getDefaultBudget(mAccount.getAccountId(), mAccount.getGrouping()), headerId));
            });
          }
        }
      }
    }

    @Override
    public long getHeaderId(int position) {
      return getHeaderIdInt(position);
    }

    private int getHeaderIdInt(int position) {
      Cursor c = getCursor();
      if (c == null) return 0;
      c.moveToPosition(position);
      return calculateHeaderId(c.getInt(getColumnIndexForYear()), getSecond(c));
    }


    private int getSectioningId(int position) {
      Cursor c = getCursor();
      return c.moveToPosition(position) ? calculateHeaderId(c.getInt(columnIndexYear), c.getInt(columnIndexMonth), Grouping.MONTH) : 0;
    }

    private int getSecond(Cursor c) {
      switch (mAccount.getGrouping()) {
        case DAY:
          return c.getInt(columnIndexDay);
        case WEEK:
          return c.getInt(columnIndexWeek);
        case MONTH:
          return c.getInt(columnIndexMonth);
        default:
          return 0;
      }
    }

    private int getColumnIndexForYear() {
      switch (mAccount.getGrouping()) {
        case WEEK:
          return columnIndexYearOfWeekStart;
        case MONTH:
          return columnIndexYearOfMonthStart;
        default:
          return columnIndexYear;
      }
    }

    @Override
    public Object[] getSections() {
      return sections;
    }

    /**
     * inspired by {@link android.widget.AlphabetIndexer}<p>
     * {@inheritDoc}
     */
    @Override
    public int getPositionForSection(int sectionIndex) {
      // Check bounds
      if (sectionIds == null) {
        return 0;
      }
      if (sectionIndex >= sections.length) {
        sectionIndex = sections.length - 1;
        if (sectionIndex <= 0) {
          return 0;
        }
      }

      int count = mTransactionsCursor.getCount();
      int start = 0;
      int end = count;
      int pos;
      int targetHeaderId = sectionIds[sectionIndex];

      // Check map
      if (Integer.MIN_VALUE != (pos = mSectionCache.get(targetHeaderId, Integer.MIN_VALUE))) {
        // Is it approximate? Using negative value to indicate that it's
        // an approximation and positive value when it is the accurate
        // position.
        if (pos < 0) {
          pos = -pos;
          end = pos;
        } else {
          // Not approximate, this is the confirmed start of section, return it
          Timber.d("getPositionForSection from cache %d: %d", sectionIndex, pos);
          return pos;
        }
      }

      // Do we have the position of the previous section?
      if (sectionIndex > 0) {
        int prevLetterPos = mSectionCache.get(sectionIds[sectionIndex - 1], Integer.MIN_VALUE);
        if (prevLetterPos != Integer.MIN_VALUE) {
          start = Math.abs(prevLetterPos);
        }
      }

      // Now that we have a possibly optimized start and end, let's binary search

      pos = (end + start) / 2;

      while (pos < end) {
        // Get letter at pos
        int curHeaderId = getSectioningId(pos);

        int diff = Utils.compare(curHeaderId, targetHeaderId);
        if (diff != 0) {
          if (mAccount.getSortDirection().equals(SortDirection.DESC)) diff = -diff;
          // Enter approximation in hash if a better solution doesn't exist
          int curPos = mSectionCache.get(curHeaderId, Integer.MIN_VALUE);
          if (curPos == Integer.MIN_VALUE || Math.abs(curPos) > pos) {
            //     Negative pos indicates that it is an approximation
            mSectionCache.put(curHeaderId, -pos);
          }
          if (diff < 0) {
            start = pos + 1;
            if (start >= count) {
              pos = count;
              break;
            }
          } else {
            end = pos;
          }
        } else {
          // They're the same, but that doesn't mean it's the start
          if (start == pos) {
            // This is it
            break;
          } else {
            // Need to go further lower to find the starting row
            end = pos;
          }
        }
        pos = (start + end) / 2;
      }
      mSectionCache.put(targetHeaderId, pos);
      Timber.d("getPositionForSection %d: %d", sectionIndex, pos);
      return pos;
    }

    @Override
    public int getSectionForPosition(int position) {
      if (sectionIds == null) return 0;
      final int indexOfKey = IntStream.range(0, sectionIds.length)
          .filter(i -> sectionIds[i] == getSectioningId(position))
          .findFirst().orElse(0);
      Timber.d("getSectionForPosition %d: %d", position, indexOfKey);
      return indexOfKey;
    }
  }

  static class HeaderViewHolder {
    private final ViewBinding viewBinding;

    TextView interimBalance() {
      return viewBinding instanceof HeaderBinding ? ((HeaderBinding) viewBinding).headerLine.interimBalance : ((HeaderWithBudgetBinding) viewBinding).interimBalance;
    }

    TextView text() {
      return viewBinding instanceof HeaderBinding ? ((HeaderBinding) viewBinding).headerLine.text : ((HeaderWithBudgetBinding) viewBinding).text;
    }

    ViewGroup sumLine() {
      return viewBinding instanceof HeaderBinding ? ((HeaderBinding) viewBinding).sumLine : ((HeaderWithBudgetBinding) viewBinding).sumLine;
    }

    TextView sumIncome() {
      return viewBinding instanceof HeaderBinding ? ((HeaderBinding) viewBinding).sumIncome : ((HeaderWithBudgetBinding) viewBinding).sumIncome;
    }

    TextView sumExpense() {
      return viewBinding instanceof HeaderBinding ? ((HeaderBinding) viewBinding).sumExpense : ((HeaderWithBudgetBinding) viewBinding).sumExpense;
    }

    TextView sumTransfer() {
      return viewBinding instanceof HeaderBinding ? ((HeaderBinding) viewBinding).sumTransfer : ((HeaderWithBudgetBinding) viewBinding).sumTransfer;
    }

    @Nullable
    DonutProgress budgetProgress() {
      return viewBinding instanceof HeaderBinding ? null : ((HeaderWithBudgetBinding) viewBinding).budgetProgress;
    }

    View dividerBottom() {
      return viewBinding instanceof HeaderBinding ? ((HeaderBinding) viewBinding).dividerBottom : ((HeaderWithBudgetBinding) viewBinding).dividerBottom;
    }

    ExpansionHandle headerIndicator() {
      return viewBinding instanceof HeaderBinding ? ((HeaderBinding) viewBinding).expansionHandle.getRoot() : ((HeaderWithBudgetBinding) viewBinding).expansionHandle.getRoot();
    }

    HeaderViewHolder(ViewBinding viewBinding) {
      this.viewBinding = viewBinding;
    }
  }

  @Override
  public void onHeaderClick(StickyListHeadersListView l, View header,
                            int itemPosition, long headerId, boolean currentlySticky) {
  }

  private void persistCollapsedHeaderIds() {
    PreferenceUtilsKt.putLongList(prefHandler, collapsedHeaderIdsPrefKey(), binding.list.getCollapsedHeaderIds());
  }

  private String collapsedHeaderIdsPrefKey() {
    return String.format(Locale.ROOT, "collapsedHeaders_%d_%s", mAccount.getId(), mAccount.getGrouping());
  }

  protected boolean isTransferAtPosition(int position) {
    if (mTransactionsCursor != null) {
      return mTransactionsCursor.moveToPosition(position) &&
          DbUtils.getLongOr0L(mTransactionsCursor, KEY_TRANSFER_ACCOUNT) != 0L;
    }
    return false;
  }

  protected boolean isSplitAtPosition(int position) {
    if (mTransactionsCursor != null) {
      return mTransactionsCursor.moveToPosition(position) &&
          SPLIT_CATID.equals(DbUtils.getLongOrNull(mTransactionsCursor, KEY_CATID));
    }
    return false;
  }

  protected boolean isVoidAtPosition(int position) {
    if (mTransactionsCursor != null) {
      if (mTransactionsCursor.moveToPosition(position)) {
        CrStatus status;
        try {
          status = CrStatus.valueOf(mTransactionsCursor.getString(columnIndexCrStatus));
        } catch (IllegalArgumentException ex) {
          status = CrStatus.UNRECONCILED;
        }
        return status.equals(CrStatus.VOID);
      }
    }
    return false;
  }

  @SuppressLint("NewApi")
  public void onDrawerOpened() {
    if (mActionMode != null) {
      mCheckedListItems = binding.list.getWrappedList().getCheckedItemPositions().clone();
      mActionMode.finish();
    }
  }

  public void onDrawerClosed() {
    if (mCheckedListItems != null) {
      for (int i = 0; i < mCheckedListItems.size(); i++) {
        if (mCheckedListItems.valueAt(i)) {
          binding.list.getWrappedList().setItemChecked(mCheckedListItems.keyAt(i), true);
        }
      }
    }
    mCheckedListItems = null;
  }

  public void addFilterCriteria(Criteria c) {
    filterPersistence.addCriteria(c);
    refreshAfterFilterChange();
  }

  protected void refreshAfterFilterChange() {
    refresh(true);
  }

  /**
   * Removes a given filter
   *
   * @return true if the filter was set and successfully removed, false otherwise
   */
  protected boolean removeFilter(int id) {
    boolean isFiltered = filterPersistence.removeFilter(id);
    if (isFiltered) {
      refreshAfterFilterChange();
    }
    return isFiltered;
  }

  private String prefNameForCriteria() {
    return TransactionListViewModel.Companion.prefNameForCriteria(getArguments().getLong(KEY_ACCOUNTID));
  }

  public void clearFilter() {
    filterPersistence.clearFilter();
    refreshAfterFilterChange();
  }

  @Override
  public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
    inflater.inflate(R.menu.expenses, menu);
    inflater.inflate(R.menu.grouping, menu);
  }

  private void configureFilterCard() {
    binding.filterCard.setVisibility(getFilter().isEmpty() ? View.GONE : View.VISIBLE);
    if (!getFilter().isEmpty()) {
      addChipsBulk(binding.filter, Stream.of(getFilter().getCriteria()).map(criterion -> criterion.prettyPrint(getContext())).collect(Collectors.toList()), null);
    }
  }

  @Override
  public void onPrepareOptionsMenu(@NonNull Menu menu) {
    super.onPrepareOptionsMenu(menu);
    if (mAccount == null || getActivity() == null) {
      //mAccount seen in report 3331195c529454ca6b25a4c5d403beda
      //getActivity seen in report 68a501c984bdfcc95b40050af4f815bf
      return;
    }
    MenuItem searchMenu = menu.findItem(R.id.SEARCH_COMMAND);
    if (searchMenu != null) {
      searchMenu.setChecked(!getFilter().isEmpty());
      MenuUtilsKt.checkMenuIcon(searchMenu);
      SubMenu filterMenu = searchMenu.getSubMenu();
      for (int i = 0; i < filterMenu.size(); i++) {
        MenuItem filterItem = filterMenu.getItem(i);
        boolean enabled = true;
        int itemId = filterItem.getItemId();
        if (itemId == R.id.FILTER_CATEGORY_COMMAND) {
          enabled = mappedCategories;
        } else if (itemId == R.id.FILTER_STATUS_COMMAND) {
          enabled = mAccount.isAggregate() || !mAccount.getType().equals(AccountType.CASH);
        } else if (itemId == R.id.FILTER_PAYEE_COMMAND) {
          enabled = mappedPayees;
        } else if (itemId == R.id.FILTER_METHOD_COMMAND) {
          enabled = mappedMethods;
        } else if (itemId == R.id.FILTER_TRANSFER_COMMAND) {
          enabled = hasTransfers;
        } else if (itemId == R.id.FILTER_TAG_COMMAND) {
          enabled = hasTags;
        } else if (itemId == R.id.FILTER_ACCOUNT_COMMAND) {
          enabled = mAccount.isAggregate();
        }
        Criteria c = getFilter().get(filterItem.getItemId());
        Utils.menuItemSetEnabledAndVisible(filterItem, enabled || c != null);
        if (c != null) {
          filterItem.setChecked(true);
          filterItem.setTitle(c.prettyPrint(getContext()));
        }
      }
    } else {
      CrashHandler.report(new Exception("Search menu not found"));
    }

    MenuItem groupingItem = menu.findItem(R.id.GROUPING_COMMAND);
    if (groupingItem != null) {
      SubMenu groupingMenu = groupingItem.getSubMenu();
      Utils.configureGroupingMenu(groupingMenu, mAccount.getGrouping());
    }

    MenuItem sortDirectionItem = menu.findItem(R.id.SORT_DIRECTION_COMMAND);
    if (sortDirectionItem != null) {
      SubMenu sortDirectionMenu = sortDirectionItem.getSubMenu();
      Utils.configureSortDirectionMenu(sortDirectionMenu, mAccount.getSortDirection());
    }

    MenuItem balanceItem = menu.findItem(R.id.BALANCE_COMMAND);
    if (balanceItem != null) {
      Utils.menuItemSetEnabledAndVisible(balanceItem, mAccount.getType() != AccountType.CASH && !mAccount.isSealed());
    }

    MenuItem syncItem = menu.findItem(R.id.SYNC_COMMAND);
    if (syncItem != null) {
      Utils.menuItemSetEnabledAndVisible(syncItem, mAccount.getSyncAccountName() != null);
    }
  }

  @Override
  public void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);
    if (filterPersistence != null) {
      filterPersistence.onSaveInstanceState(outState);
    }
    Icepick.saveInstanceState(this, outState);
  }

  public ArrayList<Criteria> getFilterCriteria() {
    return getFilter().getCriteria();
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent intent) {
    if (resultCode == Activity.RESULT_CANCELED) {
      return;
    }
    if (requestCode == FILTER_PAYEE_REQUEST) {
      String label = intent.getStringExtra(KEY_LABEL);
      if (resultCode == Activity.RESULT_OK) {
        long payeeId = intent.getLongExtra(KEY_PAYEEID, 0);
        addPayeeFilter(label, payeeId);
      }
      if (resultCode == Activity.RESULT_FIRST_USER) {
        long[] payeeIds = intent.getLongArrayExtra(KEY_PAYEEID);
        addPayeeFilter(label, payeeIds);
      }
    }
    if (requestCode == FILTER_CATEGORY_REQUEST) {
      String label = intent.getStringExtra(KEY_LABEL);
      if (resultCode == Activity.RESULT_OK) {
        long catId = intent.getLongExtra(KEY_CATID, 0);
        addCategoryFilter(label, catId);
      }
      if (resultCode == Activity.RESULT_FIRST_USER) {
        long[] catIds = intent.getLongArrayExtra(KEY_CATID);
        addCategoryFilter(label, catIds);
      }
    } else if (requestCode == FILTER_TAGS_REQUEST) {
      final ArrayList<Tag> tagList = intent.getParcelableArrayListExtra(KEY_TAG_LIST);
      if (tagList != null && !tagList.isEmpty()) {
        long[] tagIds = Stream.of(tagList).mapToLong(Tag::getId).toArray();
        String label = Stream.of(tagList).map(Tag::getLabel).collect(Collectors.joining(", "));
        addFilterCriteria(new TagCriteria(label, tagIds));
      }
    } else if (requestCode == MAP_CATEGORY_REQUEST || requestCode == MAP_PAYEE_REQUEST
        || requestCode == MAP_METHOD_REQUEST || requestCode == MAP_ACCOUNT_REQUEST) {
      Bundle b = new Bundle();
      int columnStringResId, confirmationStringResId;
      String column;
      String intentKey = KEY_ROWID;
      switch (requestCode) {
        case MAP_CATEGORY_REQUEST: {
          column = intentKey = KEY_CATID;
          columnStringResId = R.string.category;
          confirmationStringResId = R.string.remap_category;
          break;
        }
        case MAP_PAYEE_REQUEST: {
          column = intentKey = KEY_PAYEEID;
          columnStringResId = R.string.payer_or_payee;
          confirmationStringResId = R.string.remap_payee;
          break;
        }
        case MAP_METHOD_REQUEST: {
          column = KEY_METHODID;
          columnStringResId = R.string.method;
          confirmationStringResId = R.string.remap_method;
          break;
        }
        case MAP_ACCOUNT_REQUEST: {
          column = KEY_ACCOUNTID;
          columnStringResId = R.string.account;
          confirmationStringResId = R.string.remap_account;
          break;
        }
        default:
          throw new IllegalStateException("Unexpected value: " + requestCode);
      }
      b.putString(KEY_COLUMN, column);
      b.putLong(KEY_ROWID, intent.getLongExtra(intentKey, 0));
      b.putString(KEY_TITLE_STRING, getString(R.string.dialog_title_confirm_remap, getString(columnStringResId)));
      b.putInt(KEY_POSITIVE_BUTTON_LABEL, R.string.menu_remap);
      b.putInt(ConfirmationDialogFragment.KEY_POSITIVE_BUTTON_CHECKED_LABEL, R.string.button_label_clone_and_remap);
      b.putInt(ConfirmationDialogFragment.KEY_NEGATIVE_BUTTON_LABEL, android.R.string.cancel);
      b.putString(KEY_MESSAGE, getString(confirmationStringResId, intent.getStringExtra(KEY_LABEL)) + " " + getString(R.string.continue_confirmation));
      b.putString(ConfirmationDialogFragment.KEY_CHECKBOX_LABEL, getString(R.string.menu_clone_transaction));
      b.putInt(KEY_COMMAND_POSITIVE, R.id.REMAP_COMMAND);
      showConfirmationDialog(b, REMAP_DIALOG);
    }
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    if (key.equals(String.format(Locale.ROOT, prefNameForCriteria(), KEY_PAYEEID))) {
      filterPersistence = new FilterPersistence(prefHandler, prefNameForCriteria(), null, true, true);
      refreshAfterFilterChange();
    }
  }

  @Override
  public boolean onResult(@NonNull String dialogTag, int which, @NonNull Bundle extras) {
    if (which == BUTTON_POSITIVE) {
      if (NEW_TEMPLATE_DIALOG.equals(dialogTag)) {
        MyExpenses ctx = (MyExpenses) requireActivity();
        String label = extras.getString(SimpleInputDialog.TEXT);
        final Transaction transaction = Transaction.getInstanceFromDb(extras.getLong(KEY_ROWID));
        Uri uri = transaction == null ? null : new Template(transaction, label).save();
        if (uri == null) {
          ctx.showSnackBar(R.string.template_create_error);
        } else {
          // show template edit activity
          Intent i = new Intent(ctx, ExpenseEdit.class);
          i.putExtra(DatabaseConstants.KEY_TEMPLATEID, ContentUris.parseId(uri));
          startActivity(i);
        }
        finishActionMode();
      }
      if (BaseTransactionList.FILTER_COMMENT_DIALOG.equals(dialogTag)) {
        final String textResult = extras.getString(SimpleInputDialog.TEXT);
        if (textResult != null) {
          addFilterCriteria(
              new CommentCriteria(textResult.trim()));
        }
        return true;
      }
      return true;
    }
    return false;
  }

  public void remap(@NonNull Bundle extras, boolean shouldClone) {
    final long[] checkedItemIds = binding.list.getCheckedItemIds();
    final String column = extras.getString(KEY_COLUMN);
    if (column == null) return;
    if (shouldClone) {
      final ProgressDialogFragment progressDialog = ProgressDialogFragment.newInstance(
          getString(R.string.saving), null, ProgressDialog.STYLE_HORIZONTAL, false);
      progressDialog.setMax(checkedItemIds.length);
      getParentFragmentManager()
          .beginTransaction()
          .add(progressDialog, PROGRESS_TAG)
          .commit();
      viewModel.cloneAndRemap(checkedItemIds, column, extras.getLong(KEY_ROWID));
    } else {
      viewModel.remap(checkedItemIds, column, extras.getLong(KEY_ROWID))
          .observe(this, result -> {
            final String message = result > 0 ? getString(R.string.remapping_result) : "No transactions were mapped";
            ((ProtectedFragmentActivity) requireActivity()).showSnackBar(message);
          });
    }
  }

  private void addCategoryFilter(String label, long... catIds) {
    addFilterCriteria(catIds.length == 1 && catIds[0] == NULL_ITEM_ID ?
        new CategoryCriteria() : new CategoryCriteria(label, catIds));
  }

  private void addPayeeFilter(String label, long... payeeIds) {
    addFilterCriteria(payeeIds.length == 1 && payeeIds[0] == NULL_ITEM_ID ?
        new PayeeCriteria() : new PayeeCriteria(label, payeeIds));
  }

}
