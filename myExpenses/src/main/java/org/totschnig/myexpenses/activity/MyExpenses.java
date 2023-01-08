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

package org.totschnig.myexpenses.activity;

import static com.theartofdev.edmodo.cropper.CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE;
import static org.totschnig.myexpenses.activity.ConstantsKt.CREATE_ACCOUNT_REQUEST;
import static org.totschnig.myexpenses.activity.ConstantsKt.EDIT_ACCOUNT_REQUEST;
import static org.totschnig.myexpenses.activity.ConstantsKt.EDIT_REQUEST;
import static org.totschnig.myexpenses.activity.ConstantsKt.OCR_REQUEST;
import static org.totschnig.myexpenses.contract.TransactionsContract.Transactions.TYPE_TRANSACTION;
import static org.totschnig.myexpenses.preference.PrefKey.OCR;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_CLEARED_TOTAL;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_COLOR;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_CURRENCY;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_HAS_CLEARED;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_HIDDEN;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_LABEL;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_RECONCILED_TOTAL;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_ROWID;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_SEALED;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_SORT_KEY;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_TRANSACTIONID;
import static org.totschnig.myexpenses.task.TaskExecutionFragment.TASK_PRINT;
import static org.totschnig.myexpenses.task.TaskExecutionFragment.TASK_REVOKE_SPLIT;
import static org.totschnig.myexpenses.task.TaskExecutionFragment.TASK_SPLIT;
import static org.totschnig.myexpenses.util.CurrencyFormatterKt.formatMoney;
import static org.totschnig.myexpenses.viewmodel.ContentResolvingAndroidViewModelKt.KEY_ROW_IDS;
import static org.totschnig.myexpenses.viewmodel.MyExpensesViewModelKt.ERROR_INIT_DOWNGRADE;
import static eltos.simpledialogfragment.list.CustomListDialog.SELECTED_SINGLE_ID;

import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.AdapterView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.core.view.GravityCompat;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.snackbar.Snackbar;
import com.theartofdev.edmodo.cropper.CropImage;

import org.jetbrains.annotations.NotNull;
import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.adapter.MyGroupedAdapter;
import org.totschnig.myexpenses.adapter.MyViewPagerAdapter;
import org.totschnig.myexpenses.dialog.BalanceDialogFragment;
import org.totschnig.myexpenses.dialog.ConfirmationDialogFragment;
import org.totschnig.myexpenses.dialog.ConfirmationDialogFragment.ConfirmationDialogListener;
import org.totschnig.myexpenses.dialog.HelpDialogFragment;
import org.totschnig.myexpenses.dialog.MessageDialogFragment;
import org.totschnig.myexpenses.dialog.SortUtilityDialogFragment;
import org.totschnig.myexpenses.dialog.TransactionDetailFragment;
import org.totschnig.myexpenses.dialog.select.SelectFilterDialog;
import org.totschnig.myexpenses.dialog.select.SelectHiddenAccountDialogFragment;
import org.totschnig.myexpenses.fragment.ContextualActionBarFragment;
import org.totschnig.myexpenses.fragment.TransactionList;
import org.totschnig.myexpenses.model.Account;
import org.totschnig.myexpenses.model.AccountGrouping;
import org.totschnig.myexpenses.model.ContribFeature;
import org.totschnig.myexpenses.model.CurrencyUnit;
import org.totschnig.myexpenses.model.Grouping;
import org.totschnig.myexpenses.model.Money;
import org.totschnig.myexpenses.model.Sort;
import org.totschnig.myexpenses.model.SortDirection;
import org.totschnig.myexpenses.preference.PrefKey;
import org.totschnig.myexpenses.preference.PreferenceUtilsKt;
import org.totschnig.myexpenses.provider.MoreDbUtilsKt;
import org.totschnig.myexpenses.provider.ProtectedCursorLoader;
import org.totschnig.myexpenses.provider.TransactionProvider;
import org.totschnig.myexpenses.provider.filter.Criteria;
import org.totschnig.myexpenses.task.TaskExecutionFragment;
import org.totschnig.myexpenses.ui.SnackbarAction;
import org.totschnig.myexpenses.util.AppDirHelper;
import org.totschnig.myexpenses.util.Result;
import org.totschnig.myexpenses.util.TextUtils;
import org.totschnig.myexpenses.util.UiUtils;
import org.totschnig.myexpenses.util.Utils;
import org.totschnig.myexpenses.util.ads.AdHandler;
import org.totschnig.myexpenses.util.crashreporting.CrashHandler;
import org.totschnig.myexpenses.util.distrib.DistributionHelper;
import org.totschnig.myexpenses.viewmodel.RoadmapViewModel;

import java.util.AbstractMap;
import java.util.ArrayList;

import eltos.simpledialogfragment.list.MenuDialog;
import se.emilsjolander.stickylistheaders.StickyListHeadersListView;

/**
 * This is the main activity where all expenses are listed
 * From the menu sub activities (Insert, Reset, SelectAccount, Help, Settings)
 * are called
 */
public class MyExpenses extends BaseMyExpenses implements
    ViewPager.OnPageChangeListener, LoaderManager.LoaderCallbacks<Cursor>,
    ConfirmationDialogListener, SortUtilityDialogFragment.OnConfirmListener, SelectFilterDialog.Host {

  public static final int ACCOUNTS_CURSOR = -1;
  private static final String DIALOG_TAG_GROUPING = "GROUPING";
  private static final String DIALOG_TAG_SORTING = "SORTING";

  private LoaderManager mManager;

  private MyGroupedAdapter mDrawerListAdapter;
  private int mAccountCount = 0;

  private AdHandler adHandler;
  private AccountGrouping accountGrouping;
  private Sort accountSort;

  public void toggleScanMode() {
    final boolean oldMode = prefHandler.getBoolean(OCR, false);
    final boolean newMode = !oldMode;
    if (newMode) {
      contribFeatureRequested(ContribFeature.OCR, false);
    } else {
      prefHandler.putBoolean(OCR, newMode);
      updateFab();
      invalidateOptionsMenu();
    }
  }

  private ActionBarDrawerToggle mDrawerToggle;

  private RoadmapViewModel roadmapViewModel;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    final ViewGroup adContainer = findViewById(R.id.adContainer);
    accountGrouping = readAccountGroupingFromPref();
    accountSort = readAccountSortFromPref();
    adHandler = adHandlerFactory.create(adContainer, this);
    adContainer.getViewTreeObserver().addOnGlobalLayoutListener(
        new ViewTreeObserver.OnGlobalLayoutListener() {

          @Override
          public void onGlobalLayout() {
            adContainer.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            adHandler.startBanner();
          }
        });

    try {
      adHandler.maybeRequestNewInterstitial();
    } catch (Exception e) {
      CrashHandler.report(e);
    }

    toolbar = setupToolbar(false);
    toolbar.setVisibility(View.INVISIBLE);
    setupToolbarPopupMenu();
    if (binding.drawer != null) {
      mDrawerToggle = new ActionBarDrawerToggle(this, binding.drawer,
          toolbar, R.string.drawer_open, R.string.drawer_close) {

        /**
         * Called when a drawer has settled in a completely closed state.
         */
        public void onDrawerClosed(View view) {
          super.onDrawerClosed(view);
          TransactionList tl = getCurrentFragment();
          if (tl != null)
            tl.onDrawerClosed();
        }

        /**
         * Called when a drawer has settled in a completely open state.
         */
        public void onDrawerOpened(View drawerView) {
          super.onDrawerOpened(drawerView);
          TransactionList tl = getCurrentFragment();
          if (tl != null)
            tl.onDrawerOpened();
        }

        @Override
        public void onDrawerSlide(View drawerView, float slideOffset) {
          super.onDrawerSlide(drawerView, 0); // this disables the animation
        }
      };

      // Set the drawer toggle as the DrawerListener
      binding.drawer.addDrawerListener(mDrawerToggle);
    }
    mDrawerListAdapter = new MyGroupedAdapter(this, null, currencyFormatter, prefHandler, currencyContext);

    navigationView().setNavigationItemSelectedListener(item -> dispatchCommand(item.getItemId(), null));
    View navigationMenuView = navigationView().getChildAt(0);
    if (navigationMenuView != null) {
      navigationMenuView.setVerticalScrollBarEnabled(false);
    }

    accountList().setAdapter(mDrawerListAdapter);
    accountList().setAreHeadersSticky(false);
    accountList().setOnHeaderClickListener(new StickyListHeadersListView.OnHeaderClickListener() {
      @Override
      public void onHeaderClick(StickyListHeadersListView l, View header, int itemPosition, long headerId, boolean currentlySticky) {
        if (accountList().isHeaderCollapsed(headerId)) {
          accountList().expand(headerId);
        } else {
          accountList().collapse(headerId);
        }
        persistCollapsedHeaderIds();
      }

      @Override
      public boolean onHeaderLongClick(StickyListHeadersListView l, View header, int itemPosition, long headerId, boolean currentlySticky) {
        return false;
      }
    });
    accountList().setOnItemClickListener((parent, view, position, id) -> {
      if (accountId != id) {
        moveToPosition(position);
        closeDrawer();
      }
    });
    registerForContextMenu(accountList());
    accountList().setFastScrollEnabled(prefHandler.getBoolean(PrefKey.ACCOUNT_LIST_FAST_SCROLL, false));

    updateFab();
    setupFabSubMenu();
    if (!isScanMode()) {
      floatingActionButton.setVisibility(View.INVISIBLE);
    }
    if (savedInstanceState == null) {
      Bundle extras = getIntent().getExtras();
      if (extras != null) {
        accountId = Utils.getFromExtra(extras, KEY_ROWID, 0);
        showTransactionFromIntent(extras);
      }
    }
    if (accountId == 0) {
      accountId = prefHandler.getLong(PrefKey.CURRENT_ACCOUNT, 0L);
    }
    roadmapViewModel = new ViewModelProvider(this).get(RoadmapViewModel.class);
    ((MyApplication) getApplicationContext()).getAppComponent().inject(roadmapViewModel);
    getViewModel().getHasHiddenAccounts().observe(this,
        result -> navigationView().getMenu().findItem(R.id.HIDDEN_ACCOUNTS_COMMAND).setVisible(result != null && result));
    if (savedInstanceState != null) {
      setup(false);
    } else {
      newVersionCheck();
      getViewModel().initialize().observe(this, result -> {
        if (result == 0) {
          setup(true);
        } else {
          showMessage(result == ERROR_INIT_DOWNGRADE ? "Database cannot be downgraded from a newer version. Please either uninstall MyExpenses, before reinstalling, or upgrade to a new version." :
                  "Database upgrade failed. Please contact support@myexpenses.mobi !", new MessageDialogFragment.Button(android.R.string.ok, R.id.QUIT_COMMAND, null),
              null,
              null, false);
        }
      });
    }
    /*
    if (savedInstanceState == null) {
      voteReminderCheck();
      voteReminderCheck2();
    }
    */
    reviewManager.init(this);
  }

  public void showTransactionFromIntent(Bundle extras) {
    long idFromNotification = extras.getLong(KEY_TRANSACTIONID, 0);
    //detail fragment from notification should only be shown upon first instantiation from notification
    if (idFromNotification != 0) {
      FragmentManager fm = getSupportFragmentManager();
      TransactionDetailFragment.newInstance(idFromNotification)
          .show(fm, TransactionDetailFragment.class.getName());
      getIntent().removeExtra(KEY_TRANSACTIONID);
    }
  }

  public void persistCollapsedHeaderIds() {
    PreferenceUtilsKt.putLongList(prefHandler, collapsedHeaderIdsPrefKey(), accountList().getCollapsedHeaderIds());
  }

  private String collapsedHeaderIdsPrefKey() {
    return "collapsedHeadersDrawer_" + accountGrouping.name();
  }

  private void setup(boolean firstCreate) {
    getViewModel().loadHiddenAccountCount();
    mManager = LoaderManager.getInstance(this);
    if (firstCreate) {
      mManager.initLoader(ACCOUNTS_CURSOR, null, this);
    }
  }

  private void voteReminderCheck() {
    final String prefKey = "vote_reminder_shown_" + RoadmapViewModel.EXPECTED_MINIMAL_VERSION;
    if (Utils.getDaysSinceUpdate(this) > 1 &&
        !prefHandler.getBoolean(prefKey, false)) {
      roadmapViewModel.getLastVote().observe(this, vote -> {
        boolean hasNotVoted = vote == null;
        if (hasNotVoted || vote.getVersion() < RoadmapViewModel.EXPECTED_MINIMAL_VERSION) {
          Bundle bundle = new Bundle();
          bundle.putCharSequence(
              ConfirmationDialogFragment.KEY_MESSAGE, hasNotVoted ? getString(R.string.roadmap_intro) :
                  TextUtils.concatResStrings(MyExpenses.this, " ",
                      R.string.roadmap_intro, R.string.roadmap_intro_update));
          bundle.putInt(ConfirmationDialogFragment.KEY_COMMAND_POSITIVE, R.id.ROADMAP_COMMAND);
          bundle.putString(ConfirmationDialogFragment.KEY_PREFKEY, prefKey);
          bundle.putInt(ConfirmationDialogFragment.KEY_POSITIVE_BUTTON_LABEL, R.string.roadmap_vote);
          ConfirmationDialogFragment.newInstance(bundle).show(getSupportFragmentManager(),
              "ROAD_MAP_VOTE_REMINDER");
        }
      });
    }
  }

  private void voteReminderCheck2() {
    roadmapViewModel.getShouldShowVoteReminder().observe(this, shouldShow -> {
      if (shouldShow) {
        prefHandler.putLong(PrefKey.VOTE_REMINDER_LAST_CHECK, System.currentTimeMillis());
        showSnackBar(getString(R.string.reminder_vote_update), Snackbar.LENGTH_INDEFINITE, new SnackbarAction(R.string.vote_reminder_action, v -> {
          Intent intent = new Intent(this, RoadmapVoteActivity.class);
          startActivity(intent);
        }));
      }
    });
  }

  private void moveToPosition(int position) {
    if (viewPager().getCurrentItem() == position)
      setCurrentAccount(position);
    else
      viewPager().setCurrentItem(position, false);
  }

  private AccountGrouping readAccountGroupingFromPref() {
    try {
      return AccountGrouping.valueOf(
          prefHandler.getString(PrefKey.ACCOUNT_GROUPING, AccountGrouping.TYPE.name()));
    } catch (IllegalArgumentException e) {
      return AccountGrouping.TYPE;
    }
  }

  private Sort readAccountSortFromPref() {
    try {
      return Sort.valueOf(
          prefHandler.getString(PrefKey.SORT_ORDER_ACCOUNTS, Sort.USAGES.name()));
    } catch (IllegalArgumentException e) {
      return Sort.USAGES;
    }
  }

  @Override
  public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
    if (((AdapterView.AdapterContextMenuInfo) menuInfo).id > 0) {
      MenuInflater inflater = getMenuInflater();
      inflater.inflate(R.menu.accounts_context, menu);
      Cursor c = getAccountsCursor();
      if (c != null) {
        c.moveToPosition(((AdapterView.AdapterContextMenuInfo) menuInfo).position);
        final boolean isSealed = MoreDbUtilsKt.getInt(c, KEY_SEALED) == 1;
        menu.findItem(R.id.CLOSE_ACCOUNT_COMMAND).setVisible(!isSealed);
        menu.findItem(R.id.REOPEN_ACCOUNT_COMMAND).setVisible(isSealed);
        menu.findItem(R.id.EDIT_ACCOUNT_COMMAND).setVisible(!isSealed);
      }
    }
  }

  @Override
  public boolean onContextItemSelected(MenuItem item) {
    dispatchCommand(item.getItemId(), item.getMenuInfo());
    return true;
  }

  /* (non-Javadoc)
   * check if we should show one of the reminderDialogs
   * @see android.app.Activity#onActivityResult(int, int, android.content.Intent)
   */
  @Override
  protected void onActivityResult(int requestCode, int resultCode,
                                  Intent intent) {
    super.onActivityResult(requestCode, resultCode, intent);
    if (requestCode == EDIT_REQUEST) {
      floatingActionButton.show();
      if (resultCode == RESULT_OK) {
        if (!adHandler.onEditTransactionResult()) {
          reviewManager.onEditTransactionResult(this);
        }
      }
    }
    if (requestCode == CREATE_ACCOUNT_REQUEST && resultCode == RESULT_OK) {
      //navigating to the new account currently does not work, due to the way LoaderManager behaves
      //since its implementation is based on MutableLiveData
      accountId = intent.getLongExtra(KEY_ROWID, 0);
    }
    if (requestCode == CROP_IMAGE_ACTIVITY_REQUEST_CODE) {
      if (resultCode == RESULT_OK) {
        getOcrViewModel().startOcrFeature(scanFile, getSupportFragmentManager());
      } else {
        processImageCaptureError(resultCode, CropImage.getActivityResult(intent));
      }
    }
    if (requestCode == OCR_REQUEST) {
      getOcrViewModel().handleOcrData(intent, getSupportFragmentManager());
    }
  }

  @Override
  public void addFilterCriteria(@NotNull Criteria c) {
    TransactionList tl = getCurrentFragment();
    if (tl != null) {
      tl.addFilterCriteria(c);
    }
  }

  /**
   * @return true if command has been handled
   */
  public boolean dispatchCommand(int command, Object tag) {
    if (super.dispatchCommand(command, tag)) {
      return true;
    }
    Intent i;
    TransactionList tl;
    if (command == R.id.BUDGET_COMMAND) {
      contribFeatureRequested(ContribFeature.BUDGET, null);
      return true;
    } else if (command == R.id.DISTRIBUTION_COMMAND) {
      tl = getCurrentFragment();
      if (tl != null && tl.hasMappedCategories()) {
        contribFeatureRequested(ContribFeature.DISTRIBUTION, null);
      } else {
        showMessage(R.string.dialog_command_disabled_distribution);
      }
      return true;
    } else if (command == R.id.HISTORY_COMMAND) {
      tl = getCurrentFragment();
      if (tl != null && tl.hasItems()) {
        contribFeatureRequested(ContribFeature.HISTORY, null);
      } else {
        showMessage(R.string.no_expenses);
      }
      return true;
    } else if (command == R.id.CREATE_COMMAND) {
      if (mAccountCount == 0) {
        showSnackBar(R.string.warning_no_account);
      } else {
        if (isScanMode()) {
          contribFeatureRequested(ContribFeature.OCR, true);
        } else {
          createRowDo(TYPE_TRANSACTION, false);
        }
      }
      return true;
    } else if (command == R.id.BALANCE_COMMAND) {
      tl = getCurrentFragment();
      if (tl != null && hasCleared()) {
        Cursor c = ensureAccountCursorAtCurrentPosition();
        CurrencyUnit currency = getCurrentCurrencyUnit();
        if (c != null && currency != null) {
          Bundle bundle = new Bundle();
          bundle.putLong(KEY_ROWID, MoreDbUtilsKt.getLong(c, KEY_ROWID));
          bundle.putString(KEY_LABEL, MoreDbUtilsKt.getString(c, KEY_LABEL));
          bundle.putString(KEY_RECONCILED_TOTAL,
              formatMoney(currencyFormatter,
                  new Money(currency, MoreDbUtilsKt.getLong(c, KEY_RECONCILED_TOTAL))));
          bundle.putString(KEY_CLEARED_TOTAL, formatMoney(currencyFormatter,
              new Money(currency, MoreDbUtilsKt.getLong(c, KEY_CLEARED_TOTAL))));
          BalanceDialogFragment.newInstance(bundle)
              .show(getSupportFragmentManager(), "BALANCE_ACCOUNT");
        }
      } else {
        showMessage(R.string.dialog_command_disabled_balance);
      }
      return true;
    } else if (command == R.id.RESET_COMMAND) {
      doReset();
      return true;
    } else if (command == R.id.HELP_COMMAND_DRAWER) {
      i = new Intent(this, Help.class);
      i.putExtra(HelpDialogFragment.KEY_CONTEXT, "NavigationDrawer");
      startActivity(i);
      return true;
    } else if (command == R.id.MANAGE_TEMPLATES_COMMAND) {
      i = new Intent(this, ManageTemplates.class);
      startActivity(i);
      return true;
    } else if (command == R.id.CREATE_ACCOUNT_COMMAND) {
      if (getAccountsCursor() == null) {
        complainAccountsNotLoaded();
      }
      //we need the accounts to be loaded in order to evaluate if the limit has been reached
      else if (licenceHandler.hasAccessTo(ContribFeature.ACCOUNTS_UNLIMITED) || mAccountCount < ContribFeature.FREE_ACCOUNTS) {
        closeDrawer();
        i = new Intent(this, AccountEdit.class);
        if (tag != null)
          i.putExtra(KEY_CURRENCY, (String) tag);
        startActivityForResult(i, CREATE_ACCOUNT_REQUEST);
      } else {
        showContribDialog(ContribFeature.ACCOUNTS_UNLIMITED, null);
      }
      return true;
    } else if (command == R.id.DELETE_ACCOUNT_COMMAND_DO) {//reset mAccountId will prevent the now defunct account being used in an immediately following "new transaction"

      return true;
    } else if (command == R.id.SHARE_COMMAND) {
      i = new Intent();
      i.setAction(Intent.ACTION_SEND);
      i.putExtra(Intent.EXTRA_TEXT, Utils.getTellAFriendMessage(this).toString());
      i.setType("text/plain");
      startActivity(Intent.createChooser(i, getResources().getText(R.string.menu_share)));
      return true;
    } else if (command == R.id.CANCEL_CALLBACK_COMMAND) {
      finishActionMode();
      return true;
    } else if (command == R.id.OPEN_PDF_COMMAND) {
      i = new Intent();
      i.setAction(Intent.ACTION_VIEW);
      Uri data = AppDirHelper.ensureContentUri(Uri.parse((String) tag), this);
      i.setDataAndType(data, "application/pdf");
      i.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      startActivity(i, R.string.no_app_handling_pdf_available, null);
      return true;
    } else if (command == R.id.EDIT_ACCOUNT_COMMAND) {
      closeDrawer();
      long accountId = ((AdapterView.AdapterContextMenuInfo) tag).id;
      if (accountId > 0) { //do nothing if accidentally we are positioned at an aggregate account
        i = new Intent(this, AccountEdit.class);
        i.putExtra(KEY_ROWID, accountId);
        startActivityForResult(i, EDIT_ACCOUNT_REQUEST);
      }
      return true;
    } else if (command == R.id.DELETE_ACCOUNT_COMMAND) {
      closeDrawer();
      long accountId = ((AdapterView.AdapterContextMenuInfo) tag).id;
      //do nothing if accidentally we are positioned at an aggregate account
      if (accountId > 0) {
        confirmAccountDelete(accountId);
      }
      return true;
    } else if (command == R.id.GROUPING_ACCOUNTS_COMMAND) {
      MenuDialog.build()
          .menu(this, R.menu.accounts_grouping)
          .choiceIdPreset(accountGrouping.commandId)
          .title(R.string.menu_grouping)
          .show(this, DIALOG_TAG_GROUPING);
      return true;
    } else if (command == R.id.SORT_COMMAND) {
      MenuDialog.build()
          .menu(this, R.menu.accounts_sort)
          .choiceIdPreset(accountSort.getCommandId())
          .title(R.string.menu_sort)
          .show(this, DIALOG_TAG_SORTING);
      return true;
    } else if (command == R.id.CLEAR_FILTER_COMMAND) {
      getCurrentFragment().clearFilter();
      return true;
    } else if (command == R.id.ROADMAP_COMMAND) {
      Intent intent = new Intent(this, RoadmapVoteActivity.class);
      startActivity(intent);
      return true;
    } else if (command == R.id.CLOSE_ACCOUNT_COMMAND) {
      long accountId = ((AdapterView.AdapterContextMenuInfo) tag).id;
      //do nothing if accidentally we are positioned at an aggregate account
      if (accountId > 0) {
        setAccountSealed(accountId, true);
      }
      return true;
    } else if (command == R.id.REOPEN_ACCOUNT_COMMAND) {
      long accountId = ((AdapterView.AdapterContextMenuInfo) tag).id;
      //do nothing if accidentally we are positioned at an aggregate account
      if (accountId > 0) {
        setAccountSealed(accountId, false);
      }
      return true;
    } else if (command == R.id.HIDE_ACCOUNT_COMMAND) {
      long accountId = ((AdapterView.AdapterContextMenuInfo) tag).id;
      //do nothing if accidentally we are positioned at an aggregate account
      if (accountId > 0) {
        getViewModel().setAccountVisibility(true, accountId);
      }
      return true;
    } else if (command == R.id.HIDDEN_ACCOUNTS_COMMAND) {
      SelectHiddenAccountDialogFragment.newInstance().show(getSupportFragmentManager(),
          MANAGE_HIDDEN_FRAGMENT_TAG);
      return true;
    } else if (command == R.id.OCR_FAQ_COMMAND) {
      startActionView("https://github.com/mtotschnig/MyExpenses/wiki/FAQ:-OCR");
      return true;
    } else if (command == R.id.BACKUP_COMMAND) {
      i = new Intent(this, BackupRestoreActivity.class);
      i.setAction(BackupRestoreActivity.ACTION_BACKUP);
      startActivity(i);
    } else if (command == R.id.MANAGE_PARTIES_COMMAND) {
      i = new Intent(this, ManageParties.class);
      startActivity(i);
    }
    return false;
  }

  private void complainAccountsNotLoaded() {
    showSnackBar(R.string.account_list_not_yet_loaded);
  }

  private void closeDrawer() {
    if (binding.drawer != null) binding.drawer.closeDrawers();
  }

  @Override
  public void onPageSelected(int position) {
    finishActionMode();
    setCurrentPosition(position);
    setCurrentAccount(position);
  }

  public void finishActionMode() {
    if (getCurrentPosition() != -1) {
      ContextualActionBarFragment f = getCurrentFragment();
      if (f != null)
        f.finishActionMode();
    }
  }

  @Override
  public void contribFeatureNotCalled(@NonNull ContribFeature feature) {
    if (!DistributionHelper.isGithub() && feature == ContribFeature.AD_FREE) {
      finish();
    }
  }

  @Override
  public boolean onCreateOptionsMenu(@NonNull Menu menu) {
    super.onCreateOptionsMenu(menu);
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.main, menu);
    menu.findItem(R.id.MANAGE_PARTIES_COMMAND).setTitle(getString(R.string.pref_manage_parties_title) + " / " + getString(R.string.debts));
    return true;
  }

  @NonNull
  @Override
  public Loader<Cursor> onCreateLoader(int id, Bundle bundle) {
    if (id == ACCOUNTS_CURSOR) {
      Uri.Builder builder = TransactionProvider.ACCOUNTS_URI.buildUpon();
      builder.appendQueryParameter(TransactionProvider.QUERY_PARAMETER_MERGE_CURRENCY_AGGREGATES, "1");
      return new ProtectedCursorLoader(this, builder.build(), null, KEY_HIDDEN + " = 0", null, null);
    }
    throw new IllegalStateException("Unknown loader id " + id);
  }

  /**
   * set the Current account to the one in the requested position of mAccountsCursor
   */
  private void setCurrentAccount(int position) {
    Cursor c = requireAccountsCursor();
    c.moveToPosition(position);
    long newAccountId = MoreDbUtilsKt.getLong(c, KEY_ROWID);
    if (accountId != newAccountId) {
      prefHandler.putLong(PrefKey.CURRENT_ACCOUNT, newAccountId);
    }
    tintSystemUiAndFab(newAccountId < 0 ? getResources().getColor(R.color.colorAggregate) : MoreDbUtilsKt.getInt(c, KEY_COLOR));

    accountId = newAccountId;
    setCurrentCurrency(MoreDbUtilsKt.getString(c, KEY_CURRENCY));
    setBalance();
    if (MoreDbUtilsKt.getInt(c, KEY_SEALED) == 1) {
      floatingActionButton.hide();
    } else {
      floatingActionButton.show();
    }
    accountList().setItemChecked(position, true);
    supportInvalidateOptionsMenu();
  }

  @Override
  public void onLoadFinished(@NonNull Loader<Cursor> loader, @Nullable Cursor cursor) {
    if (loader.getId() == ACCOUNTS_CURSOR) {
      mAccountCount = 0;
      setAccountsCursor(cursor);

      mDrawerListAdapter.setGrouping(accountGrouping);
      accountList().setCollapsedHeaderIds(PreferenceUtilsKt.getLongList(prefHandler, collapsedHeaderIdsPrefKey()));
      mDrawerListAdapter.swapCursor(cursor);
      //swapping the cursor is altering the accountId, if the
      //sort order has changed, but we want to move to the same account as before
      long cacheAccountId = accountId;
      setupViewPager(cursor);
      accountId = cacheAccountId;
      moveToAccount();
      toolbar.setVisibility(View.VISIBLE);
      if (cursor == null) {
        showSnackBar("Data loading failed", Snackbar.LENGTH_INDEFINITE, new SnackbarAction(R.string.safe_mode, v -> {
          prefHandler.putBoolean(PrefKey.DB_SAFE_MODE, true);
          rebuildAccountProjection();
          mManager.restartLoader(ACCOUNTS_CURSOR, null, this);
        }));
      }
    }
  }

  public void setupViewPager(Cursor cursor) {
    if (getPagerAdapter() == null) {
      setPagerAdapter(new MyViewPagerAdapter(this, getSupportFragmentManager(), cursor));
      viewPager().setAdapter(getPagerAdapter());
      viewPager().addOnPageChangeListener(this);
      viewPager().setPageMargin(UiUtils.dp2Px(10, getResources()));
      viewPager().setPageMarginDrawable(new ColorDrawable(UiUtils.getColor(this, R.attr.colorOnSurface)));
    } else {
      getPagerAdapter().swapCursor(cursor);
    }
  }


  public void moveToAccount() {
    Cursor cursor = getAccountsCursor();
    if (cursor != null && cursor.moveToFirst()) {
      int position = 0;
      while (!cursor.isAfterLast()) {
        long accountId = MoreDbUtilsKt.getLong(cursor ,KEY_ROWID);
        if (accountId == this.accountId) {
          position = cursor.getPosition();
        }
        if (accountId > 0) {
          mAccountCount++;
        }
        cursor.moveToNext();
      }
      setCurrentPosition(position);
      moveToPosition(getCurrentPosition());
    } else {
      onNoData();
    }
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    Bundle extras = intent.getExtras();
    if (extras != null) {
      long accountId = extras.getLong(KEY_ROWID);
      if (accountId != this.accountId) {
        this.accountId = accountId;
        moveToAccount();
      }
      showTransactionFromIntent(extras);
    }
  }

  @Override
  public void onLoaderReset(@NonNull Loader<Cursor> loader) {
    if (loader.getId() == ACCOUNTS_CURSOR) {
      getPagerAdapter().swapCursor(null);
      mDrawerListAdapter.swapCursor(null);
      onNoData();
      setAccountsCursor(null);
    }
  }

  public void onNoData() {
    setTitle(R.string.app_name);
    toolbar.setSubtitle(null);
    setCurrentPosition(-1);
  }

  @Override
  public void onPageScrollStateChanged(int arg0) {
    // noop
  }

  @Override
  public void onPageScrolled(int arg0, float arg1, int arg2) {
    // noop
  }

  @Override
  public boolean onResult(@NonNull String dialogTag, int which, @NonNull Bundle extras) {
    if (which != BUTTON_POSITIVE) return false;
    if (DIALOG_TAG_SORTING.equals(dialogTag)) {
      return handleSortOption((int) extras.getLong(SELECTED_SINGLE_ID));
    }
    if (DIALOG_TAG_GROUPING.equals(dialogTag)) {
      return handleAccountsGrouping((int) extras.getLong(SELECTED_SINGLE_ID));
    }
    return super.onResult(dialogTag, which, extras);
  }

  @Override
  public void onPostExecute(int taskId, Object o) {
    super.onPostExecute(taskId, o);
    switch (taskId) {
      case TASK_SPLIT: {
        Result result = (Result) o;
        if (((Result) o).isSuccess()) {
          recordUsage(ContribFeature.SPLIT_TRANSACTION);
        }
        showSnackBar(result.print(this));
        break;
      }
      case TASK_REVOKE_SPLIT: {
        Result result = (Result) o;
        showSnackBar(result.print(this));
        break;
      }
      case TASK_PRINT: {
        Result<Uri> result = (Result<Uri>) o;
        if (result.isSuccess()) {
          recordUsage(ContribFeature.PRINT);
          showMessage(result.print(this),
              new MessageDialogFragment.Button(R.string.menu_open, R.id.OPEN_PDF_COMMAND, result.getExtra().toString(), true),
              MessageDialogFragment.nullButton(R.string.button_label_close),
              new MessageDialogFragment.Button(R.string.button_label_share_file, R.id.SHARE_PDF_COMMAND, result.getExtra().toString(), true),
              false);
        } else {
          showSnackBar(result.print(this));
        }
        break;
      }
    }
  }

  private boolean hasCleared() {
    Cursor cursor = ensureAccountCursorAtCurrentPosition();
    if (cursor != null) {
      return cursor.getCount() > 0 && cursor.getInt(cursor.getColumnIndexOrThrow(KEY_HAS_CLEARED)) > 0;
    }
    return false;
  }

  @Override
  protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
    super.onRestoreInstanceState(savedInstanceState);
    mManager.initLoader(ACCOUNTS_CURSOR, null, this);
  }

  @Override
  protected void onPostCreate(Bundle savedInstanceState) {
    super.onPostCreate(savedInstanceState);
    // Sync the toggle state after onRestoreInstanceState has occurred.
    if (mDrawerToggle != null) mDrawerToggle.syncState();
  }

  @Override
  public void onConfigurationChanged(@NonNull Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    if (mDrawerToggle != null) mDrawerToggle.onConfigurationChanged(newConfig);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    // Pass the event to ActionBarDrawerToggle, if it returns
    // true, then it has handled the app icon touch event
    if (mDrawerToggle != null && mDrawerToggle.onOptionsItemSelected(item)) {
      return true;
    }
    if (item.getItemId() == R.id.SCAN_MODE_COMMAND) {
      toggleScanMode();
      return true;
    }

    return handleGrouping(item) || handleSortDirection(item) || super.onOptionsItemSelected(item);
  }

  @Override
  public void onPositive(Bundle args, boolean checked) {
    super.onPositive(args, checked);
    int command = args.getInt(ConfirmationDialogFragment.KEY_COMMAND_POSITIVE);
    if (command == R.id.DELETE_COMMAND_DO) {
      finishActionMode();
      showSnackBarIndefinite(R.string.progress_dialog_deleting);
      getViewModel().deleteTransactions(args.getLongArray(KEY_ROW_IDS), checked).observe(this, result -> {
        if (result > 0) {
          if (!checked) {
            showSnackBar(getResources().getQuantityString(R.plurals.delete_success, result, result));
          } else {
            dismissSnackBar();
          }
        } else {
          showDeleteFailureFeedback(null, null);
        }
      });
    } else if (command == R.id.BALANCE_COMMAND_DO) {
      balance(args.getLong(KEY_ROWID), checked);
    } else if (command == R.id.REMAP_COMMAND) {
      getCurrentFragment().remap(args, checked);
    } else if (command == R.id.SPLIT_TRANSACTION_COMMAND) {
      finishActionMode();
      startTaskExecution(TASK_SPLIT, args, R.string.saving);
    } else if (command == R.id.UNGROUP_SPLIT_COMMAND) {
      finishActionMode();
      startTaskExecution(TASK_REVOKE_SPLIT, args, R.string.saving);
    } else if (command == R.id.LINK_TRANSFER_COMMAND) {
      finishActionMode();
      getViewModel().linkTransfer(args.getLongArray(KEY_ROW_IDS));
    }
  }

  @Override
  public void onNegative(Bundle args) {
    int command = args.getInt(ConfirmationDialogFragment.KEY_COMMAND_NEGATIVE);
    if (command != 0) {
      dispatchCommand(command, null);
    }
  }

  @Override
  public void onDismissOrCancel(Bundle args) {
  }

  @Override
  protected void onResume() {
    super.onResume();
    adHandler.onResume();
  }

  @Override
  public void onDestroy() {
    adHandler.onDestroy();
    super.onDestroy();
  }

  @Override
  protected void onPause() {
    adHandler.onPause();
    super.onPause();
  }

  public void onBackPressed() {
    if (binding.drawer != null && binding.drawer.isDrawerOpen(GravityCompat.START)) {
      binding.drawer.closeDrawer(GravityCompat.START);
    } else {
      super.onBackPressed();
    }
  }

  protected boolean handleSortOption(int itemId) {
    Sort newSort = Sort.fromCommandId(itemId);
    boolean result = false;
    if (newSort != null) {
      if (!newSort.equals(accountSort)) {
        accountSort = newSort;
        prefHandler.putString(PrefKey.SORT_ORDER_ACCOUNTS, newSort.name());

        if (mManager.getLoader(ACCOUNTS_CURSOR) != null && !mManager.getLoader(ACCOUNTS_CURSOR).isReset()) {
          mManager.restartLoader(ACCOUNTS_CURSOR, null, this);
        } else {
          mManager.initLoader(ACCOUNTS_CURSOR, null, this);
        }
      }
      result = true;
      if (itemId == R.id.SORT_CUSTOM_COMMAND) {
        Cursor cursor = getAccountsCursor();
        if (cursor == null) {
          complainAccountsNotLoaded();
        } else {
          ArrayList<AbstractMap.SimpleEntry<Long, String>> accounts = new ArrayList<>();
          if (cursor.moveToFirst()) {
            while (!cursor.isAfterLast()) {
              final long id = MoreDbUtilsKt.getLong(cursor, KEY_ROWID);
              if (id > 0) {
                accounts.add(new AbstractMap.SimpleEntry<>(id, MoreDbUtilsKt.getString(cursor, KEY_LABEL)));
              }
              cursor.moveToNext();
            }
          }
          SortUtilityDialogFragment.newInstance(accounts).show(getSupportFragmentManager(), "SORT_ACCOUNTS");
        }
      }
    }
    return result;
  }

  protected boolean handleAccountsGrouping(int itemId) {
    AccountGrouping newGrouping = null;

    if (itemId == R.id.GROUPING_ACCOUNTS_CURRENCY_COMMAND) {
      newGrouping = AccountGrouping.CURRENCY;
    } else if (itemId == R.id.GROUPING_ACCOUNTS_TYPE_COMMAND) {
      newGrouping = AccountGrouping.TYPE;
    } else if (itemId == R.id.GROUPING_ACCOUNTS_NONE_COMMAND) {
      newGrouping = AccountGrouping.NONE;
    }
    if (newGrouping != null && !newGrouping.equals(accountGrouping)) {
      accountGrouping = newGrouping;
      prefHandler.putString(PrefKey.ACCOUNT_GROUPING, newGrouping.name());

      if (mManager.getLoader(ACCOUNTS_CURSOR) != null && !mManager.getLoader(ACCOUNTS_CURSOR).isReset())
        mManager.restartLoader(ACCOUNTS_CURSOR, null, this);
      else
        mManager.initLoader(ACCOUNTS_CURSOR, null, this);
      return true;
    }
    return false;
  }

  protected boolean handleGrouping(MenuItem item) {
    Grouping newGrouping = Utils.getGroupingFromMenuItemId(item.getItemId());
    if (newGrouping != null) {
      if (!item.isChecked()) {
        getViewModel().persistGrouping(accountId, newGrouping);
      }
      return true;
    }
    return false;
  }

  protected boolean handleSortDirection(MenuItem item) {
    SortDirection newSortDirection = Utils.getSortDirectionFromMenuItemId(item.getItemId());
    if (newSortDirection != null) {
      if (!item.isChecked()) {
        if (accountId == Account.HOME_AGGREGATE_ID) {
          getViewModel().persistSortDirectionHomeAggregate(newSortDirection);
        } else if (accountId < 0) {
          getViewModel().persistSortDirectionAggregate(getCurrentCurrency(), newSortDirection);
        } else {
          getViewModel().persistSortDirection(accountId, newSortDirection);
        }
      }
      return true;
    }
    return false;
  }

  @Override
  public void onSortOrderConfirmed(long[] sortedIds) {
    getViewModel().sortAccounts(sortedIds);
  }

  public void clearFilter(View view) {
    Bundle b = new Bundle();
    b.putString(ConfirmationDialogFragment.KEY_MESSAGE, getString(R.string.clear_all_filters));
    b.putInt(ConfirmationDialogFragment.KEY_COMMAND_POSITIVE, R.id.CLEAR_FILTER_COMMAND);
    ConfirmationDialogFragment.newInstance(b).show(getSupportFragmentManager(), "CLEAR_FILTER");
  }

  public int getAccountCount() {
    return mAccountCount;
  }
}