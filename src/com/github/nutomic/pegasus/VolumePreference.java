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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.SeekBar;

import com.github.nutomic.pegasus.content.ProfileColumns;

/**
 * Preference that opens a SeekBar dialog and has a CheckBox, only if the 
 * CheckBox is checked is the value applied (but it is always saved).
 * 
 * @author Felix Ableitner
 *
 */
public class VolumePreference extends Preference implements 
		OnCheckedChangeListener, android.view.View.OnClickListener {
	
	private CheckBox mCheckBox;
	private int mProgress;
	private int mMax;
	
	/**
	 * Initialize layout, set Preference not persistent-
	 */
    public VolumePreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        setPersistent(false);
        setLayoutResource(R.layout.volume_preference);
    }
    
    /**
     * Initialize OnClickListeners.
     */
    @Override
    public View getView(View convertView, ViewGroup parent) {
    	View view = super.getView(convertView, parent);
        
        View layout = view.findViewById(R.id.text_layout);
        layout.setOnClickListener(this);

        mCheckBox = (CheckBox) view.findViewById(R.id.profile_checkbox);
        mCheckBox.setOnCheckedChangeListener(this);
    	
    	return view;
    }
    
    /**
     * Set current position of the selector.
     */
    @Override
    public void setDefaultValue(Object defaultValue) {
		mProgress = (Integer) defaultValue;	
		if (mProgress >= 0) {
			mCheckBox.setChecked(true);			
		}
		else {
			mCheckBox.setChecked(false);
			// Set progress to the actual volume value.
			mProgress += ProfileColumns.VOLUME_APPLY_FALSE;
		}
    }
    
    /**
     * Set the maximum value for the SeekBar.
     */
    public void setMaxValue(int max) {
    	mMax = max;
    }

	/**
	 * CheckBox value was changed.
	 */
	@Override
	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		callChangeListener();
	}
	
	/**
	 * Calls the change listener for this preference, with VOLUME_APPLY_FALSE 
	 * subtracted if the CheckBox is unchecked.
	 */
	private void callChangeListener() {
		callChangeListener((mCheckBox.isChecked()) 
				? mProgress 
				: mProgress - ProfileColumns.VOLUME_APPLY_FALSE);			
	}

	/**
	 * Left part (text) clicked, show volume dialog.
	 */
	@Override
	public void onClick(View v) {
		// Initialize dialog layout.
		LayoutInflater inflater = (LayoutInflater) getContext()
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View view = inflater.inflate(R.layout.seekbar_dialog, null);
        final SeekBar seekBar = (SeekBar) view.findViewById(R.id.seekbar); 
		seekBar.setMax(mMax); 
		seekBar.setProgress((mProgress >= 0) 
				? mProgress 
				: mProgress + ProfileColumns.VOLUME_APPLY_FALSE);
		
		// Create and show dialog.
		new AlertDialog.Builder(getContext())
				.setTitle(getTitle())
				.setView(view)
				.setPositiveButton(android.R.string.ok, new OnClickListener() {
					
					@Override
					public void onClick(DialogInterface dialog, int which) {
						mProgress = seekBar.getProgress();	
						callChangeListener();				
					}
				})
				.setNegativeButton(android.R.string.cancel, null)
				.show();        
	}
	
}