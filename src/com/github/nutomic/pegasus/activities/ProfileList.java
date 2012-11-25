/*
 * Copyright (C) 2012 Felix Ableitner
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.nutomic.pegasus.activities;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.ListActivity;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.database.Cursor;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.widget.SimpleCursorAdapter;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.EditText;
import android.widget.ListView;

import com.github.nutomic.pegasus.LocationService;
import com.github.nutomic.pegasus.R;
import com.github.nutomic.pegasus.content.Database;
import com.github.nutomic.pegasus.content.ProfileColumns;

/**
 * Displays a list of profiles.
 * 
 * @author Felix Ableitner
 * 
 */
public class ProfileList extends ListActivity {

	SimpleCursorAdapter mAdapter;
	
	/**
	 * AsyncTask that refreshes the ListView and LocationService after finishing.
	 * 
	 * @author Felix Ableitner
	 *
	 */
	private abstract class UpdateTask extends AsyncTask<Void, Void, Long> {

		@Override
		protected void onPostExecute(Long result) {
			updateCursor();
			LocationService.sendUpdateIntent(ProfileList.this);
		}
		
	}
	
	/**
	 * Initialize layout.
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		registerForContextMenu(getListView());
		mAdapter = new SimpleCursorAdapter(this,
				android.R.layout.simple_list_item_1, 
				null,
				new String[] { ProfileColumns.NAME }, 
				new int[] { android.R.id.text1 },
				0);
		setListAdapter(mAdapter);
	}

	/**
	 * Open SQL cursor.
	 */
	@Override
	protected void onStart() {
		super.onStart();
		updateCursor();
	}

	/**
	 * Close SQL cursor.
	 */
	@Override
	protected void onStop() {
		super.onStop();
		mAdapter.changeCursor(null);
	}

	/**
	 * Replace the current list cursor with a new one.
	 */
	private void updateCursor() {
		mAdapter.changeCursor(Database.getInstance(this)
				.getReadableDatabase().query(ProfileColumns.TABLE_NAME,
						new String[]{ ProfileColumns._ID, ProfileColumns.NAME },
						null,
						new String[]{},
						null,
						null,
						ProfileColumns._ID + " ASC"));		
	}
	
	/**
	 * Open corresponding sound profile.
	 */
	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		startActivity(new Intent(this, ProfileEdit.class)
				.putExtra(ProfileEdit.PROFILE_ID, id));
	}

	/**
	 * Create context menu for ListView items.
	 */
	public void onCreateContextMenu(android.view.ContextMenu menu, View v,
			android.view.ContextMenu.ContextMenuInfo menuInfo) {
		getMenuInflater().inflate(R.menu.profile_list_context, menu);
		final AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
		
		// Set profile name as menu title.
		Cursor c = (Cursor) getListAdapter().getItem(info.position);
		menu.setHeaderTitle(c.getString(c.getColumnIndex(ProfileColumns.NAME)));
	}
	
	private String getProfileName(AdapterContextMenuInfo info) {	
		Cursor c = (Cursor) mAdapter.getItem(info.position);
		return c.getString(c.getColumnIndex(ProfileColumns.NAME));			
	}

	/**
	 * Context menu selection, edit, rename or delete profile.
	 */
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		final AdapterContextMenuInfo info = (AdapterContextMenuInfo) item
				.getMenuInfo();
		final Database db = Database.getInstance(this);
		switch (item.getItemId()) {
		case R.id.edit:
			Intent i = new Intent(this, ProfileEdit.class);
			i.putExtra(ProfileEdit.PROFILE_ID, info.id);
			startActivity(i);
			return true;
		case R.id.rename:
			renameProfile(info.id, getProfileName((AdapterContextMenuInfo) item.getMenuInfo()));
			return true;
		case R.id.delete:
			new Builder(this)
					.setTitle(R.string.profilelist_delete)
					.setMessage(R.string.profilelist_delete_message)
					.setPositiveButton(android.R.string.yes,
							new OnClickListener() {

								public void onClick(DialogInterface dialog,
										int which) {
									new UpdateTask() {
										
										@Override
										protected Long doInBackground(Void... arg0) {
											db.getWritableDatabase().delete(ProfileColumns.TABLE_NAME, 
													ProfileColumns._ID + " = ?",
													new String[] { Long.toString(info.id) });
											return null;
										}
									}.execute((Void) null);
								}
							}).setNegativeButton(android.R.string.no, null)
					.show();
			return true;
		default:
			return super.onContextItemSelected(item);
		}
	}

	/**
	 * Show an AlertDialog to edit the name of a profile.
	 * 
	 * @param profile ID of the profile to rename.
	 */
	private void renameProfile(final long profile, String name) {
		final EditText input = new EditText(this);
		input.setText(name);
		input.setSingleLine();
		AlertDialog alert = new AlertDialog.Builder(this)
				.setTitle(R.string.profilelist_rename)
				.setView(input)
				.setPositiveButton(android.R.string.ok,
						new OnClickListener() {

							public void onClick(DialogInterface dialog,
									int which) {
								new UpdateTask() {
									
									@Override
									protected Long doInBackground(Void... params) {
										ContentValues cv = new ContentValues();
										cv.put(ProfileColumns.NAME, input.getText().toString());
										Database.getInstance(ProfileList.this)
												.getWritableDatabase().update(
														ProfileColumns.TABLE_NAME, 
														cv, 
														ProfileColumns._ID + " = ?",
														new String[] { Long.toString(profile) });
										return null;
									}
								}.execute((Void) null);
							}
						})
				.setNegativeButton(android.R.string.cancel, null)
				.create();
		alert.getWindow().setSoftInputMode(
				WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
		alert.show();
		input.selectAll();
	}

	/**
	 * Create ActionBar menu.
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.profile_list, menu);
		return true;
	}

	/**
	 * Handle ActionBar item selections.
	 */
	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
		case R.id.new_item:
			new UpdateTask() {
				
				@Override
				protected Long doInBackground(Void... params) {
					// Insert new profile into database.
					ContentValues cv = new ContentValues();
					cv.put(ProfileColumns.NAME, getResources()
							.getString(R.string.profilelist_new));
					cv.put(ProfileColumns.RINGTONE_VOLUME, 5);
					cv.put(ProfileColumns.NOTIFICATION_VOLUME, 5);
					cv.put(ProfileColumns.MEDIA_VOLUME, 5);
					cv.put(ProfileColumns.ALARM_VOLUME, 5);
					cv.put(ProfileColumns.WIFI_ENABLED, true);
					cv.put(ProfileColumns.RINGER_MODE, AudioManager.RINGER_MODE_NORMAL);
				
					return Database.getInstance(ProfileList.this).getWritableDatabase()
							.insert(ProfileColumns.TABLE_NAME, null, cv);
				}
				
				@Override
				protected void onPostExecute(Long result) {
					super.onPostExecute(result);
					renameProfile(result, getProfileName((AdapterContextMenuInfo) item.getMenuInfo()));
				}
			}.execute((Void) null);
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

}
