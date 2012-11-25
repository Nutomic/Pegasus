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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;

import com.github.nutomic.pegasus.LocationService;
import com.github.nutomic.pegasus.R;
import com.github.nutomic.pegasus.VolumePreference;
import com.github.nutomic.pegasus.content.Database;
import com.github.nutomic.pegasus.content.ProfileColumns;

/**
 * Allows editing a sound profile, uses Database values (or defaults) to
 * initialize, saves to Database.
 * 
 * Using deprecated methods because v4 support library does not have 
 * PreferenceFragment implementation.
 * 
 * @author Felix Ableitner
 * 
 */
public class ProfileEdit extends PreferenceActivity implements 
		OnPreferenceChangeListener {
	
	/** Key for the Intent extra to store the profile edited here. */
	public static final String PROFILE_ID = "profile_id";

	private volatile long mProfile;
	private VolumePreference mRingtoneVolume;
	private VolumePreference mNotificationVolume;
	private VolumePreference mMediaVolume;
	private VolumePreference mAlarmVolume;
	private ListPreference mRingerMode;
	private CheckBoxPreference mWifiEnabled;

	/**
	 * Initialize sound profile id from intent (extra "profile_id" must 
	 * be set.
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mProfile = getIntent().getExtras().getLong(PROFILE_ID);

		addPreferencesFromResource(R.xml.profile);

		new AsyncTask<Void, Void, Cursor>() {

			@Override
			protected Cursor doInBackground(Void... params) {
				Cursor c = Database.getInstance(ProfileEdit.this).getReadableDatabase().query(
						ProfileColumns.TABLE_NAME,
						new String[] { ProfileColumns.NAME,
								ProfileColumns.RINGTONE_VOLUME, 
								ProfileColumns.NOTIFICATION_VOLUME,
								ProfileColumns.MEDIA_VOLUME, 
								ProfileColumns.ALARM_VOLUME, 
								ProfileColumns.WIFI_ENABLED,
								ProfileColumns.RINGER_MODE }, 
						"_id = ?",
						new String[] { Long.toString(mProfile) }, 
						null, null, null);
				c.moveToFirst();
				return c;
			}
			
			protected void onPostExecute(Cursor c) {
				setTitle(c.getString(c.getColumnIndex(ProfileColumns.NAME)));

				final AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

				mRingtoneVolume = (VolumePreference) findPreference("ringtone_volume");
				mRingtoneVolume.setMaxValue(am.getStreamMaxVolume(AudioManager.STREAM_RING));
				mRingtoneVolume.setDefaultValue(c.getInt(c.getColumnIndex(ProfileColumns.RINGTONE_VOLUME)));
				mRingtoneVolume.setOnPreferenceChangeListener(ProfileEdit.this);

				mNotificationVolume = (VolumePreference) findPreference("notification_volume");
				mNotificationVolume.setMaxValue(am.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION));
				mNotificationVolume.setDefaultValue(c.getInt(c.getColumnIndex(ProfileColumns.NOTIFICATION_VOLUME)));
				mNotificationVolume.setOnPreferenceChangeListener(ProfileEdit.this);

				mMediaVolume = (VolumePreference) findPreference("media_volume");
				mMediaVolume.setMaxValue(am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
				mMediaVolume.setDefaultValue(c.getInt(c.getColumnIndex(ProfileColumns.MEDIA_VOLUME)));
				mMediaVolume.setOnPreferenceChangeListener(ProfileEdit.this);

				mAlarmVolume = (VolumePreference) findPreference("alarm_volume");
				mAlarmVolume.setMaxValue(am.getStreamMaxVolume(AudioManager.STREAM_ALARM));
				mAlarmVolume.setDefaultValue(c.getInt(c.getColumnIndex(ProfileColumns.ALARM_VOLUME)));
				mAlarmVolume.setOnPreferenceChangeListener(ProfileEdit.this);

				mRingerMode = (ListPreference) findPreference("ringer_mode");
				mRingerMode.setValue(Integer.toString(c.getInt(c.getColumnIndex(ProfileColumns.RINGER_MODE))));
				mRingerMode.setOnPreferenceChangeListener(ProfileEdit.this);

				mWifiEnabled = (CheckBoxPreference) findPreference("wifi_enabled");
				mWifiEnabled.setChecked((c.getInt(c.getColumnIndex(ProfileColumns.WIFI_ENABLED)) == 0)
						? true : false);
				mWifiEnabled.setOnPreferenceChangeListener(ProfileEdit.this);
				
			};
			
		}.execute((Void) null);
	}

	/**
	 * Write changed preference to database.
	 */
	public boolean onPreferenceChange(Preference preference, Object newValue) {
		ContentValues cv = new ContentValues();
		if (preference.equals(mRingtoneVolume)) {
			cv.put(ProfileColumns.RINGTONE_VOLUME, (Integer) newValue);
		}
		else if (preference.equals(mNotificationVolume)) {
			cv.put(ProfileColumns.NOTIFICATION_VOLUME, (Integer) newValue);
		}
		else if (preference.equals(mMediaVolume)) {
			cv.put(ProfileColumns.MEDIA_VOLUME, (Integer) newValue);
		}
		else if (preference.equals(mAlarmVolume)) {
			cv.put(ProfileColumns.ALARM_VOLUME, (Integer) newValue);
		}
		else if (preference.equals(mRingerMode)) {
			cv.put(ProfileColumns.RINGER_MODE, Integer.parseInt((String) newValue));
		}
		else if (preference.equals(mWifiEnabled)) {
			cv.put(ProfileColumns.RINGTONE_VOLUME, (Boolean) newValue);
		}
		
		final ContentValues values = cv;
		new Thread(new Runnable() {
			
			@Override
			public void run() {
			
				Database.getInstance(ProfileEdit.this)
						.getWritableDatabase().update(ProfileColumns.TABLE_NAME, 
							values, 
							ProfileColumns._ID + " = ?",
							new String[] { Long.toString(mProfile) });
				
				LocationService.sendUpdateIntent(ProfileEdit.this);
			}
		}).start();

		return true;
	}

}
