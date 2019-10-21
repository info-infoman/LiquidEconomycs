package com.example.liquideconomycs;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import org.apache.http.message.BasicNameValuePair;

import java.io.RandomAccessFile;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;


public class Core {
    private static final String TAG = "Core";

    private Thread                   mThread;
    private HandlerThread            mHandlerThread;
    private Handler                  mHandler;
    private Out                      mOut;
    private boolean                  mStarted;
    private HashMap                  peerGroup;
    private WebSocketClient          mClient;
    private List<BasicNameValuePair> mExtraHeaders;
    private Trie                     mTrie;
    private byte[]                   HashAccountRoot;

    public Core(Out Out) {
        mHandlerThread = new HandlerThread("Core-thread");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
        mOut     = Out;
        mStarted    = false;
    }

    public Out getOut() {
        return mOut;
    }

    public void Start(final String nodeDir) {

        if (mThread != null && mThread.isAlive()) {
            return;
        }
        mThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    //peerGroup.put()
                    //init
                    mTrie = new Trie(nodeDir);
                    HashAccountRoot = mTrie.getHash(0l);

                    mOut.onStart();
                    mStarted = true;
                } catch (Exception e) {
                    mOut.onError(e);
                    mStarted = false;
                }
            }
        });
        mThread.setDaemon(true);
        mThread.start();
    }

    public void stop() {
        //if (mSocket != null) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                    /*
                    if (mSocket != null) {
                        try {
                            mSocket.close();
                        } catch (IOException ex) {
                            Log.d(TAG, "Error while disconnecting", ex);
                            mListener.onError(ex);
                        }
                        mSocket = null;
                    }
                    mConnected = false;
                    */
                mStarted = false;
            }
        });
    }

    public void getDigest() {
        //if (mSocket != null) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {

            }
        });
    }

    public void checkKey() {
        //if (mSocket != null) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                /* todo add check key*/
                mExtraHeaders = Arrays.asList(new BasicNameValuePair("Cookie", "session=abcd"));
                mClient = new WebSocketClient(new WebSocketClient.Listener() {

                    private static final String TAG = "WebSocketClient";

                    @Override
                    public void onConnect() {
                        Log.d(TAG, "Connected!");
                        //wsOnConnected();
                    }

                    @Override
                    public void onMessage(String message) {
                        Log.d(TAG, String.format("Got string message! %s", message));
                    }

                    @Override
                    public void onMessage(byte[] data) {
                        Log.d(TAG, String.format("Got binary message! %s", data.toString()));
                    }

                    @Override
                    public void onDisconnect(int code, String reason) {
                        Log.d(TAG, String.format("Disconnected! Code: %d Reason: %s", code, reason));
                    }

                    @Override
                    public void onError(Exception error) {
                        Log.e(TAG, "Error!", error);
                    }

                }, mExtraHeaders);
                String hostPort="";
                mClient.connect(URI.create(hostPort));
            }
        });
    }

    public boolean isStarted() {
        return mStarted;
    }

    public interface Out {
        public void onStart();
        //public void onMessage(String message);
        //public void onMessage(byte[] data);
        //public void onDisconnect(int code, String reason);
        public void onError(Exception error);
    }
}