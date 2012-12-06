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
import android.content.ContentValues;
import android.content.DialogInterface;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.util.Log;
import android.util.Pair;

import com.github.nutomic.pegasus.LocationService;
import com.github.nutomic.pegasus.R;
import com.github.nutomic.pegasus.content.AreaColumns;
import com.github.nutomic.pegasus.content.Database;
import com.github.nutomic.pegasus.content.ProfileColumns;

/**
 * Allows editing an area, uses Database values (or defaults) to
 * initialize, saves to Database.
 * 
 * Using deprecated methods because v4 support library does not have 
 * PreferenceFragment implementation.
 * 
 * @author Felix Ableitner
 *
 */
public class AreaEdit extends PreferenceActivity implements 
		OnPreferenceChangeListener, OnPreferenceClickListener {
	
	/** Key for the Intent extra to store the area edited here. */
	public static final String AREA_ID = "area_id";
	
	private long mArea;
	private Preference mProfile;
	private CheckBoxPreference mWifi;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mArea = getIntent().getExtras().getLong(AREA_ID);

		addPreferencesFromResource(R.xml.area_edit);
		
		new AsyncTask<Void, Void, Cursor>() {

			@Override
			protected Cursor doInBackground(Void... params) {
				return Database.getInstance(AreaEdit.this).getReadableDatabase()
						.query(AreaColumns.TABLE_NAME, 
								new String[] { AreaColumns.WIFI_ENABLED }, 
								AreaColumns._ID + " = ?", 
								new String[] { Long.toString(mArea) }, 
								null, null, null);
			}
			
			@Override
			protected void onPostExecute(Cursor c) {
				c.moveToFirst();
				// TODO: nothing here works				
				mProfile = findPreference("profile");
				mProfile.setOnPreferenceClickListener(AreaEdit.this);
				
				mWifi = (CheckBoxPreference) findPreference("wifi_enabled");
				mWifi.setChecked((c.getInt(c.getColumnIndex(AreaColumns.WIFI_ENABLED)) == 1) 
						? true : false);
				mWifi.setOnPreferenceChangeListener(AreaEdit.this);
			}
		}.execute((Void) null);
	}
	
	@Override
	public boolean onPreferenceChange(Preference preference, Object newValue) {
		ContentValues cv = new ContentValues();
		if (preference.equals(mWifi)) {
			cv.put(AreaColumns.WIFI_ENABLED, (Boolean) newValue);
		}
		else if (preference.equals(mProfile)) {
			// TODO: does not cause profile to be applied
			cv.put(AreaColumns.PROFILE_ID, (Long) newValue);
		}
		
		final ContentValues values = cv;
		new Thread(new Runnable() {
			
			@Override
			public void run() {
				Database.getInstance(AreaEdit.this).getWritableDatabase()
						.update(AreaColumns.TABLE_NAME, 
								values,
								AreaColumns._ID + "= ?", 
								new String[] { Long.toString(mArea) });
				
				LocationService.sendUpdateIntent(AreaEdit.this);
			}
		}).start();
		return true;
	}

	/**
	 * Show a "pick sound profile" dialog for an area, the selected profile is
	 * then set to be launched when entering the area.
	 */
	@Override
	public boolean onPreferenceClick(Preference preference) {
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
		
		new AlertDialog.Builder(this).setTitle(R.string.areaedit_profile)
				.setItems(p.first, new DialogInterface.OnClickListener() {

					public void onClick(DialogInterface dialog, final int which) {
						new AsyncTask<Void, Void, Void>() {
							
							@Override
							protected Void doInBackground(Void... params) {
								onPreferenceChange(mProfile, p.second[which]);
								return null;
							}
						}.execute((Void) null);
					}
				}).show();
		return true;
	}
}
