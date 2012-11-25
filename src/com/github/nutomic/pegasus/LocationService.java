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

package com.github.nutomic.pegasus;

import java.util.Set;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.v4.app.NotificationCompat;
import android.telephony.CellLocation;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;

import com.github.nutomic.pegasus.activities.AreaList;
import com.github.nutomic.pegasus.content.AreaColumns;
import com.github.nutomic.pegasus.content.CellColumns;
import com.github.nutomic.pegasus.content.CellLogColumns;
import com.github.nutomic.pegasus.content.Database;
import com.github.nutomic.pegasus.content.ProfileColumns;

/**
 * Changes the sound profile when a different network cell is entered.
 * 
 * @author Felix Ableitner
 * 
 */
public class LocationService extends Service {

	/**
	 * Start learning an area, pass time interval in seconds as data. Must
	 * also set MESSAGE_LEARN_AREA.
	 */
	public static final String MESSAGE_LEARN_INTERVAL = "learn_interval";
	
	/**
	 * Start learning an area, pass the id of the area to learn. Must also 
	 * set MESSAGE_LEARN_INTERVAL.
	 */
	public static final String MESSAGE_LEARN_AREA = "learn_area";
	
	/** Areas or profiles updated, reapply current profile. */
	public static final String MESSAGE_UPDATE = "update";

	private static final int NOTIFICATION_ID = 1;

	private static final String TAG = "LocationService";
	
	private static final int CELL_NO_SIGNAL = -1;
	
	/** Database ID of the current area. */
	private volatile long mCurrentArea = Database.ROW_NONE;
	
	private volatile int mCurrentCell = CELL_NO_SIGNAL;
	
	/** The database ID of the area to assign new cells to. */
	private volatile long mLearnArea = Database.ROW_NONE;
	
	/** Time limit after which to stop learning mLearnArea. */
	private volatile long mLearnUntil = 0;

	private Notification mNotification = null;
	
	private CellListener mCellListener;

	private class CellListener extends PhoneStateListener {
		
		/**
		 *  Network type as TelephonyManager.PHONE_TYPE_CDMA or 
		 *  TelephonyManager.PHONE_TYPE_GSM.
		 */
		private final int mNetworkType;

		/**
		 * Set the network type on start as callback is only called on changes.
		 */
		CellListener(int networkType) {
			mNetworkType = networkType;
		}
		
		/**
		 * Read the cell id, add it to the database if it is entered 
		 * for the first time, log it, apply the associated sound profile.
		 */
		@Override
		public void onCellLocationChanged(final CellLocation location) {
			super.onCellLocationChanged(location);

			new Thread(new Runnable() {
				
				@Override
				public void run() {
					int cell = CELL_NO_SIGNAL;

					if (mNetworkType == TelephonyManager.PHONE_TYPE_CDMA) {
						CdmaCellLocation l = (CdmaCellLocation) location;
						cell = l.getBaseStationId();
					} else if (mNetworkType == TelephonyManager.PHONE_TYPE_GSM) {
						GsmCellLocation l = (GsmCellLocation) location;
						cell = l.getCid();
					}
					
					// Ignore no signal.
					if (cell == CELL_NO_SIGNAL) {
						Log.i(TAG, "Lost signal, igoring");
						return;
					}
					
					Log.i(TAG, "Switch to cell " + Integer.toString(cell));
					
					final Database db = Database.getInstance(LocationService.this);

					// Get cell ID if cell exists.
					Cursor c = db.getReadableDatabase().query(
							CellColumns.TABLE_NAME + " as c, " + AreaColumns.TABLE_NAME + " as a", 
							new String[] { "c." + CellColumns._ID + " as cell_id", 
									"a." + AreaColumns._ID + " as area_id" }, 
							"a." + AreaColumns._ID + " = c." + CellColumns.AREA_ID + " " +
							"AND " + CellColumns.CELL_ID + " = ? " +
							"AND " + CellColumns.CELL_TYPE + " = ?", 
							new String[] { Long.toString(cell),
									Integer.toString(mNetworkType) }, 
							null, null, null);
					long cellRow = Database.ROW_NONE;
					long newArea = Database.ROW_NONE;
					
					// If cell is in database, select and update if necessary.
					if (c.moveToFirst()) {	
						// Get the values.
						cellRow = c.getLong(c.getColumnIndex("cell_id"));
						newArea = c.getLong(c.getColumnIndex("area_id"));
						// Update the cell if we are learning an area.
						if (SystemClock.elapsedRealtime() <= mLearnUntil) {
							ContentValues cv = new ContentValues();
							cv.put(CellColumns.AREA_ID, mLearnArea);
							db.getWritableDatabase().update(CellColumns.TABLE_NAME, 
									cv, 
									CellColumns._ID + " = ?", 
									new String[] { Long.toString(cellRow) });
							newArea = mLearnArea;
						}
					}
					// Create cell if it does not exist.
					else {
						// Check if we are still learning, if not use default area.
						newArea = (SystemClock.elapsedRealtime() <= mLearnUntil)
							? mLearnArea
							: AreaColumns.AREA_DEFAULT;
						
						ContentValues cv = new ContentValues();
						cv.put(CellColumns.AREA_ID, newArea);
						cv.put(CellColumns.CELL_ID, cell);
						cv.put(CellColumns.CELL_TYPE, mNetworkType);					
						cellRow = db.getWritableDatabase()
								.insert(CellColumns.TABLE_NAME, null, cv);
					}
					
					// Only apply profile if we weren't in the same area before.
					if (mCurrentArea != newArea) {
						mCurrentArea = newArea;
						applyProfile(cell);
					}
					
					// Log cell.
					ContentValues cv = new ContentValues();
					cv.put(CellLogColumns.CELL_ID, cellRow);
					cv.put(CellLogColumns.TIMESTAMP, System.currentTimeMillis());
					db.getWritableDatabase().insert(CellLogColumns.TABLE_NAME, null, cv);
					
					mCurrentCell = cell;
				}
			}).start();
		}
		
		/**
		 * Applies the profile for the current area.
		 */
		private void applyProfile(final int cell) {	
			new Thread(new Runnable() {
				
				@Override
				public void run() {
					final Database db = Database.getInstance(LocationService.this);
					
					// Get area name and ID of the associated profile.
					Cursor c = db.getReadableDatabase().query(
							AreaColumns.TABLE_NAME + " as a, " + CellColumns.TABLE_NAME,
							new String[] { AreaColumns.PROFILE_ID, AreaColumns.NAME },
							"a." + AreaColumns._ID + " = " + CellColumns.AREA_ID + " AND " +
									CellColumns.CELL_ID + " = ? AND " +
									CellColumns.CELL_TYPE + " = ?",
							new String[] { Integer.toString(mCurrentCell),
									Integer.toString(mNetworkType)}, 
							null, null, null);
					
					long profileId = Database.ROW_NONE;
					String areaName;
					if (c.moveToFirst()) {
						profileId = c.getLong(c.getColumnIndex(AreaColumns.PROFILE_ID));
						areaName = c.getString(c.getColumnIndex(AreaColumns.NAME));
					}
					else {
						areaName = getResources().getString(
								R.string.locationservice_area_unknown);
					}

					c = db.getReadableDatabase().query(
							ProfileColumns.TABLE_NAME,
							new String[] { ProfileColumns.NAME,
									ProfileColumns.RINGTONE_VOLUME, 
									ProfileColumns.NOTIFICATION_VOLUME,
									ProfileColumns.MEDIA_VOLUME, 
									ProfileColumns.ALARM_VOLUME, 
									ProfileColumns.WIFI_ENABLED,
									ProfileColumns.RINGER_MODE }, 
							"_id = ?",
							new String[] { Long.toString(profileId) }, 
							null, null, null);
					
					// Apply profile if there is one and set the name for the notification.
					String profileName;
					if (c.moveToFirst()) {
						Context context = LocationService.this;
						AudioManager am = (AudioManager) context
								.getSystemService(Context.AUDIO_SERVICE);
						WifiManager wm = (WifiManager) context
								.getSystemService(Context.WIFI_SERVICE);

						am.setStreamVolume(AudioManager.STREAM_RING, 
								c.getInt(c.getColumnIndex(ProfileColumns.RINGTONE_VOLUME)),
								AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);

						am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 
								c.getInt(c.getColumnIndex(ProfileColumns.NOTIFICATION_VOLUME)), 
								AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);

						am.setStreamVolume(AudioManager.STREAM_MUSIC, 
								c.getInt(c.getColumnIndex(ProfileColumns.MEDIA_VOLUME)),
								AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);

						am.setStreamVolume(AudioManager.STREAM_ALARM, 
								c.getInt(c.getColumnIndex(ProfileColumns.ALARM_VOLUME)),
								AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);

						am.setRingerMode(c.getInt(c.getColumnIndex(ProfileColumns.RINGER_MODE)));

						wm.setWifiEnabled((c.getInt(c.getColumnIndex(ProfileColumns.WIFI_ENABLED)) == 0)
								? true : false);
						
						profileName = c.getString(c.getColumnIndex(ProfileColumns.NAME));
					}
					else {
						profileName = getResources().getString(
								R.string.arealist_profile_none);			
					}

					Log.i(TAG, "Apply profile " + profileName + 
							" (in area " + areaName + ")");
					showNotification(areaName, profileName);
				}
			}).start();
		}
	}

	/**
	 * Convenience method for sending an Intent with MESSAGE_UPDATE 
	 * to the service.
	 * 
	 * @param context Application context.
	 */
	public static void sendUpdateIntent(Context context) {
		Intent i = new Intent(context, LocationService.class);
		// Value is unused.
		i.putExtra(MESSAGE_UPDATE, 0);
		context.startService(i);		
	}

	/**
	 * Register CellListener and show Notification.
	 */
	@Override
	public void onCreate() {
		super.onCreate();
		TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		mCellListener = new CellListener(tm.getPhoneType());
		tm.listen(mCellListener, PhoneStateListener.LISTEN_CELL_LOCATION);
		// Force update.
		mCellListener.onCellLocationChanged(tm.getCellLocation());
	}

	/**
	 * Show Notification displaying area and profile.
	 * 
	 * @param area Currently active area.
	 * @param profile Currently active profile.
	 */
	private void showNotification(String area, String profile) {
		mNotification  = new NotificationCompat.Builder(this)
		        .setContentTitle(area)
		        .setContentText(profile)
		        .setSmallIcon(R.drawable.ic_launcher)
		        .setAutoCancel(false)
		        .setOngoing(true)
		        .setContentIntent(PendingIntent.getActivity(this, 0, 
		        		new Intent(this, AreaList.class), 0))
		        .build();

		startForeground(NOTIFICATION_ID, mNotification);
	}

	/**
	 * Receive start Intent, start learning an area or check if the 
	 * sound profile for the current area has changed.
	 */
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent != null) {
			Bundle extras = intent.getExtras();
			if (extras != null) {
				Set<String> keys = extras.keySet();
				if (keys.contains(MESSAGE_LEARN_AREA)) {
					// Set the area to learn now and the learn duration.
					mLearnUntil =  SystemClock.elapsedRealtime() +
							extras.getLong(MESSAGE_LEARN_INTERVAL);
					mLearnArea = extras.getLong(MESSAGE_LEARN_AREA);
				}
				if (keys.contains(MESSAGE_UPDATE)) {
					// Profile/area mappings have changed, reapply profile.
					mCellListener.applyProfile(mCurrentCell);
				}
			}
		}
		return START_STICKY;
	}

	/**
	 * Cannot bind.
	 */
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

}
