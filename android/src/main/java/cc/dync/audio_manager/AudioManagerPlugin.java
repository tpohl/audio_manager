package cc.dync.audio_manager;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;

/**
 * AudioManagerPlugin
 */
public class AudioManagerPlugin implements FlutterPlugin, MethodCallHandler, VolumeChangeObserver.VolumeChangeListener {

    private static AudioManagerPlugin instance;
    private Context context;
    private MethodChannel channel;
    private MediaPlayerHelper helper;
    private VolumeChangeObserver volumeChangeObserver;

    private static FlutterAssets flutterAssets;
    private static Registrar registrar;

    private static synchronized AudioManagerPlugin getInstance() {
        if (instance == null) {
            instance = new AudioManagerPlugin();
        }
        return instance;
    }

    public AudioManagerPlugin() {
        if (instance == null) {
            instance = this;
        }
    }

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
        final AudioManagerPlugin instance = getInstance();
        final Context applicationContext = flutterPluginBinding.getApplicationContext();

        // This might get called multiple times -> only do this, if the context changed (probably this cannot happen anyway)
        if (instance.context == null || instance.context != applicationContext || instance.channel == null) {
            final MethodChannel channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), "audio_manager");

            channel.setMethodCallHandler(getInstance());
            setup(flutterPluginBinding.getApplicationContext(), channel);
            AudioManagerPlugin.flutterAssets = flutterPluginBinding.getFlutterAssets();
        }
    }

    // This static function is optional and equivalent to onAttachedToEngine. It
    // supports the old
    // pre-Flutter-1.12 Android projects. You are encouraged to continue supporting
    // plugin registration via this function while apps migrate to use the new
    // Android APIs
    // post-flutter-1.12 via https://flutter.dev/go/android-project-migration.
    //
    // It is encouraged to share logic between onAttachedToEngine and registerWith
    // to keep
    // them functionally equivalent. Only one of onAttachedToEngine or registerWith
    // will be called
    // depending on the user's project. onAttachedToEngine or registerWith must both
    // be defined
    // in the same class.
    public static void registerWith(Registrar registrar) {
        MethodChannel channel = new MethodChannel(registrar.messenger(), "audio_manager");

        channel.setMethodCallHandler(getInstance());
        instance.setup(registrar.context(), channel);
        AudioManagerPlugin.registrar = registrar;
    }

    private void setup(Context context, MethodChannel channel) {
        instance.context = context;
        instance.channel = channel;

        instance.helper = MediaPlayerHelper.getInstance(instance.context);
        setupPlayer();
        volumeChangeObserver = new VolumeChangeObserver(instance.context);
        volumeChangeObserver.setVolumeChangeListener(instance);
        volumeChangeObserver.registerReceiver();
    }

    private void setupPlayer() {
        MediaPlayerHelper helper = instance.helper;
        MethodChannel channel = instance.channel;

        helper.setOnStatusCallbackListener((status, args) -> {
            Log.v(TAG, "--" + status.toString());
            switch (status) {
                case ready:
                    channel.invokeMethod("ready", helper.duration());
                    break;
                case seekComplete:
                    channel.invokeMethod("seekComplete", helper.position());
                    break;
                case buffering:
                    if (args.length == 0) return;
                    Log.v(TAG, "网络缓冲:" + args[1] + "%");

                    Map map = new HashMap();
                    map.put("buffering", !helper.isPlaying());
                    map.put("buffer", args[1]);
                    channel.invokeMethod("buffering", map);
                    break;
                case playOrPause:
                    if (args.length == 0) return;
                    channel.invokeMethod("playstatus", args[0]);
                    break;
                case progress:
                    if (args.length == 0) return;
                    Log.v(TAG, "进度:" + args[0] + "%");

                    Map map2 = new HashMap();
                    map2.put("position", helper.position());
                    map2.put("duration", helper.duration());
                    channel.invokeMethod("timeupdate", map2);
                    break;
                case error:
                    Log.v(TAG, "播放错误:" + args[0]);
                    channel.invokeMethod("error", args[0]);
                    helper.stop();
                    break;
                case next:
                    channel.invokeMethod("next", null);
                    break;
                case previous:
                    channel.invokeMethod("previous", null);
                    break;
                case ended:
                    channel.invokeMethod("ended", null);
                    break;
                case stop:
                    channel.invokeMethod("stop", null);
                    break;
            }
        });
    }

    private static final String TAG = "AudioManagerPlugin";

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        MediaPlayerHelper helper = instance.helper;
        switch (call.method) {
            case "getPlatformVersion":
                result.success("Android " + android.os.Build.VERSION.RELEASE);
                break;
            case "start":
                String url = call.argument("url");
                String title = call.argument("title");
                String desc = call.argument("desc");
                String cover = call.argument("cover");

                boolean isLocal = call.hasArgument("isLocal") ? call.argument("isLocal") : false;
                boolean isLocalCover = call.hasArgument("isLocalCover") ? call.argument("isLocalCover") : false;
                boolean isAuto = call.hasArgument("isAuto") ? call.argument("isAuto") : false;
                MediaPlayerHelper.MediaInfo info = new MediaPlayerHelper.MediaInfo(title, url);
                info.desc = desc;
                info.isAsset = isLocal;
                info.isAuto = isAuto;
                if (isLocal) {
                    if (registrar != null) {
                        info.url = registrar.lookupKeyForAsset(url);
                    } else if (flutterAssets != null) {
                        info.url = AudioManagerPlugin.flutterAssets.getAssetFilePathByName(url);
                    }
                }
                info.cover = cover;
                if (isLocalCover) {
                    if (registrar != null) {
                        info.cover = registrar.lookupKeyForAsset(cover);
                    } else if (flutterAssets != null) {
                        if (helper.isDataDirFile(cover)) {
                            info.cover = cover;
                        } else {
                            info.cover = AudioManagerPlugin.flutterAssets.getAssetFilePathByName(cover);
                        }
                    }
                }

                try {
                    helper.start(info);
                } catch (Exception e) {
                    result.success(e.getMessage());
                }
                break;
            case "playOrPause":
                helper.playOrPause();
                result.success(helper.isPlaying());
                break;
            case "play":
                helper.play();
                result.success(helper.isPlaying());
                break;
            case "pause":
                helper.pause();
                result.success(helper.isPlaying());
                break;
            case "stop":
                helper.stop();
            case "release":
                helper.release();
                break;
            case "updateLrc":
                helper.updateLrc(call.argument("lrc"));
                break;
            case "seekTo":
                try {
                    int position = Integer.parseInt(call.argument("position").toString());
                    helper.seekTo(position);
                } catch (Exception ex) {
                    result.success("参数错误");
                }
                break;
            case "rate":
                try {
                    double rate = Double.parseDouble(call.argument("rate").toString());
                    helper.setSpeed((float) rate);
                } catch (Exception ex) {
                    result.success("参数错误");
                }
                break;
            case "setVolume":
                try {
                    double value = Double.parseDouble(call.argument("value").toString());
                    instance.volumeChangeObserver.setVolume(value);
                } catch (Exception ex) {
                    result.success("参数错误");
                }
                break;
            case "currentVolume":
                result.success(instance.volumeChangeObserver.getCurrentMusicVolume());
                break;
            default:
                result.notImplemented();
                break;
        }
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    }

    @Override
    public void onVolumeChanged(double volume) {
        instance.channel.invokeMethod("volumeChange", volume);
    }
}
