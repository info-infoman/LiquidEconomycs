package com.example.liquideconomycs;

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
import java.util.List;

import static com.example.liquideconomycs.TrieServiceIntent.startActionGenerateAnswer;

public class SyncServiceIntent extends IntentService {

    private static final String ACTION_Start = "com.example.liquideconomycs.SyncServiceIntent.action.Start";
    private static final String EXTRA_SIGNAL_SERVER = "com.example.liquideconomycs.SyncServiceIntent.extra.SIGNAL_SERVER";
    private static final String EXTRA_Provide_service = "com.example.liquideconomycs.SyncServiceIntent.extra.Provide_service";
    private static final String EXTRA_KEY = "com.example.liquideconomycs.SyncServiceIntent.extra.KEY";
    private static String EXTRA_Token = "com.example.liquideconomycs.SyncServiceIntent.extra.Token";;
    private Core app;

    public static void startActionSync(Context context, String signalServer, byte[] pubKey, String token, boolean Provide_service) {
        if(Provide_service) {
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
            signalServer = sharedPref.getString("Signal_server_URL", "");
            token = sharedPref.getString("Signal_server_Token", "");
        }
        Intent intent = new Intent(context, SyncServiceIntent.class);
        intent.setAction(ACTION_Start);
        intent.putExtra(EXTRA_SIGNAL_SERVER, signalServer);
        intent.putExtra(EXTRA_Provide_service, Provide_service);
        intent.putExtra(EXTRA_KEY, pubKey);
        intent.putExtra(EXTRA_Token, token);
        context.startService(intent);
    }

    public SyncServiceIntent() throws FileNotFoundException {
        super("SyncServiceIntent");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        android.os.Debug.waitForDebugger();
        Context context = getApplicationContext();
        app = (Core) getApplicationContext();
        Log.i("liquideconomycs", "Service: SyncServiceIntent is create");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_Start.equals(action) && !app.isSynchronized) {
                app.isSynchronized = true;
                final String signalServer = intent.getStringExtra(EXTRA_SIGNAL_SERVER);
                final byte[] pubKey = intent.getByteArrayExtra(EXTRA_KEY);
                final String token = intent.getStringExtra(EXTRA_Token);
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
                        //first send information about as for signal server
                        app.mClient.send(token);
                    }

                    @Override
                    public void onMessage(String message) {
                        Log.d(TAG, String.format("Got string message! %s", message));
                        if(message.equals("Completed")){
                            if(!Provide_service){
                                app.sendMsg(Utils.getHashs, new byte[8]);
                            }
                        }else{
                            app.mClient.disconnect();
                        }
                    }

                    @Override
                    public void onMessage(byte[] data) {
                        Log.d(TAG, String.format("Got binary message! %s", data.toString()));
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
                        }

                    }

                    @Override
                    public void onDisconnect(int code, String reason) {
                        Log.d(TAG, String.format("Disconnected! Code: %d Reason: %s", code, reason));
                        app.isSynchronized = false;
                    }

                    @Override
                    public void onError(Exception error) {
                        Log.e(TAG, "Error!", error);
                        app.isSynchronized=false;
                    }

                }, mExtraHeaders);
                //+(slave ? "/?myKey="+String.valueOf(myKey.first) : "/?slave="+String.valueOf(pubKey)))
                app.mClient.connect(URI.create(signalServer));


                while (app.isSynchronized){
                }

                stopForeground(true);

                ////////////////////////////////////////////////////////////////
            }
        }
    }
}