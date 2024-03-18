package com.infoman.liquideconomycs.sync;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;

import com.google.common.primitives.Bytes;
import com.infoman.liquideconomycs.Core;
import com.infoman.liquideconomycs.Utils;

import org.apache.http.message.BasicNameValuePair;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.util.Pair;
import androidx.preference.PreferenceManager;

import static com.infoman.liquideconomycs.Utils.EXTRA_INDEX;
import static androidx.core.app.NotificationCompat.PRIORITY_LOW;
import static com.infoman.liquideconomycs.Utils.ACTION_START_SYNC;
import static com.infoman.liquideconomycs.Utils.BROADCAST_ACTION_ANSWER;
import static com.infoman.liquideconomycs.Utils.EXTRA_ANSWER;
import static com.infoman.liquideconomycs.Utils.EXTRA_CMD;
import static com.infoman.liquideconomycs.Utils.EXTRA_MASTER;
import static com.infoman.liquideconomycs.Utils.EXTRA_PROVIDE_SERVICE;
import static com.infoman.liquideconomycs.Utils.EXTRA_SIGNAL_SERVER;
import static com.infoman.liquideconomycs.Utils.EXTRA_TOKEN;
import static java.lang.Long.parseLong;

public class ServiceIntent extends IntentService {

    private Core app;
    private boolean isServiceStarted = false;
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Objects.equals(intent.getAction(), BROADCAST_ACTION_ANSWER)) {
                final String master = intent.getStringExtra(EXTRA_MASTER), cmd = intent.getStringExtra(EXTRA_CMD);
                if(master.equals("Trie")){
                    if(cmd.equals("Answer")){
                        final byte[] answer = intent.getByteArrayExtra(EXTRA_ANSWER);
                        if (answer.length > 1)
                            sendMsg(answer[0], Utils.getBytesPart(answer, 1, (answer.length)-1));
                    } else if(cmd.equals("AddPubKeyForInsert")){
                        final int index = intent.getIntExtra(EXTRA_INDEX, 0);
                        final byte[] pubKey = intent.getByteArrayExtra(EXTRA_ANSWER);
                        app.pubKeysForInsert.add(new Pair(pubKey, index));
                    }
                    //resultTextView.setText(param);
                }
            }
        }
    };

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
        registerReceiver(mBroadcastReceiver, new IntentFilter(BROADCAST_ACTION_ANSWER));

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
        // cancel any running threads here
        unregisterReceiver(mBroadcastReceiver);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        String action = null;
        if (intent != null) {
            action = intent.getAction();
            if (ACTION_START_SYNC.equals(action) /*&& !app.isSynchronized*/) {

                app.provideService = intent.getBooleanExtra(EXTRA_PROVIDE_SERVICE, true);

                //запрет на синхронизацию если не завершена предыдущая(для потребителей)
                if(!app.provideService) {
                    Cursor sync = app.getSyncTable();
                    if(sync.moveToNext()){
                        return;
                    }
                }

                if(!app.pubKeysForInsert.isEmpty()) {
                    app.pubKeysForInsert.clear();
                }
                app.dateTimeLastSync = new Date().getTime();
                int lastIndex = 0;
                final String TAG = "WebSocketClient";

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
                        if (message.equals("Completed")) {
                            //get hash of root in first trie
                            if (!app.provideService) {
                                byte[] ask = Bytes.concat(new byte[1], new byte[8]);
                                sendMsg(Utils.getHashs, ask);
                            }
                            app.dateTimeLastSync = new Date().getTime();
                        } else {
                            //app.broadcastActionMsg(master, "Sync", getResources().getString(R.string.onCheckTokenError));
                            app.mClient.disconnect();
                        }
                    }

                    @SuppressLint("Range")
                    @Override
                    public void onMessage(byte[] data) {
                        Log.d(TAG, String.format("Got binary message! %s", Arrays.toString(data)));
                        app.dateTimeLastSync = new Date().getTime();

                        if(!app.provideService && data.length < 44 || app.provideService && data.length < 10) {
                            app.mClient.disconnect();
                            return;
                        }

                        byte msgType = Utils.getBytesPart(data, 0, 1)[0];
                        Log.d(TAG, String.format("OK! %s", msgType));
                        byte[] payload = Utils.getBytesPart(data, 1, data.length - 1);
                        Log.d(TAG, String.format("OK! %s", Arrays.toString(payload)));

                        //Проверка типа сообщения
                        if ((app.provideService && msgType == Utils.getHashs) || (!app.provideService && msgType == Utils.hashs)) {
                            Log.d(TAG, String.format("OK! %s", Arrays.toString(payload)));
                            app.startActionGenerateAnswer(msgType, payload);
                        }else{
                            if(!app.provideService) {
                                app.mClient.disconnect();
                            }
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
                while ((new Date().getTime() - app.dateTimeLastSync) / 1000 < 300 && app.mClient.isConnected()){
                    //start sync in next node trie file
                    if (!app.provideService && (new Date().getTime() - app.dateTimeLastSync) / 1000 > 250) {
                        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(app);
                        long maxAge = parseLong(sharedPref.getString("maxAge", "30"));
                        long maxPubKeyToInsert = parseLong(sharedPref.getString("maxSyncPubKeyInSession", "10000"));

                        app.insertNewPubKeys();

                        if(lastIndex < maxAge-1 && app.pubKeysForInsert.size() < maxPubKeyToInsert) {
                            lastIndex++;
                            byte[] ask = Bytes.concat(new byte[1], new byte[8]);
                            ask[0] = (byte) lastIndex;
                            sendMsg(Utils.getHashs, ask);
                            app.dateTimeLastSync = new Date().getTime();
                        }
                    }
                }
                app.clearTable("sync");
                app.mClient.disconnect();
                if(!app.pubKeysForInsert.isEmpty()) {
                    app.pubKeysForInsert.clear();
                }
                stopSelf();
                stopForeground(true);
            }
        }
    }

    private void sendMsg(byte msgType, byte[] payload) {
        if (app.mClient.mListener != null && app.mClient.isConnected() && payload.length > 0) {
            byte[] type = new byte[1];
            type[0] = msgType;
            app.mClient.send(Bytes.concat(type, payload));
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