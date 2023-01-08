package org.totschnig.myexpenses.preference;

import android.content.DialogInterface;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MergeCursor;
import android.os.Bundle;
import android.provider.CalendarContract;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceDialogFragmentCompat;

import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.activity.ProtectedFragmentActivity;
import org.totschnig.myexpenses.provider.DbUtils;

public class CalendarListPreferenceDialogFragmentCompat extends PreferenceDialogFragmentCompat {
  @Override
  protected void onPrepareDialogBuilder(@NonNull AlertDialog.Builder builder) {
    final ListPreference preference = (ListPreference) getPreference();
    boolean localExists = false;
    Cursor selectionCursor;
    final String value = preference.getValue();
    int selectedIndex = -1;
    String[] projection =
        new String[]{
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.NAME,
            "ifnull(" + CalendarContract.Calendars.ACCOUNT_NAME + ",'') || ' / ' ||" +
                "ifnull(" + CalendarContract.Calendars.CALENDAR_DISPLAY_NAME + ",'') AS full_name"
        };
    Cursor calCursor = null;
    try {
      calCursor = getContext().getContentResolver().
          query(CalendarContract.Calendars.CONTENT_URI,
              projection,
              CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL + " >= " + CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR,
              null,
              CalendarContract.Calendars._ID + " ASC");
    } catch (SecurityException e) {
      // android.permission.READ_CALENDAR or android.permission.WRITE_CALENDAR missing
    }
    if (calCursor != null) {
      if (calCursor.moveToFirst()) {
        do {
          if (calCursor.getString(0).equals(value)) {
            selectedIndex = calCursor.getPosition();
          }
          if (DbUtils.getString(calCursor, 1).equals(MyApplication.PLANNER_ACCOUNT_NAME)
              && DbUtils.getString(calCursor, 2).equals(CalendarContract.ACCOUNT_TYPE_LOCAL)
              && DbUtils.getString(calCursor, 3).equals(MyApplication.PLANNER_CALENDAR_NAME))
            localExists = true;
        } while (calCursor.moveToNext());
      }
      if (localExists) {
        selectionCursor = calCursor;
      } else {
        MatrixCursor extras = new MatrixCursor(new String[]{
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.NAME,
            "full_name"});
        extras.addRow(new String[]{
            "-1", "", "", "",
            getContext().getString(R.string.pref_planning_calendar_create_local)});
        selectionCursor = new MergeCursor(new Cursor[]{calCursor, extras});
      }
      selectionCursor.moveToFirst();
      builder.setSingleChoiceItems(selectionCursor, selectedIndex, "full_name",
          (dialog, which) -> {
            long itemId = ((AlertDialog) dialog).getListView().getItemIdAtPosition(which);
            if (itemId == -1) {
              //TODO: use Async Task Strict Mode violation
              String plannerId = MyApplication.getInstance().createPlanner(false);
              boolean success = !plannerId.equals(MyApplication.INVALID_CALENDAR_ID);
              ((ProtectedFragmentActivity) getActivity()).showSnackBar(
                  success ? R.string.planner_create_calendar_success : R.string.planner_create_calendar_failure);
              if (success) {
                preference.setValue(plannerId);
              }
            } else {
              if (preference.callChangeListener(itemId)) {
                preference.setValue(String.valueOf(itemId));
              }
            }
            CalendarListPreferenceDialogFragmentCompat.this.onClick(dialog,
                DialogInterface.BUTTON_POSITIVE);
            dialog.dismiss();
          });
    } else {
      builder.setMessage("Calendar provider not available");
    }
    builder.setPositiveButton(null, null);
  }

  @Override
  public void onDialogClosed(boolean b) {
    //nothing to do since directly handled in onClickListener of SingleChoiceItems
  }

  public static CalendarListPreferenceDialogFragmentCompat newInstance(String key) {
    CalendarListPreferenceDialogFragmentCompat fragment = new CalendarListPreferenceDialogFragmentCompat();
    Bundle bundle = new Bundle(1);
    bundle.putString(ARG_KEY, key);
    fragment.setArguments(bundle);
    return fragment;
  }
}
