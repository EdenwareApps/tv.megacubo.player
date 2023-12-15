package tv.megacubo.player;

import android.net.Uri;
import android.net.NetworkInfo;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.app.Activity;
import android.app.ActivityManager;
import android.graphics.Point;
import android.graphics.Color;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
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
import android.view.KeyCharacterMap;
import android.view.ViewConfiguration;
import android.view.KeyEvent;
import android.view.View.OnLayoutChangeListener;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import com.google.common.collect.ImmutableList;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.Tracks;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.upstream.DefaultAllocator;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.mp3.Mp3Extractor;
import com.google.android.exoplayer2.extractor.ts.AdtsExtractor;
import com.google.android.exoplayer2.extractor.ts.TsExtractor;
import com.google.android.exoplayer2.extractor.ts.DefaultTsPayloadReaderFactory;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionOverride;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector.MappedTrackInfo;
import com.google.android.exoplayer2.ui.DefaultTrackNameProvider;
import com.google.android.exoplayer2.video.VideoSize;

import androidx.appcompat.app.AppCompatDelegate;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteOrder;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.net.UnknownHostException;
import java.net.NetworkInterface;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedList;

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
    private CallbackContext eventsTrackingContext;
    
    private FrameLayout playerContainer;
    private FrameLayout.LayoutParams aspectRatioParams;
    
    private boolean isActive;
    private boolean isPlaying;
    private float currentVolume = 1f;

    private ExoPlayer player;
    private PlayerView playerView;
    private ViewGroup parentView;
    private DataSource.Factory dataSourceFactory;
    private Player.Listener eventListener;
    private Player.Listener videoListener;
    
    private static final int BUFFER_SEGMENT_SIZE = 64 * 1024;
    private static final int BUFFER_SEGMENT_COUNT = 256;

    private String currentPlayerState = "";
    private String currentURL = "";
    private String currentMediatype = "";
    private String currentMimetype = "";
    private boolean currentMimetypeIsHLS;
    private boolean currentMimetypeIsDash;
    private boolean inLiveStream;
    private String currentCookie = "";
    private String currentSubtitle = "";
    private float currentPlaybackRate = 1;
    private boolean checkedMiUi = false;
    private boolean viewAdded = false;
    private boolean sendEventEnabled = true;
    private long lastVideoTime = -1;
    private long videoLoadingSince = -1;
    private long currentStreamStartMs = -1;
    private int videoWidth = 1280;
    private int videoHeight = 720;
    private int videoForcedWidth = 1280;
    private int videoForcedHeight = 720;
	private int hlsMinPlaylistWindowTime = 6;
    private float videoForcedRatio = 1.7777777777777777f; // 16:9

	private String TAG = "MegacuboPlayerPlugin";
    private Timeline.Period period = new Timeline.Period();            
	private Runnable timer;
	private Handler handler;
	private boolean uiVisible = true;
	private boolean hasPermanentKeys;
	private DefaultTrackSelector trackSelector;
    
    private static List<Long> errorCounter = new LinkedList<Long>();

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
		
		context = this.cordova.getActivity().getApplicationContext();
		handler = new Handler();		
		timer = new Runnable() {
			@Override
			public void run() {
				if(isPlaying && uiVisible){
					cordova.getActivity().runOnUiThread(new Runnable() {
						@Override
						public void run() {
							SendTimeData(false);
						}
					});
					handler.postDelayed(timer, 1000);
				}
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
		
		hasPermanentKeys = checkHasPermanentKeys();
        Log.d(TAG, "We're initialized.");	
    }
    
    public boolean checkHasPermanentKeys() {
		// ViewConfiguration.get(context).hasPermanentMenuKey()
    
        Resources resources = context.getResources();
        int snb = resources.getIdentifier("config_showNavigationBar", "bool", "android");
        boolean snbb = resources.getBoolean(snb);
        if(!snbb){
			Log.d(TAG, "checkHasPermanentKeys(): config_showNavigationBar=false");	
			//return true;
        }
        
		int width = 0;
		int realWidth = 0;
		int height = 0;
		int realHeight = 0;
		Activity activity = cordova.getActivity();

		WindowManager w = activity.getWindowManager();
		Display d = w.getDefaultDisplay();
		DisplayMetrics metrics = new DisplayMetrics();
		d.getMetrics(metrics);
		width = metrics.widthPixels;
		height = metrics.heightPixels;

		if (Build.VERSION.SDK_INT >= 14 && Build.VERSION.SDK_INT < 17){ // includes window decorations (statusbar bar/menu bar)
			try {
				realHeight = (Integer) Display.class.getMethod("getRawHeight").invoke(d);
				realWidth = (Integer) Display.class.getMethod("getRawWidth").invoke(d);
			} catch (Exception ignored) { }
		}
		if (Build.VERSION.SDK_INT >= 17){ // includes window decorations (statusbar bar/menu bar)
			try {
				Point realSize = new Point();
				Display.class.getMethod("getRealSize", Point.class).invoke(d, realSize);
				realHeight = realSize.y;
				realWidth = realSize.x;
			} catch (Exception ignored) { }
		}
		Log.d(TAG, "checkHasPermanentKeys(): "+ Boolean.toString(ViewConfiguration.get(context).hasPermanentMenuKey()) +", "+ Boolean.toString(KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_BACK)) +", "+ Boolean.toString(KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_HOME)));        
		Log.d(TAG, "checkHasPermanentKeys(): "+ height +" == "+ realHeight +", "+ width +" == "+ realWidth);
		if(height == realHeight && width == realWidth){
			return true;
		}
		else{
			return false;
		}
	}

	public static boolean isMiUi() {
        return !TextUtils.isEmpty(getSystemProperty("ro.miui.ui.version.name"));
    }

    public static String getSystemProperty(String propName) {
        String line;
        BufferedReader input = null;
        try {
            java.lang.Process p = Runtime.getRuntime().exec("getprop " + propName);
            input = new BufferedReader(new InputStreamReader(p.getInputStream()), 1024);
            line = input.readLine();
            input.close();
        } catch (IOException ex) {
            return null;
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return line;
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
                            uiVisible = true;
                            MCLoad(args.getString(0), args.getString(1), args.getString(2), args.getString(3), args.getString(4), callbackContext);
							if(!checkedMiUi){
								checkedMiUi = true;
								String MiUi = isMiUi() ? "true" : "false";
								int nightMode = AppCompatDelegate.getDefaultNightMode();
        						sendEvent("nightMode", "{\"mode\":" + nightMode + ",\"miui\":" + MiUi + "}", true);
							}
                        } catch (JSONException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
            } else if(action.equals("getAppMetrics")) { 
                GetAppMetrics();
            } else if(action.equals("getNetworkIp")) { 
				sendEvent("networkIp", "\""+ getWifiIp() +"\"", true);
            } else if(action.equals("restart")) {
                MCRestartApp();
            } else if(isActive) {                
                cordova.getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if(action.equals("pause")) {
                                MCPause();
                            } else if (action.equals("resume")) {
                                MCResume();
                            } else if (action.equals("seekBy")) {
								Log.e(TAG, "MCSeekBy("+ (args.getInt(0) * 1000) +")");
                                MCSeekBy(args.getInt(0) * 1000);
                            } else if (action.equals("stop")) {
                                MCStop();
                            } else if(action.equals("mute")) {            
                                MCMute(args.getBoolean(0));
                            } else if(action.equals("volume")) {        
                                MCVolume(args.getInt(0));
                            } else if(action.equals("ratio")) {  
                                float ratio = Float.valueOf(args.getString(0));
                                MCRatio(ratio);
                            } else if(action.equals("audioTrack")) {  
                                String trackId = args.getString(0);
                                audioTrack(trackId);
                            } else if(action.equals("subtitleTrack")) {  
                                String trackId = args.getString(0);
                                subtitleTrack(trackId);
                            } else if(action.equals("rate")) {
                                float rate = Float.valueOf(args.getString(0));
                                currentPlaybackRate = rate;
                                MCPlaybackRate(rate);
                            } else if(action.equals("ui")) {            
                                uiVisible = args.getBoolean(0);
                                if(uiVisible){
									SendTimeData(true);
									if(isPlaying){
										handler.postDelayed(timer, 0);
									}
                                }
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
    
    public boolean hasNavigationBar(Resources resources) {
        /*
        config_showNavigationBar is not trusteable
        int id = resources.getIdentifier("config_showNavigationBar", "bool", "android");
        if(id > 0){
            Log.d(TAG, "config_showNavigationBar=" + id +", "+ resources.getBoolean(id));
			return resources.getBoolean(id);
		} else {		
		*/
		// !(KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_BACK) && KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_HOME)); returns true incorrectly on my phone
		// https://stackoverflow.com/questions/16092431/check-for-navigation-bar
		return !hasPermanentKeys;
    }
        
    protected String getWifiIp() {
		String host = "";
		try {
			WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
			if (wifiManager != null && wifiManager.isWifiEnabled()) {
				int ipAddress = wifiManager.getConnectionInfo().getIpAddress();
				// Convert little-endian to big-endianif needed
				if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
					ipAddress = Integer.reverseBytes(ipAddress);
				}
				byte[] ipByteArray = BigInteger.valueOf(ipAddress).toByteArray();
				host = InetAddress.getByAddress(ipByteArray).getHostAddress();
				if(isIPv4NetworkIP(host)){
					Log.d(TAG, "getWifiIp() "+ host);
					return host;
				}
			}
		} catch (UnknownHostException ex) {
			Log.e(TAG, "getWifiIp() Unable to get host address.", ex);
		}
		return "";
	}
    
    public boolean isIPv4NetworkIP(String addr) {
        if(addr != null){
			if(addr.indexOf("10.") == 0 || addr.indexOf("172.") == 0 || addr.indexOf("192.") == 0){
				return true;
			}
		}
		return false;
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
        int actionBarHeight = 0;
        if(hasNavigationBar(resources)){
			resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android");
			if (resourceId > 0) {
				actionBarHeight = resources.getDimensionPixelSize(resourceId);
				actionBarHeight = (int) (((float)actionBarHeight) / scaleRatio);
			}
        }
        
        Window win = cordova.getActivity().getWindow();
        Display display = win.getWindowManager().getDefaultDisplay();
		Point screenSize = new Point();
		Point usableSize = new Point();
		display.getRealSize(screenSize);
        display.getSize(usableSize);

        top = statusBarHeight;
        if (usableSize.x == screenSize.x) {
			bottom = actionBarHeight;
		} else {
			int rotation = display.getRotation();  
			if(rotation == 3 && Build.VERSION.SDK_INT > Build.VERSION_CODES.N){
				left = actionBarHeight;
			}  else {
				right = actionBarHeight;
			}
		}
        
        sendEvent("appMetrics", "{\"top\":" + top + ",\"bottom\":" + bottom + ",\"right\":" + right + ",\"left\":" + left + "}", true);
    }

    public boolean isPlaybackStalled(){
		if(isActive && currentPlayerState.equals("loading") && (currentMimetypeIsHLS || currentMimetypeIsDash)){
			long now = System.currentTimeMillis();
			long elapsed = (now - videoLoadingSince) / 1000;
			if(elapsed >= 5){
				int remainingTime = GetRemainingTime();
				//Log.d(TAG, "isPlaybackStalled MAYBE " + remainingTime);
				return remainingTime > hlsMinPlaylistWindowTime;
			}
        }
        return false;
    }
    
    public void nudge(){
		Map<String, Long> data = GetTimeData();
		long duration = data.get("duration");
		long position = data.get("position");
		int remainingTime = ((int) (duration - position)) / 1000;
		//Log.d(TAG, "Playback seems stalled for "+ elapsed + "s, remainingTime: " + remainingTime + "s");
		if(remainingTime > hlsMinPlaylistWindowTime){
			// nao deve ser menor que duration - 30 
			// nem menor que 10
			// nao pode ser maior que liveduration - 3			
			long newPosition = position + 10000;
			long minNewPosition = duration - 30000;
			long maxNewPosition = duration - 3000;
			if(newPosition < minNewPosition){
				newPosition = minNewPosition;
			}
			if(newPosition > maxNewPosition){
				newPosition = maxNewPosition;
			}			
			Log.d(TAG, "nudging currentTime, from "+ position + "ms to "+ newPosition +"ms, duration="+ duration + ", getDuration="+ player.getDuration());
			player.seekTo(newPosition);
			Log.d(TAG, "nudged currentTime " + player.getCurrentPosition());
		}
		setTimeout(() -> {                            
			cordova.getActivity().runOnUiThread(new Runnable() {
				@Override
				public void run() {
					fixStalledPlayback();
				}
			});
		}, 5000);
    }

    public void fixStalledPlayback(){ 
		int recheckAfter = 5000;
        if(isPlaybackStalled()){
			long now = System.currentTimeMillis();
			long elapsed = (now - videoLoadingSince) / 1000;
			if(elapsed < 5){
				//Log.d(TAG, "Playback seems stalled for "+ elapsed + "s");
				recheckAfter = (5 - (int)elapsed) * 1000;
			} else {
				videoLoadingSince = now;
				nudge();
				recheckAfter = 10000;
            }
        }
		//Log.d(TAG, "Playback doesn't seems stalled");
		if(currentPlayerState.equals("loading")){
			setTimeout(() -> {                            
				cordova.getActivity().runOnUiThread(new Runnable() {
					@Override
					public void run() {
						fixStalledPlayback();
					}
				});
			}, recheckAfter);
		}
    }

    public Map<String, Long> GetTimeData(){
		Map<String, Long> m = new HashMap<String, Long>();
		long rawPosition = player.getCurrentPosition(); 
		long currentPosition = rawPosition; 
		long duration = 0;         
		long offset = 0;
		if(currentMediatype.equals("live")){
			Timeline timeline = player.getCurrentTimeline(); 
			if (!timeline.isEmpty()) {
				offset = timeline.getPeriod(player.getCurrentPeriodIndex(), period).getPositionInWindowMs();
				currentPosition -= offset;	
			}
			duration = currentPosition + player.getTotalBufferedDuration();
			long pduration = player.getDuration();
			if(pduration > duration){
				duration = pduration;
			}
		} else {
			duration = player.getDuration();
		}
		if(duration < currentPosition){
			duration = currentPosition;
		}
		lastVideoTime = currentPosition;
		m.put("position", currentPosition);
		m.put("duration", duration);
		return m;
	}
    
    public void SendTimeData(boolean force){        
        if(isActive && sendEventEnabled){
            Map<String, Long> data = GetTimeData();
			sendEvent("time", "{\"currentTime\":" + data.get("position") + ",\"duration\":" + data.get("duration") + "}", false);
        }
    }

    public int GetRemainingTime(){
        if(isActive){
            Map<String, Long> data = GetTimeData();
			long duration = data.get("duration");
			long position = data.get("position");
			//Log.d(TAG, "GetRemainingTime " + duration + " | "+ position + " | "+ (int)(duration - position));
			return (int)(duration - position) / 1000;
        } else {
			return 0;
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

    private void MCLoad(String uri, String mimetype, String subtitles, String cookie, String mediatype, final CallbackContext callbackContext) {
        currentURL = uri;
        currentMimetype = mimetype;
        currentMimetypeIsHLS = mimetype.toLowerCase().indexOf("mpegurl") != -1;
        currentMimetypeIsDash = mimetype.toLowerCase().indexOf("dash") != -1;
        inLiveStream = mediatype.equals("live");
        currentMediatype = mediatype;
		currentSubtitle = subtitles;
        currentCookie = cookie;

        if(playerView != null){
			playerView.setKeepContentOnPlayerReset(false);
		}
        resetErrorCounter();
        MCPrepare(true);

        PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
        pluginResult.setKeepCallback(false);
        callbackContext.sendPluginResult(pluginResult);
    }

	public void MCSeekBy(long toMsRelativeToCurrentTime) {
		if (!isActive){
			return;
		}
		
		// Get the current window index
		Timeline timeline = player.getCurrentTimeline();
		int windowIndex = player.getCurrentWindowIndex();

		Log.d(TAG, "Current window is "+ windowIndex);

		// Calculate the relative playback position
		long seekPosition = player.getCurrentPosition() + toMsRelativeToCurrentTime;

		// Loop through previous windows while seekPosition is negative and windowIndex is valid
		while (seekPosition < 0 && windowIndex > 0) {
			windowIndex--;

			// Get the duration of the previous window
			Timeline.Window window = new Timeline.Window();
			try {
				Log.d(TAG, "Trying to get window "+ windowIndex);
				timeline.getWindow(windowIndex, window);
			} catch (NullPointerException e) {
				// there is no previous window
				break;
			}

			if (window == null) {
				// The window at the specified index is null, so there is no previous window
				break;
			}

			long duration = window.getDurationMs();

			// Check if duration is unknown
			if (duration == C.TIME_UNSET) {
				break;
			}

			// Update seekPosition with previous window duration
			seekPosition += duration;
		}

		// Seek to the target window and position
		player.seekTo(windowIndex, Math.max(0, seekPosition));
	}
    
    private void MCPlaybackRate(float rate){
		if(isActive){
			Log.d(TAG, "Set playback rate to " + rate);
			PlaybackParameters param = new PlaybackParameters(rate);
			player.setPlaybackParameters(param);
		}
    }

    private void MCPrepare(boolean resetPosition) {
		currentPlaybackRate = 1;
        initMegacuboPlayer();
        // player!!.audioAttributes = AudioAttributes.Builder().setFlags(C.FLAG_AUDIBILITY_ENFORCED).setUsage(C.USAGE_NOTIFICATION_RINGTONE).setContentType(C.CONTENT_TYPE_SPEECH).build()
        MediaSource mediaSource = getMediaSource(currentURL, currentMimetype, currentSubtitle, currentCookie);
        if(resetPosition){
			long startFromZero = 0;
			player.setMediaSource(mediaSource, startFromZero);
		} else {
			player.setMediaSource(mediaSource, false);
		}
        player.prepare();
        player.setPlayWhenReady(true);
        player.setVolume(currentVolume);
        webView.getView().setBackgroundColor(android.R.color.transparent);
    }
    
    private static int increaseErrorCounter(){
        errorCounter.add(System.currentTimeMillis());
        int length = errorCounter.size();
        long lastSecs = System.currentTimeMillis() - (1000 * 10); // last 10 seconds
        while(length > 0 && errorCounter.get(0) < lastSecs){
            errorCounter.remove(0);
            length--;
        }
        return length;
    }
    
    private static void resetErrorCounter(){
        errorCounter.clear();
    }
	
	private class PlayerEventListener implements Player.Listener {

		@Override
		public void onPlayerError(PlaybackException error) {
            player.setPlayWhenReady(false);
			Map<String, Long> data = GetTimeData();
			String what = "";
			String errStr = error.toString();
			String playbackPosition = data.get("position") +"-"+ data.get("duration");
			int errorCount = increaseErrorCounter();
			if(errorCount >= 4){
				Log.e(TAG, "onPlayerError (fatal, "+ errorCount +" errors) " + errStr +" "+ what +" "+ playbackPosition);
				sendEvent("error", "ExoPlayer error " + what, false);
				MCStop();
				return;
			}
			String errStack = Log.getStackTraceString(error); 
			String errorFullStr = errStr + " " + what + " " + errStack;
			boolean shouldReopen = (
				errorFullStr.indexOf("PlaylistStuck") != -1 || 
				errorFullStr.indexOf("Most likely not a Transport Stream") != -1 ||
				errorFullStr.indexOf("PlaylistResetException") != -1 || 
				errorFullStr.indexOf("Unable to connect") != -1 || 
				errorFullStr.indexOf("Response code: 404") != -1
			);
			sendEvent("state", "loading", false);
			SendTimeData(true); // send last valid data to ui			
			sendEventEnabled = false;
			playerView.setKeepContentOnPlayerReset(true);
			if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
    			player.seekToDefaultPosition();
    			player.prepare();
  			} else if(errorCount >= 3 || shouldReopen){
				if(player != null){
					player.stop();
				}
				MCPrepare(true);
			} else if(errorCount >= 2){
				if(player != null){
					player.seekToDefaultPosition();
					player.prepare();
				}
			} else {
				player.retry();
			}			
            player.setPlayWhenReady(true);
			setTimeout(() -> {
				sendEventEnabled = true;
				sendEvent("state", currentPlayerState, false);
				if(currentPlayerState.equals("loading")){
					cordova.getActivity().runOnUiThread(new Runnable() {
						@Override
						public void run() {
							fixStalledPlayback();
						}
					});
				}
			}, 100);
			Log.e(TAG, "onPlayerError (auto-recovering) " + errStr + " " + what + "  "+ playbackPosition);
		}

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
						MCPlaybackRate(currentPlaybackRate);
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
			if(state.equals("loading")){
				videoLoadingSince = System.currentTimeMillis();
			}
			currentPlayerState = state;
			sendEvent("state", state, false);
			cordova.getActivity().runOnUiThread(new Runnable() {
				@Override
				public void run() {
					fixStalledPlayback();
				}
			});
		}

		@Override 
		public void onIsPlayingChanged(boolean playing){
			isPlaying = playing;
			if(isPlaying) {
				handler.postDelayed(timer, 0);
			} else {
				handler.removeCallbacks(timer);
			}
		}

		@Override
		public void onVideoSizeChanged(VideoSize videoSize) {
			videoWidth = videoSize.width;
			videoHeight = videoSize.height;
			ResetAspectRatio();
		}
	
		@Override
		public void onTracksChanged(Tracks tracks) {
      		Log.d(TAG, "onTracksChanged");
			sendTracksToUI(tracks);
		}
		
	}

	public void sendTracksToUI(Tracks tracks) {
		String groups = "";
		Log.d(TAG, "sendTracksToUI");
		for (Tracks.Group trackGroup : tracks.getGroups()) {
			for (int i = 0; i < trackGroup.length; i++) {
				//boolean isSupported = trackGroup.isTrackSupported(i);
				//if(isSupported) {
					Format format = trackGroup.getTrackFormat(i);
				//	if(format.sampleMimeType.contains("audio") || format.sampleMimeType.contains("text")){
						boolean isSelected = trackGroup.isTrackSelected(i);
						String lang = format.language;
						String id = format.id;
						String label = format.label;
						String enabled = isSelected ? "true" : "false";
						groups += "{\"id\":\""+ id +"\",\"label\":\""+ label +"\",\"lang\":\""+ lang +"\",\"type\":\""+ format.sampleMimeType +"\",\"enabled\":"+ enabled +"},";
				//	}
				//}
			}
		}
		if(groups.length() > 0){
			groups = "["+ groups.substring(0, groups.length() - 1) +"]";
		} else {
			groups = "[]";
		}
		sendEvent("tracks", groups, true);
	}
	
    public void subtitleTrack(String trackId) {
		if(player != null){
			Tracks tracks = player.getCurrentTracks();
			for (Tracks.Group trackGroup : tracks.getGroups()) {
				for (int i = 0; i < trackGroup.length; i++) {
					boolean isSupported = trackGroup.isTrackSupported(i);
					if(isSupported) {
						Format format = trackGroup.getTrackFormat(i);
						Log.d(TAG, "TTRACKS3 select ;"+ format.sampleMimeType +";");
						if(format.sampleMimeType.contains("text")){
							String id = format.id;
							Log.d(TAG, "TTRACKS3 select ;"+ id +";  ;"+ trackId +";");
							if(id.equals(trackId)) {
								boolean isSelected = trackGroup.isTrackSelected(i);
								if(!isSelected){
									player.setTrackSelectionParameters(
										player.getTrackSelectionParameters()
										.buildUpon()
										.setOverrideForType(
											new TrackSelectionOverride(
												trackGroup.getMediaTrackGroup(),
												i
											)
										).build()
									);
								}
								Log.d(TAG, "TTRACKS4 select "+ trackId);
								break;
							}
						}
					}
				}
			}
			sendTracksToUI(player.getCurrentTracks());
		}
	}
	
    public void audioTrack(String trackId) {
		if(player != null){
			Tracks tracks = player.getCurrentTracks();
			for (Tracks.Group trackGroup : tracks.getGroups()) {
				for (int i = 0; i < trackGroup.length; i++) {
					boolean isSupported = trackGroup.isTrackSupported(i);
					if(isSupported) {
						Format format = trackGroup.getTrackFormat(i);
						Log.d(TAG, "TTRACKS3 select ;"+ format.sampleMimeType +";");
						if(format.sampleMimeType.contains("audio")){
							String id = format.id;
							Log.d(TAG, "TTRACKS3 select ;"+ id +";  ;"+ trackId +";");
							if(id.equals(trackId)) {
								boolean isSelected = trackGroup.isTrackSelected(i);
								if(!isSelected){
									player.setTrackSelectionParameters(
										player.getTrackSelectionParameters()
										.buildUpon()
										.setOverrideForType(
											new TrackSelectionOverride(
												trackGroup.getMediaTrackGroup(),
												i
											)
										).build()
									);
								}
								Log.d(TAG, "TTRACKS4 select "+ trackId);
								break;
							}
						}
					}
				}
			}
			sendTracksToUI(player.getCurrentTracks());
		}
	}

    public MediaSource getMediaSource(String u, String mimetype, String subtitleUrl, String cookie) {
        MediaItem.Builder mediaItemBuilder = new MediaItem.Builder()
            .setUri(Uri.parse(u))
            .setMimeType(mimetype)
			.setLiveConfiguration(
				new MediaItem.LiveConfiguration.Builder()
					.setMinPlaybackSpeed(1.0f)
					.setMaxPlaybackSpeed(1.0f)
					.build());
        Log.d(TAG, "MEDIASOURCE " + u + ", " + mimetype + ", " + ua + ", " + cookie + ", " + subtitleUrl);
        
		if (subtitleUrl != null && !subtitleUrl.isEmpty()) {
			String[] subtitleUrls = subtitleUrl.split("§");
			for (String url : subtitleUrls) {
				Uri subtitleUri = Uri.parse(url);
				String language = subtitleUri.getQueryParameter("lang");
				String label = subtitleUri.getQueryParameter("label");
				if(language == null || language.isEmpty()) {
					language = Locale.getDefault().getLanguage();
				}
				MediaItem.SubtitleConfiguration subtitle =
					new MediaItem.SubtitleConfiguration.Builder(subtitleUri)
						.setMimeType(MimeTypes.TEXT_VTT)
						.setLanguage(language)
						.setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
						.build();
				mediaItemBuilder.setSubtitleConfigurations(ImmutableList.of(subtitle));
			}
		}
    	MediaItem mediaItem = mediaItemBuilder.build();

        Map<String, String> headers = new HashMap<String, String>(1);
        headers.put("Cookie", cookie);

		HttpDataSource.Factory httpDataSource = new DefaultHttpDataSource.Factory()
			.setUserAgent(ua)
            .setReadTimeoutMs(10000)
            .setConnectTimeoutMs(10000)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(headers)
			.setAllowCrossProtocolRedirects(true);
		DefaultDataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(context, httpDataSource);
        if(currentMimetypeIsHLS){
			HlsMediaSource hlsMediaSource = new HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem);
			return hlsMediaSource;
        } else if(currentMimetypeIsDash){
			DashMediaSource dashMediaSource = new DashMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem);
			return dashMediaSource;
        } else {
			DefaultExtractorsFactory extractorsFactory = new DefaultExtractorsFactory();
			extractorsFactory.setMp3ExtractorFlags(Mp3Extractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING_ALWAYS);
			extractorsFactory.setAdtsExtractorFlags(AdtsExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING_ALWAYS);
			extractorsFactory.setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES);
			extractorsFactory.setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS);
			extractorsFactory.setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS);
			extractorsFactory.setTsExtractorMode​(TsExtractor.MODE_SINGLE_PMT);
			extractorsFactory.setConstantBitrateSeekingEnabled(true);
			ProgressiveMediaSource progressiveMediaSource =
				new ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory)
					.createMediaSource(mediaItem);
			return progressiveMediaSource;
        }
    }

    private void initMegacuboPlayer() {
        if(!isActive){
            isActive = true;
            if(!viewAdded){
				if(playerContainer == null) {			
					Log.d(TAG, "init");
					playerContainer = new FrameLayout(cordova.getActivity());		
					eventListener = new PlayerEventListener();
					parentView = (ViewGroup) webView.getView().getParent();
					playerView = new PlayerView(context);
					playerContainer.addView(playerView);
				}						
				if(player == null){
					BandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
					AdaptiveTrackSelection.Factory trackSelectionFactory = new AdaptiveTrackSelection.Factory();
					TrackSelector trackSelector = new DefaultTrackSelector(context, trackSelectionFactory);   

					trackSelector.setParameters(new DefaultTrackSelector.ParametersBuilder()
						.setAllowVideoMixedMimeTypeAdaptiveness(true)
                        .setRendererDisabled(C.TRACK_TYPE_VIDEO, false)
                        .build()
                	);

					DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
        				.setAllocator(new DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE))
            			.build();

					player = new ExoPlayer.Builder(context)
						.setBandwidthMeter(bandwidthMeter)
						.setTrackSelector(trackSelector)
						.setLoadControl(loadControl)
						.build();
					playerView.setPlayer(player);
					player.setHandleAudioBecomingNoisy(true);
					player.setSeekParameters(SeekParameters.CLOSEST_SYNC);
					player.setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT);
					playerView.setUseController(false); 
					player.addListener(eventListener);
				}
                aspectRatioParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);					
				setTimeout(() -> {                            
					cordova.getActivity().runOnUiThread(new Runnable() {
						@Override
						public void run() {
                			if(!viewAdded) {
								viewAdded = true;
								parentView.addView(playerContainer, 0, aspectRatioParams);
							}
							webView.getView().setBackgroundColor(android.R.color.transparent);
							parentView.setBackgroundColor(Color.BLACK);
						}
					});
				}, 200);
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

    private void MCVolume(int volume){
        if(isActive){        
			//Log.d(TAG, "VOLUME " + volume);
			currentVolume = (float) ((float)volume / 100);
			//Log.d(TAG, "VOLUME float " + currentVolume);
            player.setVolume(currentVolume);
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

    private void MCResume() {        
        if(isActive){
            if(currentPlayerState.equals("ended")){
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
		resetErrorCounter();
        isActive = false;
		if(player != null){
			playerView.setKeepContentOnPlayerReset(false);
			player.stop();
			player.release();
			player = null;
		}
        if(viewAdded){
            Log.d(TAG, "view found - removing container");
            viewAdded = false;
            parentView.removeView(playerContainer);
        }
		currentStreamStartMs = -1;
    }

    private void MCRestartApp(){
		cordova.getActivity().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				MCStop();
			}
		});
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
                        Log.d(TAG, baseError + " StartActivity is null");
                    }
                } else {
                    Log.d(TAG, baseError + " PackageManager is null");
                }
            } else {
                Log.d(TAG, baseError+" Context is null");
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
		if(player != null){
			player.release();
			player = null;
		}
	}
}
