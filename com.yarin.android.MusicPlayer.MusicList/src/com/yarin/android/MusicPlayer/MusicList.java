package com.yarin.android.MusicPlayer;

import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

public class MusicList extends ListActivity {
	private MusicPlayerService mMusicPlayerService = null;
	private MusicInfoController mMusicInfoController = null;
	private Cursor mCursor = null;

	private TextView mTextView = null;
	private Button mPlayPauseButton = null;
	private Button mStopButton = null;
	// a little bit winding...
	// bindService -> ServiceConnection -> IBinder
	private ServiceConnection mPlaybackConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			mMusicPlayerService = ((MusicPlayerService.LocalBinder) service)
					.getService();
		}

		public void onServiceDisconnected(ComponentName className) {
			mMusicPlayerService = null;
		}
	};

	// Tom Xue: Server will sendBroadcast (when some action is ready),
	// and then client will react after receive the broadcast
	protected BroadcastReceiver mPlayerEvtReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (action.equals(MusicPlayerService.PLAYER_PREPARE_END)) {
				// will begin to play
				mTextView.setVisibility(View.INVISIBLE);
				mPlayPauseButton.setVisibility(View.VISIBLE);
				mStopButton.setVisibility(View.VISIBLE);

				mPlayPauseButton.setText(R.string.pause);
			} else if (action.equals(MusicPlayerService.PLAY_COMPLETED)) {
				mPlayPauseButton.setText(R.string.play);
			}
		}
	};

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.list_layout);

		MusicPlayerApp musicPlayerApp = (MusicPlayerApp) getApplication();
		mMusicInfoController = (musicPlayerApp).getMusicInfoController();

		// bind/start playback service, refactor by Tom Xue
		Intent intent = new Intent(this, MusicPlayerService.class);
		bindService(intent,
				mPlaybackConnection, Context.BIND_AUTO_CREATE);
		startService(intent);

		mTextView = (TextView) findViewById(R.id.show_text);
		mPlayPauseButton = (Button) findViewById(R.id.play_pause_btn);
		mStopButton = (Button) findViewById(R.id.stop_btn);

		// toggle play/pause
		mPlayPauseButton.setOnClickListener(new Button.OnClickListener() {
			public void onClick(View v) {
				// Perform action on click
				if (mMusicPlayerService != null
						&& mMusicPlayerService.isPlaying()) {
					mMusicPlayerService.pause();
					mPlayPauseButton.setText(R.string.play);
				} else if (mMusicPlayerService != null) {
					mMusicPlayerService.start();
					mPlayPauseButton.setText(R.string.pause);
				}
			}
		});

		mStopButton.setOnClickListener(new Button.OnClickListener() {
			public void onClick(View v) {
				// Perform action on click
				if (mMusicPlayerService != null) {
					mTextView.setVisibility(View.VISIBLE);
					mPlayPauseButton.setVisibility(View.INVISIBLE);
					mStopButton.setVisibility(View.INVISIBLE);
					mMusicPlayerService.stop();
				}
			}
		});

		IntentFilter filter = new IntentFilter();
		filter.addAction(MusicPlayerService.PLAYER_PREPARE_END);
		filter.addAction(MusicPlayerService.PLAY_COMPLETED);
		// Register a BroadcastReceiver to be run in the main activity thread.
		registerReceiver(mPlayerEvtReceiver, filter);
	}

	protected void onResume() {
		super.onResume();
		// Tom Xue: a way to retrieve/refresh the songs, and notice to
		// disconnect the SD card with PC
		mCursor = mMusicInfoController.getAllSongs();

		// Tom Xue: associate the song list and the view
		ListAdapter adapter = new MusicListAdapter(this,
				android.R.layout.simple_expandable_list_item_2, mCursor,
				new String[] {}, new int[] {});
		setListAdapter(adapter);
	}

	protected void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);

		if (mCursor == null || mCursor.getCount() == 0) {
			return;
		}
		mCursor.moveToPosition(position);
		String url = mCursor.getString(mCursor
		// path: the path of the file, or the http/rtsp URL of the stream
		// you want to play
		// So here should be the stream data specified
		// below is my guess, for better understanding
		// row1: col1 col2 col3
		// row2: col1 col2 col3
		// song 1: TITLE DISPLAY_NAME DATA ...
		// song 2: TITLE DISPLAY_NAME DATA
				.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA));
		mMusicPlayerService.setDataSource(url);
		mMusicPlayerService.start();
	}
}

/**********************************
 * 
 *********************************/
class MusicListAdapter extends SimpleCursorAdapter {

	public MusicListAdapter(Context context, int layout, Cursor c,
			String[] from, int[] to) {
		super(context, layout, c, from, to);
	}

	// to bind additional meta data to the view, by Tom Xue
	public void bindView(View view, Context context, Cursor cursor) {

		super.bindView(view, context, cursor);

		TextView titleView = (TextView) view.findViewById(android.R.id.text1);
		TextView artistView = (TextView) view.findViewById(android.R.id.text2);

		titleView.setText(cursor.getString(cursor
		// music name/title
				.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)));

		artistView.setText(cursor.getString(cursor
		// music author/artist
				.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)));

		// int duration =
		// cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION));
	}

	// not used
	public static String makeTimeString(long milliSecs) {
		StringBuffer sb = new StringBuffer();
		long m = milliSecs / (60 * 1000);
		// why "0"+ ?
		sb.append(m < 10 ? "0" + m : m);
		sb.append(":");
		long s = (milliSecs % (60 * 1000)) / 1000;
		sb.append(s < 10 ? "0" + s : s);
		return sb.toString();
	}
}
