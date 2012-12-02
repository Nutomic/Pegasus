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

import android.provider.BaseColumns;

/**
 * Profile table columns.
 * 
 * @author Felix Ableitner
 *
 */
public class ProfileColumns implements BaseColumns {
	
	public static final String TABLE_NAME = "profile";
	
	// Columns
	public static final String NAME = "name";
	public static final String RINGTONE_VOLUME = "ringtone_volume";
	public static final String NOTIFICATION_VOLUME = "notification_volume";
	public static final String MEDIA_VOLUME = "media_volume";
	public static final String ALARM_VOLUME = "alarm_volume";
	public static final String WIFI_ENABLED = "wifi_enabled";
	public static final String RINGER_MODE = "ringer_mode";
	

	public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" +
			_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
			NAME + " TEXT," +
			RINGTONE_VOLUME + " INTEGER," +
			NOTIFICATION_VOLUME + " INTEGER," +
			MEDIA_VOLUME + " INTEGER," +
			ALARM_VOLUME + " INTEGER," +
			WIFI_ENABLED + " INTEGER," +
			RINGER_MODE + " INTEGER" +
		    ");";

	/**
	 * This value is subtracted from any volume value if it should not 
	 * be applied (only saved). Generally allows for easy testing by 
	 * checking if the value is smaller than zero.
	 */
	public static final int VOLUME_APPLY_FALSE = 100;
	
	/**
	 * Value to indicate that AlarmManager.setRingerMode should not be used.
	 */
	public static final int RINGER_MODE_KEEP = -1;
	  
}
