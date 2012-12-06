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

package com.github.nutomic.pegasus.content;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.media.AudioManager;
import android.util.Log;

import com.github.nutomic.pegasus.R;

/**
 * Singleton that handles SQL database connection and provides 
 * methods for common queries.
 * 
 * @author Felix Ableitner
 * 
 */
public class Database extends SQLiteOpenHelper {
	
	public static final long ROW_NONE = -1;
	
	private static final String TAG = "Database";
	private static final String DATABASE_NAME = "pegasus.db";
	private static final int DATABASE_VERSION = 2;

	private static Database mInstance = null;

	private Context mContext;

	/**
	 * Return the database instance, creating it if it does not exist.
	 * 
	 * @param context
	 *            The context to start in. Will automatically take the app
	 *            context of it.
	 * @return The databse instance.
	 */
	public static synchronized Database getInstance(Context context) {
		if (mInstance == null) {
			mInstance = new Database(context.getApplicationContext());
		}
		return mInstance;
	}

	private Database(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
		mContext = context;
	}

	/**
	 * Create tables (area, profile, cell, cell_log).
	 */
	@Override
	public void onCreate(SQLiteDatabase db) {
		Log.i(TAG, "Creating database tables.");
		db.execSQL(AreaColumns.CREATE_TABLE);
		db.execSQL(ProfileColumns.CREATE_TABLE);
		db.execSQL(CellColumns.CREATE_TABLE);
		db.execSQL(CellLogColumns.CREATE_TABLE);
		
		// Insert "Normal" profile. Does not change any settings by default.
		ContentValues cv = new ContentValues();
		cv.put(ProfileColumns.NAME, mContext.getResources()
				.getString(R.string.sql_profile_normal));
		cv.put(ProfileColumns.RINGTONE_VOLUME, 5 - ProfileColumns.VOLUME_APPLY_FALSE);
		cv.put(ProfileColumns.NOTIFICATION_VOLUME, 5 - ProfileColumns.VOLUME_APPLY_FALSE);
		cv.put(ProfileColumns.MEDIA_VOLUME, 9 - ProfileColumns.VOLUME_APPLY_FALSE);
		cv.put(ProfileColumns.ALARM_VOLUME, 5 - ProfileColumns.VOLUME_APPLY_FALSE);
		cv.put(ProfileColumns.RINGER_MODE, ProfileColumns.RINGER_MODE_KEEP);
		long normal = db.insert(ProfileColumns.TABLE_NAME, null, cv);
		
		// Insert "Silent" profile. Changes ringtone and notification 
		// volume by default and enables vibration.
		cv = new ContentValues();
		cv.put(ProfileColumns.NAME, mContext.getResources()
				.getString(R.string.sql_profile_silent));
		cv.put(ProfileColumns.RINGTONE_VOLUME, 0);
		cv.put(ProfileColumns.NOTIFICATION_VOLUME, 0);
		cv.put(ProfileColumns.MEDIA_VOLUME, 0 - ProfileColumns.VOLUME_APPLY_FALSE);
		cv.put(ProfileColumns.ALARM_VOLUME, 0 - ProfileColumns.VOLUME_APPLY_FALSE);
		cv.put(ProfileColumns.RINGER_MODE, AudioManager.RINGER_MODE_VIBRATE);
		long silent = db.insert(ProfileColumns.TABLE_NAME, null, cv);

		// Insert the default area.
		cv = new ContentValues();	
		cv.put(AreaColumns.NAME, mContext.getResources()
				.getString(R.string.sql_area_unknown));
		cv.put(AreaColumns.PROFILE_ID, normal);
		cv.put(AreaColumns.WIFI_ENABLED, false);
		cv.put(AreaColumns.BLUETOOTH_ENABLED, false);
		db.insert(AreaColumns.TABLE_NAME, null, cv);

		// Insert "Home" area.
		cv = new ContentValues();	
		cv.put(AreaColumns.NAME, mContext.getResources()
				.getString(R.string.sql_area_home));
		cv.put(AreaColumns.PROFILE_ID, normal);
		cv.put(AreaColumns.WIFI_ENABLED, true);
		cv.put(AreaColumns.BLUETOOTH_ENABLED, false);
		db.insert(AreaColumns.TABLE_NAME, null, cv);

		// Insert "Work" area.
		cv = new ContentValues();	
		cv.put(AreaColumns.NAME, mContext.getResources()
				.getString(R.string.sql_area_work));
		cv.put(AreaColumns.PROFILE_ID, silent);
		cv.put(AreaColumns.WIFI_ENABLED, true);
		cv.put(AreaColumns.BLUETOOTH_ENABLED, false);
		db.insert(AreaColumns.TABLE_NAME, null, cv);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		if (oldVersion == 1) {
			// Add wifi preference to area (Wifi reference in area will not 
			// be used any more).
			db.execSQL("ALTER TABLE area " +
					"ADD COLUMN " + AreaColumns.WIFI_ENABLED + " INTEGER;");
			ContentValues cv = new ContentValues();
			cv.put(AreaColumns.WIFI_ENABLED, true);
			db.update(AreaColumns.TABLE_NAME, cv, null, null);
			
			// Add bluetooth column to area.
			db.execSQL("ALTER TABLE area " +
					"ADD COLUMN " + AreaColumns.BLUETOOTH_ENABLED + " INTEGER;");
			cv = new ContentValues();
			cv.put(AreaColumns.BLUETOOTH_ENABLED, false);
			db.update(AreaColumns.TABLE_NAME, cv, null, null);
		}
	}

}
