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
import android.app.ListActivity;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.widget.SimpleCursorAdapter;
import android.util.Pair;
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
import com.github.nutomic.pegasus.content.AreaColumns;
import com.github.nutomic.pegasus.content.CellColumns;
import com.github.nutomic.pegasus.content.CellLogColumns;
import com.github.nutomic.pegasus.content.Database;
import com.github.nutomic.pegasus.content.ProfileColumns;

/**
 * Displays a list of areas.
 * 
 * @author Felix Ableitner
 * 
 */
public class AreaList extends ListActivity {

	public static final String AREANAME = "areaname";
	public static final String PROFILENAME = "profilename";
	
	private static final String FIRST_RUN = "first_run";
	
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
			LocationService.sendUpdateIntent(AreaList.this);
		}
		
	}
	
	/**
	 * Initializes layout.
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		registerForContextMenu(getListView());
		mAdapter = new SimpleCursorAdapter(this,
				android.R.layout.simple_list_item_2, 
				null,
				new String[] { AREANAME, PROFILENAME }, 
				new int[] { android.R.id.text1, android.R.id.text2 }, 
				0);
		setListAdapter(mAdapter);
		
		// Show welcome dialog only on first start.
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		if (prefs.getBoolean(FIRST_RUN, true)) {
			Editor e = prefs.edit();
			e.putBoolean(FIRST_RUN, false);
			e.commit();
			
			new AlertDialog.Builder(this)
					.setTitle(R.string.arealist_intro_title)
					.setMessage(R.string.arealist_intro_text)
					.setPositiveButton(android.R.string.ok, null)
					.show();
		}
		
		// Make sure LocationService is running.
		startService(new Intent(this, LocationService.class));
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
		// Default string if noe profile is set.
		String noProfile = getResources().getString(R.string.arealist_profile_none);
		mAdapter.changeCursor(Database.getInstance(this)
				.getReadableDatabase().rawQuery(
						"SELECT a._id, a.name as " + AREANAME + ", " +
						"ifnull(p.name, '" + noProfile + "') as " + PROFILENAME + " " +
						"FROM " + AreaColumns.TABLE_NAME + " as a " +
						"LEFT JOIN " + ProfileColumns.TABLE_NAME + " as p " +
						"ON a.profile_id = p._id " + 
						"ORDER BY a." + AreaColumns._ID + " ASC",
						null));		
	}
	
	/**
	 * Open corresponding sound profile.
	 */
	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		chooseProfile(id);
	}

	/**
	 * Show a "pick sound profile" dialog for an area, the selected profile is
	 * then set to be launched when entering the area.
	 * 
	 * @param area
	 *            The sqlite id of the area to choose a profile for. Set -1 if 
	 *            the area does not exist and should be created.
	 */
	private void chooseProfile(final long area) {
		final Database db = Database.getInstance(this);
		
		// Get a pair of all area names and all area IDs.
		Cursor c = db.getReadableDatabase().query(
				ProfileColumns.TABLE_NAME,
				new String[] { ProfileColumns._ID, 
						ProfileColumns.NAME }, 
				null, null, null, null, null);
		String[] names = new String[c.getCount()];
		Long[] ids = new Long[c.getCount()];
		while (c.moveToNext()) {
			ids[c.getPosition()] = c.getLong(c.getColumnIndex(ProfileColumns._ID));
			names[c.getPosition()] = c.getString(c.getColumnIndex(ProfileColumns.NAME));
		}
		final Pair<String[], Long[]> p = Pair.create(names, ids);
		
		new AlertDialog.Builder(this).setTitle(R.string.arealist_profile_choose)
				.setItems(p.first, new DialogInterface.OnClickListener() {

					public void onClick(DialogInterface dialog, final int which) {
						new UpdateTask() {
							
							@Override
							protected Long doInBackground(Void... params) {
								// Set the new profile in area.
								ContentValues cv = new ContentValues();
								cv.put(AreaColumns.PROFILE_ID, p.second[which]);

								db.getWritableDatabase().update(AreaColumns.TABLE_NAME, 
										cv, 
										AreaColumns._ID + " = ?",
										new String[] { Long.toString(area) });
								return null;
							}
						}.execute((Void) null);
					}
				}).show();
	}

	/**
	 * Create context menu for ListView items. Modifiers are hidden for the
	 * default area.
	 */
	public void onCreateContextMenu(android.view.ContextMenu menu, View v,
			android.view.ContextMenu.ContextMenuInfo menuInfo) {
		getMenuInflater().inflate(R.menu.area_list_context, menu);
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;

		String name = getAreaName(info);
		
		menu.setHeaderTitle(name);
		
		// Default area selected, can't rename, learn or delete.
		if (info.id == AreaColumns.AREA_DEFAULT) {
			menu.removeItem(R.id.rename);
			menu.removeItem(R.id.learn);
			menu.removeItem(R.id.delete);
		}
	}

	private String getAreaName(AdapterContextMenuInfo info) {		
		Cursor c = (Cursor) mAdapter.getItem(info.position);
		return c.getString(c.getColumnIndex(AREANAME));		
	}

	/**
	 * Context item on ListView selected, allow choosing the associated sound 
	 * profile, renaming, learning or deleting the area.
	 */
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		final AdapterContextMenuInfo info = (AdapterContextMenuInfo) item
				.getMenuInfo();
		final Database db = Database.getInstance(this);
		switch (item.getItemId()) {
		case R.id.choose:
			chooseProfile(info.id);
			return true;
		case R.id.rename:
			renameArea(info.id, getAreaName((AdapterContextMenuInfo) item.getMenuInfo()));
			return true;
		case R.id.learn:
			new AlertDialog.Builder(this)
					.setTitle(R.string.arealist_learn)
					.setItems(R.array.arealist_learn_area_strings,
							new DialogInterface.OnClickListener() {

								public void onClick(DialogInterface dialog,
										int which) {
									TypedArray millisecondValues = getResources()
											.obtainTypedArray(
													R.array.arealist_learn_area_values);
									long interval = millisecondValues.getInt(which, 0);

									if (interval > 0) {
										// Add future cells during interval.
										Intent i = new Intent(AreaList.this, LocationService.class);
										i.putExtra(LocationService.MESSAGE_LEARN_AREA, info.id);
										i.putExtra(LocationService.MESSAGE_LEARN_INTERVAL, interval);
										startService(i);
										// Add only the current cell from database.
										interval = 0;
									} else {
										// Do not pass the negativity to Database.
										interval = -interval;
									}
									
									final long selectionStartTime = System.currentTimeMillis() - interval;
									new UpdateTask() {
										
										@Override
										protected Long doInBackground(Void... params) {
											ContentValues cv = new ContentValues();
											cv.put(CellColumns.AREA_ID, info.id);
											// Set current area in current cell and in any cell that was visited during interval.
											db.getWritableDatabase().update(CellColumns.TABLE_NAME,
													cv, 
													CellColumns._ID + " = (SELECT " + CellLogColumns.CELL_ID + " FROM " +
															CellLogColumns.TABLE_NAME + " ORDER BY " + 
															CellLogColumns.TIMESTAMP + " DESC Limit 1) OR " +
															CellColumns._ID + " IN (SELECT " + 
															CellLogColumns.CELL_ID + " FROM " +
															CellLogColumns.TABLE_NAME + " WHERE " + 
															CellLogColumns.TIMESTAMP + " > ?)", 
													new String[] { Long.toString(selectionStartTime) });
											return null;
										}
									}.execute((Void) null);
								}
							})
					.show();
			return true;
		case R.id.delete:
			new AlertDialog.Builder(this)
					.setTitle(R.string.arealist_delete)
					.setMessage(R.string.arealist_delete_message)
					.setPositiveButton(android.R.string.yes, new OnClickListener() {
						
						@Override
						public void onClick(DialogInterface dialog, int which) {
							new UpdateTask() {
								
								@Override
								protected Long doInBackground(Void... params) {
									// Don't delete default area.
									if (info.id != AreaColumns.AREA_DEFAULT) {
										db.getWritableDatabase().delete(AreaColumns.TABLE_NAME,
												AreaColumns._ID + " = ?",
												new String[] { Long.toString(info.id) });
										// Reset cells to default area.
										ContentValues cv = new ContentValues();
										cv.put(CellColumns.AREA_ID, AreaColumns.AREA_DEFAULT);
										db.getWritableDatabase().update(CellColumns.TABLE_NAME, 
												cv, 
												CellColumns.AREA_ID + " = ?", 
												new String[] { Long.toString(info.id) });
									}
									return null;
								}
							}.execute((Void) null);
						}
					})
					.setNegativeButton(android.R.string.no, null)
					.show();
			return true;
		default:
			return super.onContextItemSelected(item);
		}
	}

	/**
	 * Show an AlertDialog to edit the name of an area.
	 * 
	 * @param area ID of the area to rename.
	 */
	private void renameArea(final long id, String name) {
		final Database db = Database.getInstance(this);
		final EditText input = new EditText(this);
		input.setText(name);
		input.setSingleLine();
		AlertDialog alert = new AlertDialog.Builder(this)
				.setTitle(R.string.arealist_rename)
				.setView(input)
				.setPositiveButton(android.R.string.ok,
						new OnClickListener() {

							public void onClick(DialogInterface dialog,
									int which) {
								new UpdateTask() {
									
									@Override
									protected Long doInBackground(Void... params) {
										// Don't rename default area.
										if (id != AreaColumns.AREA_DEFAULT) {
											ContentValues cv = new ContentValues();
											cv.put(AreaColumns.NAME, input.getText().toString());

											db.getWritableDatabase().update(AreaColumns.TABLE_NAME, 
													cv, 
													AreaColumns._ID + " = ?",
													new String[] { Long.toString(id) });
										}
										return null;
									}
								}.execute((Void) null);
							}
						})
				.setNegativeButton(android.R.string.cancel, null)
				.create();
		alert.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
		alert.show();
		input.selectAll();
	}

	/**
	 * Create ActionBar menu from xml.
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.area_list, menu);
		return true;
	}

	/**
	 * Handle ActionBar item selections.
	 */
	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
		case R.id.profiles:
			startActivity(new Intent(this, ProfileList.class));
			return true;
		case R.id.new_area:
			new UpdateTask() {
				
				@Override
				protected Long doInBackground(Void... arg0) {
					ContentValues cv = new ContentValues();
					cv.put(AreaColumns.NAME, getResources()
							.getString(R.string.arealist_new));
					cv.put(AreaColumns.PROFILE_ID, Database.ROW_NONE);
					return Database.getInstance(AreaList.this).getWritableDatabase()
							.insert(AreaColumns.TABLE_NAME, null, cv);
				}
				
				@Override
				protected void onPostExecute(Long result) {
					super.onPostExecute(result);
					renameArea(result, getAreaName((AdapterContextMenuInfo) item.getMenuInfo()));
				}
			}.execute((Void) null);
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

}
