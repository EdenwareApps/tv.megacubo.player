package tv.megacubo.player;

import android.net.Uri;
import android.app.Dialog;
import android.graphics.Point;
import android.graphics.Color;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.Configuration;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.util.DisplayMetrics;
import android.view.Window;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Display; 
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.view.View.OnLayoutChangeListener;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.video.VideoListener;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.upstream.DefaultAllocator;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;
import java.util.HashMap;
import java.util.List;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;

public class MegacuboPlayerPlugin extends CordovaPlugin {

    private Uri uri;
    private String ua;
    private Context context;
    private CallbackContext mainCallbackContext;
    private CallbackContext eventsTrackingContext;
    
    private FrameLayout playerContainer;
    private FrameLayout.LayoutParams aspectRatioParams;
    
    private boolean isActive;
    private boolean isPlaying;
    private Handler mainHandler;
    private Handler handler;
    private Runnable timer;
    private float currentVolume = 0f;

    private SimpleExoPlayer player;
    private PlayerView playerView;
    private ViewGroup parentView;
    private DataSource.Factory dataSourceFactory;
    private Player.EventListener eventListener;
    private VideoListener videoListener;
    
    private static final int BUFFER_SEGMENT_SIZE = 64 * 1024;
    private static final int BUFFER_SEGMENT_COUNT = 256;

    private String currentPlayerState = "";
    private String currentURL = "";
    private String currentMimetype = "";
    private String currentCookie = "";
    private boolean viewAdded = false;
    private boolean sendEventEnabled = true;
    private boolean fixingStalledPlayback = false;
    private int videoWidth = 1280;
    private int videoHeight = 720;
    private int videoForcedWidth = 1280;
    private int videoForcedHeight = 720;
    private float videoForcedRatio = 1.7777777777777777f; // 16:9

	private String TAG = "MegacuboPlayerPlugin";
    private Timeline.Period period = new Timeline.Period();
    
    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        if(playerContainer == null) {

            Log.d(TAG, "init");

            context = this.cordova.getActivity().getApplicationContext();
            playerContainer = new FrameLayout(cordova.getActivity());

            mainHandler = new Handler();
            handler = new Handler();
            timer = new Runnable() {
                @Override
                public void run() {
                    cordova.getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            GetTimeData();
                            if(isPlaying){
                                handler.postDelayed(timer, 1000);
                            }
                        }
                    });
                }
            };

            // Player Event Listener
            eventListener = new Player.EventListener() {

                @Override
                public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                    // Player.STATE_IDLE, Player.STATE_BUFFERING, Player.STATE_READY or Player.STATE_ENDED.
                    String state = "";
                    if(playWhenReady){
                        switch(playbackState){
                            case Player.STATE_IDLE: // the player is stopped or playback failed.
                                state = "";
                                break;
                            case Player.STATE_ENDED: // finished playing all media.
                                state = "ended";
                                break;
                            case Player.STATE_BUFFERING: // not able to immediately play from its current position, more data needs to be loaded.
                                state = "loading";
                                break;
                            case Player.STATE_READY: // able to immediately play from its current position.
                                state = "playing";
                                break;
                        }
                    } else {
                        switch(playbackState){
                            case Player.STATE_IDLE:
                                state = "";
                                break;
                            case Player.STATE_ENDED:
                                state = "ended";
                                break;
                            case Player.STATE_BUFFERING:
                            case Player.STATE_READY:
                                state = "paused";
                                break;
                        }
                    }
                    currentPlayerState = state;
                    sendEvent("state", state, false);
                    if(playWhenReady && !fixingStalledPlayback && isPlaybackStalled()){
                        fixStalledPlayback();
                    }
                }

                @Override 
                public void onIsPlayingChanged(boolean playing){
                    isPlaying = playing;
                    sendEvent("playing", String.valueOf(isPlaying), false);
                    if(isPlaying) {
                        handler.postDelayed(timer, 0);
                    } else {
                        handler.removeCallbacks(timer);
                    }
                }

                @Override
                public void onPlayerError(ExoPlaybackException error) {
                    String what;
                    String errStr = error.toString();
					switch (error.type) {
						case ExoPlaybackException.TYPE_SOURCE:
							what = "Source error: " + error.getSourceException().getMessage();
							break;
						case ExoPlaybackException.TYPE_RENDERER:
							what = "Renderer error: " + error.getRendererException().getMessage();
							break;
						case ExoPlaybackException.TYPE_UNEXPECTED:
							what = "Unexpected error: " + error.getUnexpectedException().getMessage();
							break;
						default:
							what = "Unknown error: " + errStr;
					}
					String errStack = Log.getStackTraceString(error); 
					String errorFullStr = errStr + " " + what + " " + errStack;
                    if(
                    errorFullStr.indexOf("PlaylistStuck") != -1 || 
                    errorFullStr.indexOf("BehindLiveWindow") != -1 || 
                    errorFullStr.indexOf("InvalidResponseCode") != -1 || 
                    errorFullStr.indexOf("Most likely not a Transport Stream") != -1 ||
                    errorFullStr.indexOf("PlaylistResetException") != -1 || 
                    errorFullStr.indexOf("Renderer error") != -1
                    ){
                        sendEvent("state", "loading", false);
                        sendEventEnabled = false;
                        if(player != null){
							player.setPlayWhenReady(false);
							player.stop();
						}
                        MCPrepare();
                        // player.prepare();
                        setTimeout(() -> {                            
                            cordova.getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    player.setPlayWhenReady(true);
                                    sendEventEnabled = true;
                                    if(currentPlayerState != "loading"){
                                        sendEvent("state", currentPlayerState, false);
                                    }
                                }
                            });
                        }, 100);
                        Log.e(TAG, "*onPlayerError (auto-recovering) " + errStr + " " + what);
                    } else {
                        Log.e(TAG, "*onPlayerError (fatal) " + errStr +" "+ what);
                        sendEvent("error", "ExoPlayer error " + what, false);
                        MCStop();
                    }
                }
            };            
            parentView = (ViewGroup) webView.getView().getParent();
        };
        
        videoListener = new VideoListener() {

            @Override
            public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
                videoWidth = width;
                videoHeight = height;
                ResetAspectRatio();
            }

        };
		
		webView.getView().addOnLayoutChangeListener(new OnLayoutChangeListener() {
			@Override
			public void onLayoutChange(View v, int left, int top, int right,
					int bottom, int oldLeft, int oldTop, int oldRight,
					int oldBottom) {
				GetAppMetrics();
			}
		});

        Log.d(TAG, "We were initialized");	
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        //noinspection IfCanBeSwitch using if instead of switch in order to maintain compatibility with Java 6 projects
        Log.d(TAG, action);
        if(action.equals("bind")) {
            if(callbackContext != null) {
                eventsTrackingContext = callbackContext;
            }
            ua = args.getString(0);
            Log.d(TAG, "binding events bridge");
            if(callbackContext == null) {
                Log.d(TAG, "bind called with null");
            }
        } else {
            if (action.equals("play")) {
                cordova.getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            MCLoad(args.getString(0), args.getString(1), args.getString(2), callbackContext);
                        } catch (JSONException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
            } else if(action.equals("restart")) {
                MCRestartApp();
            } else if(action.equals("getAppMetrics")) { 
                GetAppMetrics();
            } else if(isActive) {                
                cordova.getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if(action.equals("pause")) {
                                MCPause();
                            } else if (action.equals("resume")) {
                                MCResume();
                            } else if (action.equals("seek")) {
                                MCSeek(args.getInt(0) * 1000);
                            } else if (action.equals("stop")) {
                                MCStop();
                            } else if(action.equals("mute")) {            
                                MCMute(args.getBoolean(0));
                            } else if(action.equals("volume")) {        
                                MCVolume(args.getInt(0) / 100);
                            } else if(action.equals("ratio")) {  
                                float ratio = Float.valueOf(args.getString(0));
                                MCRatio(ratio);
                            } else if(action.equals("rate")) {
                                float rate = Float.valueOf(args.getString(0));
                                PlaybackParameters param = new PlaybackParameters(rate);
                                player.setPlaybackParameters(param);
                            }
                        } catch (JSONException e) {
                            throw new RuntimeException(e);
                        } 
                    }
                });
            }
        }
        return true;
    }
    
    @Override
    public void onConfigurationChanged(Configuration newConfig){
        super.onConfigurationChanged(newConfig);
        GetAppMetrics();
    }
            
    public void GetAppMetrics() {
        // status bar height
        Resources resources = context.getResources();
        float scaleRatio = resources.getDisplayMetrics().density;
        int statusBarHeight = 0;
        int top = 0;
        int bottom = 0;
        int right = 0;
        int left = 0;
        int resourceId = resources.getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            statusBarHeight = resources.getDimensionPixelSize(resourceId);
            statusBarHeight = (int) (((float)statusBarHeight) / scaleRatio);
        }

        // navigation bar height
        int navigationBarHeight = 0;
        resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android");
        if (resourceId > 0) {
            navigationBarHeight = resources.getDimensionPixelSize(resourceId);
            navigationBarHeight = (int) (((float)navigationBarHeight) / scaleRatio);
        }
        
        Window win = cordova.getActivity().getWindow();
        Display display = win.getWindowManager().getDefaultDisplay();
		Point screenSize = new Point();
		Point usableSize = new Point();
		display.getRealSize(screenSize);
        display.getSize(usableSize);

        top = statusBarHeight;
        if (usableSize.x == screenSize.x) {
			bottom = navigationBarHeight;
		} else {
			int rotation = display.getRotation();  
			if(rotation == 3 && Build.VERSION.SDK_INT > Build.VERSION_CODES.N){
				left = navigationBarHeight;
			}  else {
				right = navigationBarHeight;
			}
		}
        
        sendEvent("appMetrics", "{\"top\":" + top + ",\"bottom\":" + bottom + ",\"right\":" + right + ",\"left\":" + left + "}", true);
    }

    public void Seek(long to){ // TODO, on live streams, we're unable to seek back too much, why?!        
        if(isActive){
            boolean debug = true;
            Timeline timeline = player.getCurrentTimeline();
            int currentWindowIndex = player.getCurrentWindowIndex();
            long offset = player.getCurrentPosition();
            long position;
            long currentPosition = player.getCurrentPosition();
            if (timeline != null) {
                offset = (timeline.getPeriod(player.getCurrentPeriodIndex(), period).getPositionInWindowMs() * -1);
                position = offset + currentPosition;

                if(debug){
                    Log.d(TAG, "SEEK DEBUG " + to + " :: " + offset);
                }

                if(to < offset){
                    to = 0; // zero after offset
                } else {
                    to = to - offset;
                }

                if(debug){
                    Log.d(TAG, "SEEKTO " + to + " " + currentPosition);
                }

                player.seekTo(to);

                if(debug){
                    Log.d(TAG, "SEEKED " + to + " " + player.getCurrentPosition());
                }

                GetTimeData();
            }
        }
    }

    public boolean isPlaybackStalled(){        
        if(sendEventEnabled && isActive && currentPlayerState == "loading" && currentMimetype.toLowerCase().indexOf("mpegurl") != -1){
            Timeline timeline = player.getCurrentTimeline();
            long currentPosition = player.getCurrentPosition();
            long position = 0;
            long duration = 0;
            long offset = 0;
            
            if (!timeline.isEmpty()) {
                offset = (timeline.getPeriod(player.getCurrentPeriodIndex(), period).getPositionInWindowMs() * -1);
                position = offset + currentPosition;
                if(player.isCurrentWindowLive()){
                    duration = offset + currentPosition + player.getTotalBufferedDuration();
                } else {
                    duration = offset + player.getDuration();
                }
                return (position < duration - 3);
            }
        }
        return false;
    }

    public void fixStalledPlayback(){   
        if(isPlaybackStalled()){
            if(fixingStalledPlayback){
                Log.d(TAG, "nudging currentTime by +500ms");
                long newTime = player.getCurrentPosition() + 500;
                player.seekTo(newTime);
            } else {
                fixingStalledPlayback = true; // give a initial 500ms delay to siuation fix itself
            }
            setTimeout(() -> {                            
                cordova.getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        fixStalledPlayback();
                    }
                });
            }, 500);
        } else {
            fixingStalledPlayback = false;
        }
    }

    public void GetTimeData(){        
        if(isActive){
            Timeline timeline = player.getCurrentTimeline();
            long currentPosition = player.getCurrentPosition();
            long position = 0;
            long duration = 0;
            long offset = 0;
            
            if (!timeline.isEmpty()) {
                offset = (timeline.getPeriod(player.getCurrentPeriodIndex(), period).getPositionInWindowMs() * -1);

                position = offset + currentPosition;

                if(player.isCurrentWindowLive()){
                    duration = offset + currentPosition + player.getTotalBufferedDuration();
                } else {
                    duration = offset + player.getDuration();
                }

                sendEvent("time", "{\"currentTime\":" + position + ",\"duration\":" + duration + "}", false);
            }
        }
    }

    public void ApplyAspectRatio(float ratio){
        if(isActive){
            videoForcedRatio = ratio;
            videoForcedHeight = (int) (videoWidth / videoForcedRatio);
            
            int screenHeight = webView.getView().getHeight();
            int screenWidth = webView.getView().getWidth();
            float screenRatio = (float)screenWidth / screenHeight; // cast one of the operands to float

            if(videoForcedRatio > screenRatio){
                videoForcedWidth = screenWidth;
                videoForcedHeight = (int) (screenWidth / videoForcedRatio);
            } else {
                videoForcedHeight = screenHeight;
                videoForcedWidth = (int) (screenHeight * videoForcedRatio);
            }

            //Log.d(TAG, "RATIO: " + videoForcedWidth + "x" + videoForcedHeight + "(" + videoForcedRatio + ") , SCREEN: " + screenWidth + "x" + screenHeight + " (" + screenRatio + ") ");
            
            aspectRatioParams.gravity = Gravity.CENTER;
            aspectRatioParams.width = videoForcedWidth;
            aspectRatioParams.height = videoForcedHeight;

            playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FILL);
            playerContainer.setLayoutParams(aspectRatioParams);
            playerContainer.requestLayout();
            playerView.requestLayout();

            sendEvent("ratio", "{\"ratio\":" + videoForcedRatio + ",\"width\":" + videoWidth + ",\"height\":" + videoHeight + "}", false);
        }
    }

    public void ResetAspectRatio(){
        if(isActive){
            videoWidth = 1280;
            videoHeight = 720;
            videoForcedHeight = 720;
            videoForcedRatio = 1.7777777777777777f;

            aspectRatioParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
            aspectRatioParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
            
            Log.d(TAG, "ratio reset");
            playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
            playerContainer.setLayoutParams(aspectRatioParams);

            sendEvent("ratio", "{\"ratio\":" + videoForcedRatio + ",\"width\":" + videoWidth + ",\"height\":" + videoHeight + "}", false);
        }
    }

    public MediaSource getMediaSource(String u, String mimetype, String cookie) {
        MediaItem mediaItem = new MediaItem.Builder()
            .setUri(Uri.parse(u))
            .setMimeType​(mimetype)
            .build();
        Log.d(TAG, "MEDIASOURCE " + u + ", " + mimetype + ", " + ua + ", " + cookie);
        DefaultHttpDataSourceFactory httpFactory = new DefaultHttpDataSourceFactory(ua, null);
        httpFactory.getDefaultRequestProperties().set("Cookie", cookie);       
        DefaultDataSourceFactory factory = new DefaultDataSourceFactory(context, null, httpFactory);
        if(mimetype.toLowerCase().indexOf("mpegurl") != -1){
            return new HlsMediaSource.Factory(factory).createMediaSource(mediaItem);
        } else {
            return new DefaultMediaSourceFactory(factory).createMediaSource(mediaItem);
        }
    }

    private void MCLoad(String uri, String mimetype, String cookie, final CallbackContext callbackContext) {
        mainCallbackContext = callbackContext;

        currentURL = uri;
        currentMimetype = mimetype;
        currentCookie = cookie;
        
        MCPrepare();

        PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
        pluginResult.setKeepCallback(false);
        callbackContext.sendPluginResult(pluginResult);
    }

    private void MCPrepare() {
        initMegacuboPlayer();

        // player!!.audioAttributes = AudioAttributes.Builder().setFlags(C.FLAG_AUDIBILITY_ENFORCED).setUsage(C.USAGE_NOTIFICATION_RINGTONE).setContentType(C.CONTENT_TYPE_SPEECH).build()

        MediaSource mediaSource = getMediaSource(currentURL, currentMimetype, currentCookie);
        player.setMediaSource(mediaSource, true);
        player.prepare();
        player.setPlayWhenReady(true);
    }

    private void initMegacuboPlayer() {
        if(!isActive){
            isActive = true;
            if(player == null){
                webView.getView().setBackgroundColor(android.R.color.transparent);
                playerView = new PlayerView(context); 
                player = new SimpleExoPlayer.Builder(context).build();
                playerView.setUseController(false); 
                playerView.setPlayer(player);
                player.setHandleAudioBecomingNoisy(true);
                player.setHandleWakeLock(true);
                player.setSeekParameters(SeekParameters.CLOSEST_SYNC);
                player.setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT);
                player.addListener(eventListener);
                player.addVideoListener(videoListener);
                playerContainer.addView(playerView);
                aspectRatioParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                parentView.setBackgroundColor(Color.BLACK);
            }
            if(!viewAdded){
                viewAdded = true;
                parentView.addView(playerContainer, 0, aspectRatioParams);
            }
        }
    }

    public void sendEvent(String type, String data, boolean force){
        if(sendEventEnabled && isActive){
			force = true;
        }
        if(force && eventsTrackingContext != null) {
            JSONObject json = new JSONObject();
            try {
                json.put("type", type);
                json.put("data", data);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }  
            PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, json);
            pluginResult.setKeepCallback(true);
            eventsTrackingContext.sendPluginResult(pluginResult);
        }
    }

    private void MCRatio(float ratio){        
        ApplyAspectRatio(ratio);
    }

    private void MCVolume(float volume){
        if(isActive){
            player.setVolume(volume);
        }
    }

    private void MCMute(boolean doMute) {
        if(isActive){
            float volume = player.getVolume();
            if(currentVolume == 0f || volume != 0f){
                currentVolume = volume;
            }
            if(doMute == true){
                player.setVolume(0f);
            } else {
                player.setVolume(currentVolume);
            }
        }
    }

    private void MCSeek(long to) {   
        if(isActive){
            Seek(to);
        }
    }

    private void MCResume() {        
        if(isActive){
            if(currentPlayerState == "ended"){
                player.seekTo(0);
            }
            player.setPlayWhenReady(true);
        }
    }
    
    private void MCPause() {
        if(isActive){
            player.setPlayWhenReady(false);
        }
    }

	private void MCStop() {
        Log.d(TAG, "Stopping video.");
        isActive = false;
		if(player != null){
			player.setPlayWhenReady(false);
			player.seekTo(0);
			player.stop();
			player.release();
		}
        if(viewAdded){
            viewAdded = false;
            Log.d(TAG, "view found - removing container");
            parentView.removeView(playerContainer);
        }
        player = null;
    }

    private void MCRestartApp(){
		MCStop();
        String baseError = "Unable to cold restart application: ";
        try {
            Log.d(TAG, "Cold restarting application");
            if (context != null) {
                //fetch the packagemanager so we can get the default launch activity
                // (you can replace this intent with any other activity if you want
                PackageManager pm = context.getPackageManager();
                if (pm != null) {
                    //create the intent with the default start activity for your application
                    Intent mStartActivity = pm.getLaunchIntentForPackage(
                            context.getPackageName()
                    );
                    if (mStartActivity != null) {
                        //mStartActivity.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        //create a pending intent so the application is restarted after System.exit(0) was called.
                        // We use an AlarmManager to call this intent in 100ms
                        int mPendingIntentId = 223344;
                        PendingIntent mPendingIntent = PendingIntent
                                .getActivity(context, mPendingIntentId, mStartActivity,
                                        PendingIntent.FLAG_CANCEL_CURRENT);
                        AlarmManager mgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                        Log.i(TAG,"Killing application for cold restart");
                        mgr.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 100, mPendingIntent);
                        //kill the application
                        //this.cordova.getActivity().finish();
                        System.exit(0);
                        //android.os.Process.killProcess(android.os.Process.myPid());
                    } else {
                        Log.d(TAG, baseError+"StartActivity is null");
                    }
                } else {
                    Log.d(TAG, baseError+"PackageManager is null");
                }
            } else {
                Log.d(TAG, baseError+"Context is null");
            }
        } catch (Exception ex) {
            Log.d(TAG, baseError+ ex.getMessage());
        }
    }
    
    public static void setTimeout(Runnable runnable, int delay){
        new Thread(() -> {
            try {
                Thread.sleep(delay);
                runnable.run();
            }
            catch (Exception e){
                System.err.println(e);
                Log.d("MegacuboPlayerPlugin", "setTimeout error "+ e.getMessage());
            }
        }).start();
    }

	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.d(TAG, "onDestroy triggered.");
		MCStop();
	}
}
