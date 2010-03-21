/*
 * Copyright (C) 2010 Christopher Eby <kreed@kreed.org>
 *
 * This file is part of Vanilla Music Player.
 *
 * Vanilla Music Player is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Library General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Vanilla Music Player is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.kreed.vanilla;

import org.kreed.vanilla.IPlaybackService;
import org.kreed.vanilla.R;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

public class FullPlaybackActivity extends PlaybackActivity implements View.OnClickListener, SeekBar.OnSeekBarChangeListener, View.OnFocusChangeListener {
	private IPlaybackService mService;

	private RelativeLayout mMessageOverlay;
	private View mControlsTop;
	private View mControlsBottom;

	private View mPreviousButton;
	private ImageView mPlayPauseButton;
	private View mNextButton;
	private SeekBar mSeekBar;
	private TextView mSeekText;

	private int mState;
	private int mDuration;
	private boolean mSeekBarTracking;

	private static final int SONG_SELECTOR = 8;

	private static final int MENU_QUIT = 0;
	private static final int MENU_PREFS = 2;
	private static final int MENU_LIBRARY = 3;

	@Override
	public void onCreate(Bundle icicle)
	{
		super.onCreate(icicle);

		setContentView(R.layout.full_playback);

		mCoverView = (CoverView)findViewById(R.id.cover_view);
		mCoverView.setOnClickListener(this);

		mControlsTop = findViewById(R.id.controls_top);
		mControlsBottom = findViewById(R.id.controls_bottom);

		mPreviousButton = findViewById(R.id.previous);
		mPreviousButton.setOnClickListener(this);
		mPreviousButton.setOnFocusChangeListener(this);
		mPlayPauseButton = (ImageView)findViewById(R.id.play_pause);
		mPlayPauseButton.setOnClickListener(this);
		mPlayPauseButton.setOnFocusChangeListener(this);
		mNextButton = findViewById(R.id.next);
		mNextButton.setOnClickListener(this);
		mNextButton.setOnFocusChangeListener(this);

		mSeekText = (TextView)findViewById(R.id.seek_text);
		mSeekBar = (SeekBar)findViewById(R.id.seek_bar);
		mSeekBar.setMax(1000);
		mSeekBar.setOnSeekBarChangeListener(this);
		mSeekBar.setOnFocusChangeListener(this);
	}

	@Override
	protected void setState(int state)
	{
		if (state == mState)
			return;

		mState = state;

		if (mMessageOverlay != null)
			mMessageOverlay.setVisibility(View.GONE);

		switch (state) {
		case PlaybackService.STATE_PLAYING:
			if (!mHandler.hasMessages(HIDE))
				mControlsBottom.setVisibility(View.GONE);
			// fall through
		case PlaybackService.STATE_NORMAL:
			if (state == PlaybackService.STATE_NORMAL)
				mControlsBottom.setVisibility(View.VISIBLE);

			mPlayPauseButton.setImageResource(state == PlaybackService.STATE_PLAYING ? R.drawable.pause : R.drawable.play);
			break;
		case PlaybackService.STATE_NO_MEDIA:
			if (mMessageOverlay == null) {
				mMessageOverlay = new RelativeLayout(this);
				mMessageOverlay.setBackgroundColor(Color.BLACK);
				addContentView(mMessageOverlay,
					new ViewGroup.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT,
					                           LinearLayout.LayoutParams.FILL_PARENT));
			} else {
				mMessageOverlay.setVisibility(View.VISIBLE);
				mMessageOverlay.removeAllViews();
			}

			RelativeLayout.LayoutParams layoutParams =
				new RelativeLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
				                                LinearLayout.LayoutParams.WRAP_CONTENT);
			layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT);

			TextView text = new TextView(this);
			text.setText(R.string.no_songs);
			text.setLayoutParams(layoutParams);
			mMessageOverlay.addView(text);
			break;
		}
	}

	@Override
	protected void setService(IPlaybackService service)
	{
		super.setService(service);

		mService = service;

		if (service != null) {
			try {
				mDuration = service.getDuration();
			} catch (RemoteException e) {
			}
		}
	}

	@Override
	protected void onServiceChange(Intent intent)
	{
		super.onServiceChange(intent);

		if (mService != null) {
			try {
				mDuration = mService.getDuration();
				mHandler.sendEmptyMessage(UPDATE_PROGRESS);
			} catch (RemoteException e) {
				setService(null);
			}
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		menu.add(0, MENU_PREFS, 0, R.string.settings);
		menu.add(0, MENU_LIBRARY, 0, R.string.library);
		menu.add(0, MENU_QUIT, 0, R.string.quit);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu)
	{
		menu.findItem(MENU_LIBRARY).setEnabled(mState != PlaybackService.STATE_NO_MEDIA);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId()) {
		case MENU_QUIT:
			ContextApplication.quit(this);
			break;
		case MENU_PREFS:
			startActivity(new Intent(this, PreferencesActivity.class));
			break;
		case MENU_LIBRARY:
			showDialog(SONG_SELECTOR);
			break;
		}

		return true;
	}

	@Override
	public boolean onSearchRequested()
	{
		showDialog(SONG_SELECTOR);
		return false;
	}

	@Override
	public Dialog onCreateDialog(int id)
	{
		if (id == SONG_SELECTOR)
			return new SongSelector(this);
		return null;
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event)
	{
		switch (keyCode) {
		case KeyEvent.KEYCODE_DPAD_CENTER:
		case KeyEvent.KEYCODE_ENTER:
			onClick(mCoverView);
			return true;
		}

		return false;
	}

	private String stringForTime(int ms)
	{
		int seconds = ms / 1000;

		int hours = seconds / 3600;
		seconds -= hours * 3600;
		int minutes = seconds / 60;
		seconds -= minutes * 60;

		if (hours > 0)
			return String.format("%d:%02d:%02d", hours, minutes, seconds);
		else
			return String.format("%02d:%02d", minutes, seconds);
	}

	private void updateProgress()
	{
		if (mControlsTop.getVisibility() != View.VISIBLE)
			return;
		
		int position = 0;
		if (mService != null) {
			try {
				position = mService.getPosition();
			} catch (RemoteException e) {
			}
		}

		if (!mSeekBarTracking)
			mSeekBar.setProgress(mDuration == 0 ? 0 : (int)(1000 * position / mDuration));
		mSeekText.setText(stringForTime((int)position) + " / " + stringForTime(mDuration));
		
		long next = 1000 - position % 1000;
		mHandler.removeMessages(UPDATE_PROGRESS);
		mHandler.sendEmptyMessageDelayed(UPDATE_PROGRESS, next);
	}
	
	private void sendHideMessage()
	{
		mHandler.removeMessages(HIDE);
		mHandler.sendEmptyMessageDelayed(HIDE, 3000);
	}

	public void onClick(View view)
	{
		sendHideMessage();

		if (view == mCoverView) {
			if (mControlsTop.getVisibility() == View.VISIBLE) {
				mControlsTop.setVisibility(View.GONE);
				if (mState == PlaybackService.STATE_PLAYING)
					mControlsBottom.setVisibility(View.GONE);
			} else {
				mControlsTop.setVisibility(View.VISIBLE);
				mControlsBottom.setVisibility(View.VISIBLE);

				mPlayPauseButton.requestFocus();

				updateProgress();
			}
		} else {
			try {
				if (view == mNextButton)
					mCoverView.go(1);
				else if (view == mPreviousButton)
					mCoverView.go(-1);
				else if (view == mPlayPauseButton)
					mCoverView.go(0);
			} catch (RemoteException e) {
				Log.e("VanillaMusic", "service dead", e);
				setService(null);
			}
		}
	}

	private static final int HIDE = 0;
	private static final int UPDATE_PROGRESS = 1;

	private Handler mHandler = new Handler() {
		public void handleMessage(Message message) {
			switch (message.what) {
			case HIDE:
				mControlsTop.setVisibility(View.GONE);
				if (mState == PlaybackService.STATE_PLAYING)
					mControlsBottom.setVisibility(View.GONE);
				break;
			case UPDATE_PROGRESS:
				updateProgress();
				break;
			}
		}
	};

	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
	{
		if (!fromUser || mService == null)
			return;

		try {
			mService.seekToProgress(progress);
		} catch (RemoteException e) {
		}
	}

	public void onStartTrackingTouch(SeekBar seekBar)
	{
		mHandler.removeMessages(HIDE);
		mSeekBarTracking = true;
	}

	public void onStopTrackingTouch(SeekBar seekBar)
	{
		sendHideMessage();
		mSeekBarTracking = false;
	}

	public void onFocusChange(View v, boolean hasFocus)
	{
		if (hasFocus)
			sendHideMessage();
	}
}