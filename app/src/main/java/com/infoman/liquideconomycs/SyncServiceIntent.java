package com.infoman.liquideconomycs;

import android.app.IntentService;
import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.common.primitives.Ints;

import org.apache.http.message.BasicNameValuePair;
import org.bitcoinj.core.SignatureDecodeException;

import java.io.FileNotFoundException;
import java.net.URI;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static com.infoman.liquideconomycs.TrieServiceIntent.startActionGenerateAnswer;
import static com.infoman.liquideconomycs.Utils.*;

public class SyncServiceIntent extends IntentService {

    private Core app;

    public static void startActionSync(Context context, String master, String signalServer, byte[] pubKey, String token, boolean Provide_service) {
        if(Provide_service) {
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
            signalServer = sharedPref.getString("Signal_server_URL", "");
            token = sharedPref.getString("Signal_server_Token", "");
        }
        Intent intent = new Intent(context, SyncServiceIntent.class);
        intent.setAction(ACTION_Start);
        intent.putExtra(EXTRA_SIGNAL_SERVER, signalServer);
        intent.putExtra(EXTRA_Provide_service, Provide_service);
        intent.putExtra(EXTRA_PUBKEY, pubKey);
        intent.putExtra(EXTRA_Token, token);
        intent.putExtra(EXTRA_MASTER, master);
        context.startService(intent);
    }

    public SyncServiceIntent() throws FileNotFoundException {
        super("SyncServiceIntent");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        //android.os.Debug.waitForDebugger();
        app = (Core) getApplicationContext();
        Log.i("liquideconomycs", "Service: SyncServiceIntent is create");

    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_Start.equals(action) && !app.isSynchronized) {
                app.isSynchronized = true;
                app.dateTimeLastSync=new Date().getTime();
                final String signalServer = intent.getStringExtra(EXTRA_SIGNAL_SERVER);
                final byte[] pubKey = intent.getByteArrayExtra(EXTRA_PUBKEY);
                final String token = intent.getStringExtra(EXTRA_Token);
                final String master = intent.getStringExtra(EXTRA_MASTER);
                final boolean Provide_service = intent.getBooleanExtra(EXTRA_Provide_service,true);



                ////////////////////////////////////////////////////////////////
                Notification.Builder builder = new Notification.Builder(getBaseContext())
                        .setTicker("SyncServiceIntent") // use something from something from R.string
                        .setContentTitle("liquid economycs") // use something from something from
                        .setContentText("Sync liquid base") // use something from something from
                        .setProgress(0, 0, true); // display indeterminate progress

                startForeground(9991, builder.build());
                //todo sync processor
                List<BasicNameValuePair> mExtraHeaders = Arrays.asList(new BasicNameValuePair("Cookie", "session=abcd"));
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
                        Log.d(TAG, String.format("Got string message! %s", message));
                        if(message.equals("Completed")){
                            broadcastActionMsg(master, "Sync", getResources().getString(R.string.onCheckToken));
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
                        Log.d(TAG, String.format("Got binary message! %s", data.toString()));
                        if(data.length<7)
                            app.mClient.disconnect();

                        byte msgType    = Utils.getBytesPart(data,0,1)[0];
                        int sigLength   = Ints.fromByteArray(Utils.getBytesPart(data,1,4));
                        byte[] sig      = Utils.getBytesPart(data,5, sigLength);
                        byte[] payload  = Utils.getBytesPart(data, 5+sigLength, data.length-5+sigLength);
                        //todo check sig
                        try {
                            if(!Utils.chekSigMsg(pubKey, sig, msgType, payload))
                                app.mClient.disconnect();
                        } catch (SignatureDecodeException e) {
                            app.mClient.disconnect();
                        }

                        //Provide_service - if owner server - who give work
                        //
                        if((Provide_service && msgType != Utils.getHashs) || (!Provide_service && msgType != Utils.hashs)){
                            app.mClient.disconnect();
                        }else {
                            startActionGenerateAnswer(getApplicationContext(), "SyncServiceIntent", msgType, payload);
                            app.dateTimeLastSync = new Date().getTime();
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


                while (app.isSynchronized && (new Date().getTime() - app.dateTimeLastSync) / 1000 < 300){
                }

                app.mClient.disconnect();

                broadcastActionMsg(master, "Sync", getResources().getString(R.string.SyncFinish));
                stopForeground(true);

                ////////////////////////////////////////////////////////////////
            }
        }
    }

    // called to send data to Activity
    public void broadcastActionMsg(String master, String cmd, String answer) {
        Intent intent = new Intent(BROADCAST_ACTION_ANSWER);
        intent.putExtra(EXTRA_MASTER, master);
        intent.putExtra(EXTRA_CMD, cmd);
        intent.putExtra(EXTRA_ANSWER, answer);
        sendBroadcast(intent);
    }
}