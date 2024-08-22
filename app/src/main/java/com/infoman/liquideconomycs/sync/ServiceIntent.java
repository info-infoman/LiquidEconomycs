package com.infoman.liquideconomycs.sync;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;

import com.infoman.liquideconomycs.Core;
import com.infoman.liquideconomycs.Utils;

import org.apache.http.message.BasicNameValuePair;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import static androidx.core.app.NotificationCompat.PRIORITY_LOW;
import static com.infoman.liquideconomycs.Utils.ACTION_START_SYNC;
import static com.infoman.liquideconomycs.Utils.EXTRA_PROVIDE_SERVICE;
import static com.infoman.liquideconomycs.Utils.EXTRA_SIGNAL_SERVER;
import static com.infoman.liquideconomycs.Utils.EXTRA_TOKEN;

public class ServiceIntent extends IntentService {

    private Core app;
    private boolean isServiceStarted = false;

    public ServiceIntent() {
        super("SyncServiceIntent");
    }

    @SuppressLint("ForegroundServiceType")
    @Override
    public void onCreate() {
        super.onCreate();
        //android.os.Debug.waitForDebugger();
        app = (Core) getApplicationContext();
        if(isServiceStarted) return;
        isServiceStarted = true;

        ////////////////////////////////////////////////////////////////
        String channel;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            channel = createChannel();
        else {
            channel = "";
        }

        NotificationCompat.Builder builder = null; // display indeterminate progress
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            builder = new NotificationCompat.Builder(getBaseContext(), channel)
                    .setTicker("SyncServiceIntent") // use something from something from R.string
                    .setContentTitle("liquid economycs") // use something from something from
                    .setContentText("Sync liquid base") // use something from something from
                    .setProgress(0, 0, true)
                    .setPriority(PRIORITY_LOW)
                    .setCategory(Notification.CATEGORY_SERVICE);
        }

        assert builder != null;
        startForeground(9991, builder.build());

    }

    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        String action = null;
        if (intent != null) {
            action = intent.getAction();
            if (ACTION_START_SYNC.equals(action) /*&& !app.isSynchronized*/) {

                app.provideService = intent.getBooleanExtra(EXTRA_PROVIDE_SERVICE, true);

                app.dateTimeLastSync = new Date().getTime();
                int lastIndex = 0;
                final String TAG = "WebSocketClient";
                Log.d(TAG, "start!");
                final String signalServer = intent.getStringExtra(EXTRA_SIGNAL_SERVER),
                        token = intent.getStringExtra(EXTRA_TOKEN);

                //todo sync processor
                List<BasicNameValuePair> mExtraHeaders = Collections.singletonList(new BasicNameValuePair("Cookie", "session=abcd"));

                app.mClient = new WebSocketClient(new WebSocketClient.Listener() {

                    @Override
                    public void onConnect() {
                        Log.d(TAG, "Connected!");
                        //first send information about as for signal server
                        app.mClient.send(token);
                    }

                    @Override
                    public void onMessage(String message) {
                        Log.d(TAG, message);
                        if (message.equals("Completed")) {
                            //get hash of root in first trie
                            if (!app.provideService) {
                                app.sendMsg(Utils.getHashs, new byte[1]);
                            }
                            app.dateTimeLastSync = new Date().getTime();
                        } else {
                            app.mClient.disconnect();
                        }
                    }

                    @SuppressLint("Range")
                    @Override
                    public void onMessage(byte[] data) {
                        Log.d(TAG, String.format("Got binary message! %s", Arrays.toString(data)));
                        app.dateTimeLastSync = new Date().getTime();

                        if(!app.provideService && data.length < 21 || app.provideService && data.length < 2) {
                            app.mClient.disconnect();
                            return;
                        }

                        byte msgType = Utils.getBytesPart(data, 0, 1)[0];
                        int age = Utils.getBytesPart(data, 1, 1)[0];
                        if(age <= app.maxAge && age > -1){
                            //Проверка типа сообщения
                            if (app.provideService && msgType == Utils.getHashs) {
                                app.generateAnswer(age);
                            }else if(!app.provideService && msgType == Utils.hashs){
                                byte[] payload = Utils.getBytesPart(data, 2, data.length - 2);
                                app.insert(payload, age);
                            }else{
                                if(!app.provideService) {
                                    app.mClient.disconnect();
                                }
                            }
                        }else{
                            app.mClient.disconnect();
                        }
                    }

                    @Override
                    public void onDisconnect(int code, String reason) {
                    }

                    @Override
                    public void onError(Exception error) {
                    }


                }, mExtraHeaders);

                app.mClient.connect(URI.create(signalServer));

                while ((new Date().getTime() - app.dateTimeLastSync) / 1000 < 10){

                }

                //Таймер проверки ответов
                while ((new Date().getTime() - app.dateTimeLastSync) / 1000 < 150 && app.mClient.isConnected()){
                    //start sync in next node trie file
                    if (!app.provideService && (new Date().getTime() - app.dateTimeLastSync) / 1000 > 5) {
                        if(lastIndex < app.maxAge-1) {
                            lastIndex++;
                            byte[] ask = new byte[1];
                            ask[0] = (byte) lastIndex;
                            app.sendMsg(Utils.getHashs, ask);
                            app.dateTimeLastSync = new Date().getTime();
                        }
                    }
                }
                app.mClient.disconnect();
                stopForeground(true);
                stopSelf();
            }
        }
    }



    @NonNull
    @TargetApi(26)
    private synchronized String createChannel() {
        NotificationManager mNotificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);

        String name = "Service: liquideconomycs SyncServiceIntent ";
        int importance = NotificationManager.IMPORTANCE_LOW;

        NotificationChannel mChannel = new NotificationChannel("Service: liquideconomycs SyncServiceIntent", name, importance);

        mChannel.enableLights(true);
        mChannel.setLightColor(Color.BLUE);
        if (mNotificationManager != null) {
            mNotificationManager.createNotificationChannel(mChannel);
        } else {
            stopSelf();
        }
        return "Service: liquideconomycs SyncServiceIntent";
    }
    // called to send data to Activity
}