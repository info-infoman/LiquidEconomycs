package com.infoman.liquideconomycs.sync;

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
import com.google.common.primitives.Ints;
import com.infoman.liquideconomycs.Core;
import com.infoman.liquideconomycs.Utils;

import org.apache.http.message.BasicNameValuePair;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.SignatureDecodeException;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;

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
import static org.bitcoinj.core.ECKey.ECDSASignature.decodeFromDER;

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
                    }
                    //resultTextView.setText(param);
                }
            }
        }
    };

    public ServiceIntent() {
        super("SyncServiceIntent");
    }

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

    //@Override
    //public int onStartCommand(Intent intent, int flags, int startId) {
    //    //waitingIntentCount++;
    //    return super.onStartCommand(intent, flags, startId);
    //}

    @Override
    protected void onHandleIntent(Intent intent) {
        String action = null;
        if (intent != null) {
            action = intent.getAction();
            if (ACTION_START_SYNC.equals(action) /*&& !app.isSynchronized*/) {
                final boolean Provide_service = intent.getBooleanExtra(EXTRA_PROVIDE_SERVICE, true);

                if(!Provide_service) {
                    Cursor sync = app.getSyncTable();
                    if(sync.moveToNext()){
                        return;
                    }
                }

                app.insertedPubKeyInSession = 0;
                //app.isSynchronized = true;
                app.dateTimeLastSync = new Date().getTime();
                app.clientPubKey = new byte[32];
                int lastIndex = 0;
                final String TAG = "WebSocketClient";
                Cursor query = app.getClients();

                final String signalServer = intent.getStringExtra(EXTRA_SIGNAL_SERVER),
                        token = intent.getStringExtra(EXTRA_TOKEN),
                        master = intent.getStringExtra(EXTRA_MASTER);

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
                            if (!Provide_service) {
                                byte[] ask = Bytes.concat(new byte[1], new byte[8]);
                                sendMsg(Utils.getHashs, ask);
                            }
                            app.dateTimeLastSync = new Date().getTime();
                        } else {
                            //app.broadcastActionMsg(master, "Sync", getResources().getString(R.string.onCheckTokenError));
                            app.mClient.disconnect();
                        }
                    }

                    @Override
                    public void onMessage(byte[] data) {
                        Log.d(TAG, String.format("Got binary message! %s", Arrays.toString(data)));
                        app.dateTimeLastSync = new Date().getTime();

                        if (data.length < 5) {
                            if(!Provide_service) {
                                app.mClient.disconnect();
                            }
                            return;
                        }

                        byte msgType = Utils.getBytesPart(data, 0, 1)[0];
                        int sigLength = Ints.fromByteArray(Utils.getBytesPart(data, 1, 4));

                        if (data.length < 5+sigLength+9) {
                            if(!Provide_service) {
                                app.mClient.disconnect();
                            }
                            return;
                        }

                        byte[] sig = Utils.getBytesPart(data, 5, sigLength), payload = Utils.getBytesPart(data, 5 + sigLength, data.length - (5 + sigLength));

                        //Проверка подписи
                        try {
                            byte[] digest = new byte[1];
                            digest[0] = msgType;
                            digest = Sha256Hash.hash(Bytes.concat(digest, Utils.getBytesPart(payload, 0, 8)));
                            //если публичный ключ не поределен, найти его в базе путем перебора таблицы clients
                            if(Arrays.equals(app.clientPubKey, new byte[32])) {
                                byte[] pk;
                                while (query.moveToNext()) {
                                    pk = query.getBlob(query.getColumnIndex("pubKey"));
                                    if (Utils.chekSig(pk, decodeFromDER(sig), digest)) {
                                        app.clientPubKey = pk;
                                        break;
                                    }
                                }
                                if(Arrays.equals(app.clientPubKey, new byte[32])){
                                    if(!Provide_service) {
                                        app.mClient.disconnect();
                                    }
                                    return;
                                }
                            }else{
                                if (!Utils.chekSig(app.clientPubKey, decodeFromDER(sig), digest)) {
                                    if (!Provide_service) {
                                        app.mClient.disconnect();
                                    }
                                    return;
                                }
                            }
                        } catch (SignatureDecodeException e) {
                            if(!Provide_service) {
                                app.mClient.disconnect();
                            }
                            return;
                        }

                        //Проверка типа сообщения
                        if ((Provide_service && msgType == Utils.getHashs) || (!Provide_service && msgType == Utils.hashs)) {
                            Log.d(TAG, String.format("OK! %s", Arrays.toString(payload)));
                            app.startActionGenerateAnswer(msgType, payload);

                        }else{
                            if(!Provide_service) {
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

                //Таймер проверки ответов
                while ((new Date().getTime() - app.dateTimeLastSync) / 1000 < 300){
                    for( int i : app.waitingIntentCounts) {
                        if(i!=0){
                            app.dateTimeLastSync = new Date().getTime();
                        }
                    }
                    //start sync in next node trie file
                    if (!Provide_service && (new Date().getTime() - app.dateTimeLastSync) / 1000 > 250) {
                        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(app);
                        long maxAge = parseLong(sharedPref.getString("maxAge", "30"));
                        long maxPubKeyToInsert = parseLong(sharedPref.getString("maxSyncPubKeyInSession", "10000"));
                        if(lastIndex < maxAge-1 && app.insertedPubKeyInSession < maxPubKeyToInsert) {
                            lastIndex++;
                            byte[] ask = Bytes.concat(new byte[1], new byte[8]);
                            ask[0] = (byte) lastIndex;
                            sendMsg(Utils.getHashs, ask);
                            app.dateTimeLastSync = new Date().getTime();
                        }
                    }
                }
                //app.isSynchronized=false;
                query.close();
                app.clearTable("sync");
                app.mClient.disconnect();
                app.insertedPubKeyInSession = 0;
                stopSelf();
                stopForeground(true);
            }
        }
    }

    private void sendMsg(byte msgType, byte[] payload) {
        if (app.mClient.mListener != null && app.mClient.isConnected() && payload.length > 0) {
            byte[] type = new byte[1];
            type[0] = msgType;
            byte[] sig = Utils.Sig(
                    (byte[]) app.getMyKey().second,
                    Sha256Hash.hash(Bytes.concat(type, Utils.getBytesPart(payload, 0, 8)))
            );
            app.mClient.send(Bytes.concat(type, Ints.toByteArray(sig.length), sig, payload));
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