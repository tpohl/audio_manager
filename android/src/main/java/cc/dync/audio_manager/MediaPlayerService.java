package cc.dync.audio_manager;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.drawable.RippleDrawable;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.widget.RemoteViews;
import android.app.Notification;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.Objects;

public class MediaPlayerService extends Service {
    private static final String ACTION_NEXT = "MediaPlayerService_next";
    private static final String ACTION_PREVIOUS = "MediaPlayerService_previous";
    private static final String ACTION_PLAY_OR_PAUSE = "MediaPlayerService_playOrPause";
    private static final String ACTION_STOP = "MediaPlayerService_stop";
    private static final String NOTIFICATION_CHANNEL_ID = "MediaPlayerService_1100";
    private static final String NOTIFICATION_TAG = "MediaPlayerServiceTag_1234";

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return serviceBinder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // 取消Notification
        if (notificationManager != null) {
            notificationManager.cancel(NOTIFICATION_PENDING_ID);
        }
        stopForeground(true);
        // 停止服务
        stopSelf();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        setupNotification();
    }

    // 定义Binder类-当然也可以写成外部类
    private ServiceBinder serviceBinder = new ServiceBinder();

    public class ServiceBinder extends Binder {
        Service getService() {
            return MediaPlayerService.this;
        }
    }


    public enum Events {
        next, previous, playOrPause, stop, binder
    }

    public interface ServiceEvents {
        void onEvents(Events events, Object... args);
    }

    private static ServiceEvents serviceEvents;
    private static MediaPlayerService bindService;
    private static boolean isBindService = false;
    private static Context context;

    // 绑定服务 必须先调用 registerReceiver
    public static void bindService(ServiceEvents serviceEvents) {
        MediaPlayerService.serviceEvents = serviceEvents;

        if (!MediaPlayerService.isBindService) {
            Intent intent = new Intent(context, MediaPlayerService.class);
            /*
             * Service：Service的桥梁
             * ServiceConnection：处理链接状态
             * flags：BIND_AUTO_CREATE, BIND_DEBUG_UNBIND, BIND_NOT_FOREGROUND, BIND_ABOVE_CLIENT, BIND_ALLOW_OOM_MANAGEMENT, or BIND_WAIVE_PRIORITY.
             */
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        } else {
            if (serviceEvents != null) {
                serviceEvents.onEvents(Events.binder, bindService);
            }
        }

    }

    /// 通知事件处理，只能加载一次，否则会重复
    public static void registerReceiver(Context context) {
        MediaPlayerService.context = context;
        // 注册广播
        BroadcastReceiver playerReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d("action", intent.getAction());
                switch (Objects.requireNonNull(intent.getAction())) {
                    case ACTION_NEXT:
                        serviceEvents.onEvents(Events.next);
                        break;
                    case ACTION_PREVIOUS:
                        serviceEvents.onEvents(Events.previous);
                        break;
                    case ACTION_PLAY_OR_PAUSE:// 暂停/播放
                        serviceEvents.onEvents(Events.playOrPause);
                        break;
                    case ACTION_STOP:
                        serviceEvents.onEvents(Events.stop);
                        break;
                }
            }
        };
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_NEXT);
        intentFilter.addAction(ACTION_PREVIOUS);
        intentFilter.addAction(ACTION_PLAY_OR_PAUSE);
        intentFilter.addAction(ACTION_STOP);
        context.registerReceiver(playerReceiver, intentFilter);
    }

    // 解除绑定
    public static void unBind(Context context) {
        if (isBindService) {
            bindService.onDestroy();
            context.unbindService(serviceConnection);
            isBindService = false;
        }
    }

    /**
     * serviceConnection是一个ServiceConnection类型的对象，它是一个接口，用于监听所绑定服务的状态
     */
    private static ServiceConnection serviceConnection = new ServiceConnection() {
        /**
         * 该方法用于处理与服务已连接时的情况。
         */
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            ServiceBinder binder = (ServiceBinder) service;
            bindService = (MediaPlayerService) binder.getService();
            isBindService = true;
            if (serviceEvents != null) {
                serviceEvents.onEvents(Events.binder, bindService);
            }
        }

        /**
         * 该方法用于处理与服务断开连接时的情况。
         */
        @Override
        public void onServiceDisconnected(ComponentName name) {
            bindService = null;
        }

    };

    //    private static final int DELETE_PENDING_REQUESTS = 1022;
    private static final int CONTENT_PENDING_REQUESTS = 1023;
    private static final int NEXT_PENDING_REQUESTS = 1024;
    private static final int PLAY_PENDING_REQUESTS = 1025;
    private static final int STOP_PENDING_REQUESTS = 1026;
    private static final int PREV_PENDING_REQUESTS = 1027;
    private static final int NOTIFICATION_PENDING_ID = 1;

    private NotificationManager notificationManager;
    private NotificationCompat.Builder builder;

    private void setupNotification() {
        Intent intent = new Intent(this, AudioManagerPlugin.class);
        PendingIntent contentPendingIntent = PendingIntent.getActivity(this, CONTENT_PENDING_REQUESTS, intent, getIntentFlags());

        // 停止
        Intent intentStop = new Intent(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getBroadcast(this, STOP_PENDING_REQUESTS, intentStop,
                                                                     getIntentFlags()
        );

        androidx.media.app.NotificationCompat.MediaStyle style = new androidx.media.app.NotificationCompat.MediaStyle();
        style.setShowActionsInCompactView(0,1,2);

        builder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            // 设置状态栏小图标
            .setSmallIcon(R.drawable.ic_player_icon)
            // 设置标题
            .setContentTitle("")
            // 设置内容
            .setContentText("")
            .setStyle(style)
            .setAutoCancel(false)
            .setShowWhen(false)
            .setSilent(true)
            // 设置点击通知效果
            .setContentIntent(contentPendingIntent)

        ;
        updateNotificationBuilder(false, "", "");

        // 获取NotificationManager实例
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel;
            notificationChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID,
                                                          "Trauerfeier Musik", NotificationManager.IMPORTANCE_DEFAULT);
            notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            notificationManager.createNotificationChannel(notificationChannel);
        }

        // 前台服务
        startForeground(NOTIFICATION_PENDING_ID, builder.build());
    }

    private int getIntentFlags() {
        return (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) ?
               PendingIntent.FLAG_MUTABLE :
               PendingIntent.FLAG_CANCEL_CURRENT;
    }

    void updateCover(Bitmap bitmap) {
        builder.setLargeIcon(bitmap);
        notificationManager.notify(NOTIFICATION_PENDING_ID, builder.build());
    }



    // 更新Notification
    void updateNotification(boolean isPlaying, String title, String desc) {


        updateNotificationBuilder(isPlaying, title, desc);

        // 刷新notification
        notificationManager.notify(NOTIFICATION_PENDING_ID, builder.build());
    }

    private void updateNotificationBuilder(boolean isPlaying, String title, String desc) {
        Intent intentPlay = new Intent(ACTION_PLAY_OR_PAUSE);
        PendingIntent playPendingIntent = PendingIntent.getBroadcast(this, PLAY_PENDING_REQUESTS, intentPlay, getIntentFlags());

        Intent intentNext = new Intent(ACTION_NEXT);
        PendingIntent nextPendingIntent = PendingIntent.getBroadcast(this, NEXT_PENDING_REQUESTS, intentNext, getIntentFlags());

        Intent intentPrev = new Intent(ACTION_PREVIOUS);
        PendingIntent prevPendingIntent = PendingIntent.getBroadcast(this, PREV_PENDING_REQUESTS, intentPrev, getIntentFlags());


        builder.setContentTitle(title)
               .setContentText(desc)
               .clearActions();

        builder.addAction(R.drawable.ic_baseline_skip_previous, "Prev", prevPendingIntent);

        if (isPlaying) {
            builder.addAction(R.drawable.ic_baseline_pause, "Pause", playPendingIntent);
        } else {
            builder.addAction(R.drawable.ic_baseline_play_arrow, "Play", playPendingIntent);
        }

        builder.addAction(R.drawable.ic_baseline_skip_next, "Next", nextPendingIntent);
    }
}
