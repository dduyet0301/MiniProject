package com.t3h.buoi15_a.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.widget.RemoteViews;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.lifecycle.MutableLiveData;

import com.t3h.buoi15_a.R;
import com.t3h.buoi15_a.controller.MediaController;
import com.t3h.buoi15_a.controller.MediaListener;
import com.t3h.buoi15_a.model.Song;
import com.t3h.buoi15_a.model.SoundInfo;

import java.util.ArrayList;

public class Mp3Service extends Service implements MediaListener, Runnable {
    private MediaController controller;
    private MutableLiveData<SoundInfo> info = new MutableLiveData<>();
    private Thread t;
    private final String ACTION_NEXT = "action.NEXT";
    private final String ACTION_PREV = "action.PREV";
    private final String ACTION_PAUSE = "action.PAUSE";
    private final String ACTION_CLOSE = "action.CLOSE";

    @Override
    public void onCreate() {
        super.onCreate();
        info.postValue(new SoundInfo());
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_NEXT);
        filter.addAction(ACTION_PAUSE);
        filter.addAction(ACTION_PREV);
        filter.addAction(ACTION_CLOSE);
        registerReceiver(receiver, filter);
        Log.e(getClass().getSimpleName(), "onCreate");
    }

    private void pushNotify() {
        if (t == null) {
            t = new Thread(this);
            t.start();
        }
        SoundInfo value = info.getValue();
        value.setPlaying(controller.isPlaying());
        value.setStarting(true);
        value.setName(controller.getName());
        value.setDuration(controller.getDuration());
        info.postValue(value);

        Intent intent = new Intent(this, getClass());
        startService(intent);

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        String channel = "Mp3Service";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel c = new NotificationChannel(
                    channel, channel, NotificationManager.IMPORTANCE_DEFAULT
            );
            manager.createNotificationChannel(c);
        }

        RemoteViews views = new RemoteViews(
                getPackageName(),
                R.layout.song_notification);

        views.setTextViewText(R.id.tv_title, controller.getName());
        views.setImageViewResource(R.id.im_play, controller.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play_arrow);

        registerAction(views, ACTION_CLOSE, R.id.im_close);
        registerAction(views, ACTION_PAUSE, R.id.im_play);
        registerAction(views, ACTION_NEXT, R.id.im_next);
        registerAction(views, ACTION_PREV, R.id.im_prev);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                this,
                channel
        );
        builder.setSmallIcon(R.mipmap.ic_launcher);
        builder.setCustomBigContentView(views);
        startForeground(123, builder.build());
    }

    private void registerAction(RemoteViews view, String action, int id) {
        Intent intent = new Intent(action);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, 0);
        view.setOnClickPendingIntent(id, pendingIntent);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.e(getClass().getSimpleName(), "onBind");
        return new Mp3Binder(this);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.e(getClass().getSimpleName(), "onUnbind");
        return super.onUnbind(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(receiver);
        Log.e(getClass().getSimpleName(), "onDestroy");
    }

    public void setSongData(ArrayList<Song> arr) {
        if (controller != null) {
            controller.release();
        }
        controller = new MediaController(this, arr, this);
    }

    public MediaController getController() {
        return controller;
    }

    @Override
    public void onStarted() {
        pushNotify();
    }

    @Override
    public void onPause() {
        pushNotify();
    }

    @Override
    public void run() {
        while (true) {
            try {
                SoundInfo value = info.getValue();
                value.setPosition(controller.getCurrentPosition());
                info.postValue(value);
                Thread.sleep(1000);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public class Mp3Binder extends Binder {
        private Mp3Service service;

        public Mp3Binder(Mp3Service service) {
            this.service = service;
        }

        public Mp3Service getService() {
            return service;
        }
    }

    public MutableLiveData<SoundInfo> getInfo() {
        return info;
    }

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            switch (action) {
                case ACTION_CLOSE:
                    info.postValue(new SoundInfo());
                    t.interrupt();
                    controller.release();
                    controller = null;
                    stopForeground(true);
                    stopSelf();
                    break;
                case ACTION_NEXT:
                    controller.change(1);
                    break;
                case ACTION_PREV:
                    controller.change(-1);
                    break;
                case ACTION_PAUSE:
                    if (controller.isPlaying()) {
                        controller.pause();
                    } else {
                        controller.start();
                    }
                    break;
            }
        }
    };
}
