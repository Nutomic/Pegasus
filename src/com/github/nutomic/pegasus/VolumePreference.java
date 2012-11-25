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

import android.content.Context;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.SeekBar;

/**
 * Preference that opens a SeekBar dialog.
 * 
 * @author Felix Ableitner
 *
 */
public class VolumePreference extends DialogPreference {
	
	private int mMax;
	private int mProgress;
	private SeekBar mSeekBar;
	
	/**
	 * Initialize layout, set Preference not persistent-
	 */
    public VolumePreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        setPersistent(false);
        setDialogLayoutResource(R.layout.seekbar_dialog);
   
        setPositiveButtonText(android.R.string.ok);
        setNegativeButtonText(android.R.string.cancel);
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);

        mSeekBar = (SeekBar) view.findViewById(R.id.seekbar);
        
		mSeekBar.setProgress(mProgress);
		mSeekBar.setMax(mMax);	
    }
    
    /**
     * Set current position of the selector.
     */
    @Override
    public void setDefaultValue(Object defaultValue) {
		mProgress = (Integer) defaultValue;
    	if (mSeekBar != null) {
    		mSeekBar.setProgress((Integer) defaultValue);
    	}
    }
    
    /**
     * Set the maximum value for the SeekBar.
     */
    public void setMaxValue(int max) {
		mMax = max;
    	if (mSeekBar != null) {
    		mSeekBar.setMax(max);	
    	}
    }
	
    /**
     * Send new value to listener (if positive button was clicked).
     */
	@Override
	protected void onDialogClosed(boolean positiveResult) {
		if (positiveResult) {
			mProgress = mSeekBar.getProgress();
			callChangeListener(mProgress);			
		}
	}
}