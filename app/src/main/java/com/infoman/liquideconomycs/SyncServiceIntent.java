package com.infoman.liquideconomycs;

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
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;

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
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import static androidx.constraintlayout.widget.Constraints.TAG;
import static androidx.core.app.NotificationCompat.PRIORITY_LOW;
import static com.infoman.liquideconomycs.TrieServiceIntent.startActionGenerateAnswer;
import static com.infoman.liquideconomycs.Utils.ACTION_GET_HASH;
import static com.infoman.liquideconomycs.Utils.ACTION_START;
import static com.infoman.liquideconomycs.Utils.BROADCAST_ACTION_ANSWER;
import static com.infoman.liquideconomycs.Utils.EXTRA_ANSWER;
import static com.infoman.liquideconomycs.Utils.EXTRA_CMD;
import static com.infoman.liquideconomycs.Utils.EXTRA_MASTER;
import static com.infoman.liquideconomycs.Utils.EXTRA_MSG_TYPE;
import static com.infoman.liquideconomycs.Utils.EXTRA_PAYLOAD;
import static com.infoman.liquideconomycs.Utils.EXTRA_POS;
import static com.infoman.liquideconomycs.Utils.EXTRA_PUBKEY;
import static com.infoman.liquideconomycs.Utils.EXTRA_PROVIDE_SERVICE;
import static com.infoman.liquideconomycs.Utils.EXTRA_SIGNAL_SERVER;
import static com.infoman.liquideconomycs.Utils.EXTRA_TOKEN;
import static org.bitcoinj.core.ECKey.ECDSASignature.decodeFromDER;

public class SyncServiceIntent extends IntentService {

    private Core app;

    public static void startActionSync(Context context, String master, String signalServer, byte[] pubKey, String token, boolean Provide_service) {

        if(Provide_service) {
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
            signalServer = sharedPref.getString("Signal_server_URL", "");
            token = sharedPref.getString("Signal_server_Token", "");
        }
        Intent intent = new Intent(context, SyncServiceIntent.class)
            .setAction(ACTION_START)
            .putExtra(EXTRA_SIGNAL_SERVER, signalServer)
            .putExtra(EXTRA_PROVIDE_SERVICE, Provide_service)
            .putExtra(EXTRA_PUBKEY, pubKey)
            .putExtra(EXTRA_TOKEN, token)
            .putExtra(EXTRA_MASTER, master);
        Log.d(TAG, Arrays.toString(pubKey));
        context.startService(intent);
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Objects.equals(intent.getAction(), BROADCAST_ACTION_ANSWER)) {
                final String master = intent.getStringExtra(EXTRA_MASTER), cmd = intent.getStringExtra(EXTRA_CMD);
                if(master.equals("Trie")){
                    if(cmd.equals("Answer")){
                        final byte[] answer = intent.getByteArrayExtra(EXTRA_ANSWER);
                        if (answer.length > 1)
                            app.sendMsg(answer[0], Utils.getBytesPart(answer, 1, (answer.length)-1));
                    }
                    //resultTextView.setText(param);
                }
            }
        }
    };

    public SyncServiceIntent() {
        super("SyncServiceIntent");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        //android.os.Debug.waitForDebugger();
        app = (Core) getApplicationContext();
        Log.i("liquideconomycs", "Service: SyncServiceIntent is create");
        registerReceiver(mBroadcastReceiver, new IntentFilter(BROADCAST_ACTION_ANSWER));
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_START.equals(action) && !app.isSynchronized) {
                app.clearPrefixTable();
                app.isSynchronized = true;
                app.dateTimeLastSync=new Date().getTime();
                final String    signalServer = intent.getStringExtra(EXTRA_SIGNAL_SERVER),
                                token = intent.getStringExtra(EXTRA_TOKEN),
                                master = intent.getStringExtra(EXTRA_MASTER);
                final byte[] pubKey = intent.getByteArrayExtra(EXTRA_PUBKEY);
                final boolean Provide_service = intent.getBooleanExtra(EXTRA_PROVIDE_SERVICE,true);

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
                //todo sync processor
                List<BasicNameValuePair> mExtraHeaders = Collections.singletonList(new BasicNameValuePair("Cookie", "session=abcd"));
                app.mClient = new WebSocketClient(new WebSocketClient.Listener() {

                    private static final String TAG = "WebSocketClient";

                    @Override
                    public void onConnect() {
                        Log.d(TAG, "Connected!");
                        broadcastActionMsg(master, "Sync", getResources().getString(R.string.onConnection));
                        //first send information about as for signal server
                        app.mClient.send(token);
                    }


                    @Override
                    public void onMessage(String message) {
                        //  Log.d(TAG, String.format("Got string message! %s", message));
                        if(message.equals("Completed")){
                        // //    broadcastActionMsg(master, "Sync", getResources().getString(R.string.onCheckToken));
                        //     //если получатель услуг то запросим хеш корня базы
                            if(!Provide_service){
                                app.sendMsg(Utils.getHashs, new byte[8]);
                            }
                            app.dateTimeLastSync = new Date().getTime();
                        }else{
                             broadcastActionMsg(master, "Sync", getResources().getString(R.string.onCheckTokenError));
                             app.mClient.disconnect();
                        }
                    }

                    @Override
                    public void onMessage(byte[] data) {
                        Log.d(TAG, String.format("Got binary message! %s", Arrays.toString(data)));
                        app.dateTimeLastSync = new Date().getTime();

                        if(data.length<7)
                            app.mClient.disconnect();

                        byte msgType    = Utils.getBytesPart(data,0,1)[0];
                        int sigLength   = Ints.fromByteArray(Utils.getBytesPart(data,1,4));
                        byte[] sig      = Utils.getBytesPart(data,5, sigLength), payload  = Utils.getBytesPart(data, 5+sigLength, data.length-(5+sigLength));

                        //Проверка подписи
                        try {
                            byte[] digest = new byte[1];
                            digest[0] = msgType;
                            digest = Sha256Hash.hash(Bytes.concat(digest, Utils.getBytesPart(payload,0, 8)));
                            if(!Utils.chekSig(pubKey, decodeFromDER(sig), digest))
                                app.mClient.disconnect();
                        } catch (SignatureDecodeException e) {
                            app.mClient.disconnect();
                        }

                        //Проверка типа сообщения
                        if((Provide_service && msgType == Utils.getHashs) || (!Provide_service && msgType == Utils.hashs)){
                            startActionGenerateAnswer(getApplicationContext(),
                                    msgType,
                                    payload);
                        }

                    }

                    @Override
                    public void onDisconnect(int code, String reason) {
                        Log.d(TAG, String.format("Disconnected! Code: %d Reason: %s", code, reason));
                        broadcastActionMsg(master, "Sync", getResources().getString(R.string.disconnect)+reason);
                        app.isSynchronized = false;
                    }

                    @Override
                    public void onError(Exception error) {
                        Log.e(TAG, "Error!", error);
                        broadcastActionMsg(master, "Sync", getResources().getString(R.string.errorConnection) + error);
                        app.isSynchronized=false;
                    }

                }, mExtraHeaders);
                //+(slave ? "/?myKey="+String.valueOf(myKey.first) : "/?slave="+String.valueOf(pubKey)))
                app.mClient.connect(URI.create(signalServer));

                //Таймер проверки ответов
                int counter = 0;
                while (app.isSynchronized && (new Date().getTime() - app.dateTimeLastSync) / 1000 < 300){
                    /*counter++;
                    if(counter > 5000){
                        Cursor query = app.getSyncMsg();
                        byte[] payload;
                        if (query.getCount() > 0) {
                            while (query.moveToNext()) {
                                payload = query.getBlob(query.getColumnIndex("payload"));
                                app.deleteSyncMsg(query.getInt(query.getColumnIndex("id")));
                                app.sendMsg((Provide_service?Utils.hashs:Utils.getHashs), payload);
                            }
                        }
                        query.close();
                        counter = 0;
                    }

                     */
                }

                app.isSynchronized=false;
                app.mClient.disconnect();
                //todo clear tmp register
                stopForeground(true);
                broadcastActionMsg("Main", "Sync", getResources().getString(R.string.SyncFinish));
                ////////////////////////////////////////////////////////////////
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
    public void broadcastActionMsg(String master, String cmd, String answer) {
        Intent intent = new Intent(BROADCAST_ACTION_ANSWER)
            .putExtra(EXTRA_MASTER, master)
            .putExtra(EXTRA_CMD, cmd)
            .putExtra(EXTRA_ANSWER, answer);
        sendBroadcast(intent);
    }
}