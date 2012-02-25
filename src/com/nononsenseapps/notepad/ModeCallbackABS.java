package com.nononsenseapps.notepad;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.support.v4.app.FragmentActivity;
import android.text.ClipboardManager;
import android.text.format.Time;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.Toast;

import com.actionbarsherlock.view.ActionMode;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.nononsenseapps.notepad.interfaces.DeleteActionListener;
import com.nononsenseapps.notepad.interfaces.OnModalDeleteListener;

public class ModeCallbackABS implements ActionMode.Callback,
		DeleteActionListener, OnItemClickListener, OnItemLongClickListener {

	private static final String TAG = "ModeCallbackABS";

	protected NotesListFragment list;
	
	protected FragmentActivity activity;

	protected HashMap<Long, String> textToShare;

	protected OnModalDeleteListener onDeleteListener;

	protected HashSet<Integer> notesToDelete;

	protected ActionMode mode;
	
	public int checkedItemCount = 0;

	public ModeCallbackABS(NotesListFragment list, FragmentActivity activity) {
		textToShare = new HashMap<Long, String>();
		notesToDelete = new HashSet<Integer>();
		this.list = list;
		this.activity = activity;
		checkedItemCount = 0;
	}
	
	public void setActionMode(ActionMode mode) {
		this.mode = mode;
	}

	public void setDeleteListener(OnModalDeleteListener onDeleteListener) {
		this.onDeleteListener = onDeleteListener;

	}

	protected Intent createShareIntent(String text) {
		Intent shareIntent = new Intent(Intent.ACTION_SEND);
		shareIntent.setType("text/plain");
		shareIntent.putExtra(Intent.EXTRA_TEXT, text);
		shareIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);

		return shareIntent;
	}

	protected void addTextToShare(long id) {
		// Read note
		Uri uri = NotesEditorFragment.getUriFrom(id);
		Cursor cursor = openNote(uri);

		if (cursor != null && !cursor.isClosed() && cursor.moveToFirst()) {
			String note = "";

			int colTitleIndex = cursor
					.getColumnIndex(NotePad.Notes.COLUMN_NAME_TITLE);

			if (colTitleIndex > -1)
				note = cursor.getString(colTitleIndex) + "\n";

			int colDueIndex = cursor
					.getColumnIndex(NotePad.Notes.COLUMN_NAME_DUE_DATE);
			String due = "";
			if (colDueIndex > -1)
				due = cursor.getString(colDueIndex);

			if (due != null && !due.isEmpty()) {
				Time date = new Time(Time.getCurrentTimezone());
				date.parse3339(due);

				note = note + "due date: " + date.format3339(true) + "\n";
			}

			int colNoteIndex = cursor
					.getColumnIndex(NotePad.Notes.COLUMN_NAME_NOTE);

			if (colNoteIndex > -1)
				note = note + "\n" + cursor.getString(colNoteIndex);

			// Put in hash
			textToShare.put(id, note);
		}
		cursor.close();
	}

	protected void delTextToShare(long id) {
		textToShare.remove(id);
	}

	protected String buildTextToShare() {
		String text = "";
		ArrayList<String> notes = new ArrayList<String>(textToShare.values());
		if (!notes.isEmpty()) {
			text = text + notes.remove(0);
			while (!notes.isEmpty()) {
				text = text + "\n\n" + notes.remove(0);
			}
		}
		return text;
	}

	@Override
	public boolean onCreateActionMode(ActionMode mode, Menu menu) {
		if (FragmentLayout.UI_DEBUG_PRINTS)
			Log.d("MODALMAN", "onCreateActionMode mode: " + mode);
		// Clear data!
		this.textToShare.clear();
		this.notesToDelete.clear();

		MenuInflater inflater = activity.getSupportMenuInflater();
		// if (FragmentLayout.lightTheme)
		// inflater.inflate(R.menu.list_select_menu_light, menu);
		// else
		inflater.inflate(R.menu.list_select_menu, menu);
		mode.setTitle("Select Items");

		// this.mode = mode;

		return true;
	}

	@Override
	public void onDestroyActionMode(ActionMode mode) {
		if (FragmentLayout.UI_DEBUG_PRINTS)
			Log.d("modeCallback", "onDestroyActionMode: " + mode.toString()
					+ ", " + mode.getMenu().toString());
		checkedItemCount = 0;
		list.getListView().setOnItemClickListener(null);
		//list.getListView().setOnItemLongClickListener(null);
		list.setFutureSingleCheck();
	}

	// FIX
	public void onItemCheckedStateChanged(int position,
			long id, boolean checked) {
		// Set the share intent with updated text
		if (checked) {
			addTextToShare(id);
			this.notesToDelete.add(position);
		} else {
			delTextToShare(id);
			this.notesToDelete.remove(position);
		}
		final int checkedCount = checkedItemCount;
		switch (checkedCount) {
		case 0:
			mode.setSubtitle(null);
			break;
		case 1:
			mode.setSubtitle("One item selected");
			break;
		default:
			mode.setSubtitle("" + checkedCount + " items selected");
			break;
		}
	}

	private void shareNote(String text) {
		Intent share = new Intent(Intent.ACTION_SEND);
		share.setType("text/plain");
		share.putExtra(Intent.EXTRA_TEXT, text);
		share.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
		activity.startActivity(Intent.createChooser(share, "Share note"));
	}

	public Cursor openNote(Uri uri) {
		/*
		 * Using the URI passed in with the triggering Intent, gets the note or
		 * notes in the provider. Note: This is being done on the UI thread. It
		 * will block the thread until the query completes. In a sample app,
		 * going against a simple provider based on a local database, the block
		 * will be momentary, but in a real app you should use
		 * android.content.AsyncQueryHandler or android.os.AsyncTask.
		 */
		Cursor cursor = activity.managedQuery(uri, // The URI that gets
													// multiple
				// notes from
				// the provider.
				NotesEditorFragment.PROJECTION, // A projection that returns
												// the note ID and
				// note
				// content for each note.
				null, // No "where" clause selection criteria.
				null, // No "where" clause selection values.
				null // Use the default sort order (modification date,
						// descending)
				);
		// Or Honeycomb will crash
		activity.stopManagingCursor(cursor);
		return cursor;
	}

	@Override
	public void onDeleteAction() {
		int num = notesToDelete.size();
		if (onDeleteListener != null) {
			for (int pos : notesToDelete) {
				if (FragmentLayout.UI_DEBUG_PRINTS)
					Log.d(TAG, "Deleting key: " + pos);
			}
			onDeleteListener.onModalDelete(notesToDelete);
		}
		Toast.makeText(activity, "Deleted " + num + " items",
				Toast.LENGTH_SHORT).show();
		mode.finish();
	}

	@Override
	public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
		return false;
	}

	@Override
	public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
		if (FragmentLayout.UI_DEBUG_PRINTS)
			Log.d("MODALMAN", "onActionItemClicked mode: " + mode);
		switch (item.getItemId()) {
		case R.id.modal_share:
			shareNote(buildTextToShare());
			mode.finish();
			break;
		case R.id.modal_copy:
			ClipboardManager clipboard = (ClipboardManager) activity
					.getSystemService(Context.CLIPBOARD_SERVICE);
			// ICS style
			// clipboard.setPrimaryClip(ClipData.newPlainText("Note",
			// buildTextToShare()));
			// Gingerbread style.
			clipboard.setText(buildTextToShare());
			Toast.makeText(
					activity,
					"Copied " + checkedItemCount
							+ " notes to clipboard", Toast.LENGTH_SHORT).show();
			mode.finish();
			break;
		case R.id.modal_delete:
			onDeleteAction();
			break;
		default:
			// Toast.makeText(activity, "Clicked " + item.getTitle(),
			// Toast.LENGTH_SHORT).show();
			break;
		}
		return true;
	}

	@Override
	public boolean onItemLongClick(AdapterView<?> arg0, View arg1, int pos,
			long id) {
		list.getListView().setItemChecked(pos, !list.getListView().isItemChecked(pos));
		return toggleItemCheck(pos, id);
	}

	@Override
	public void onItemClick(AdapterView<?> arg0, View arg1, int pos, long id) {
		toggleItemCheck(pos, id);
	}

	public boolean toggleItemCheck(int pos, long id) {
		Log.d(TAG, "pos: " + pos + " is checked: " + list.getListView().isItemChecked(pos));
		
		boolean checked = list.getListView().isItemChecked(pos);
		
		if (checked)
			checkedItemCount++;
		else
			checkedItemCount--;
		
		onItemCheckedStateChanged(pos,
				id, checked);
		
		return true;
	}
}
