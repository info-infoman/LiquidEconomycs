package com.example.liquideconomycs;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PointF;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.dlazaro66.qrcodereaderview.QRCodeReaderView;
import com.google.android.material.snackbar.Snackbar;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.util.concurrent.atomic.AtomicBoolean;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;

import static com.example.liquideconomycs.SyncServiceIntent.startActionSync;
import static com.example.liquideconomycs.TrieServiceIntent.BROADCAST_ACTION_ANSWER;
import static com.example.liquideconomycs.TrieServiceIntent.EXTRA_ANSWER;
import static com.example.liquideconomycs.TrieServiceIntent.EXTRA_CMD;
import static com.example.liquideconomycs.TrieServiceIntent.EXTRA_MASTER;
import static com.example.liquideconomycs.TrieServiceIntent.startActionGetHash;

public class ScanerActivity extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback , QRCodeReaderView.OnQRCodeReadListener {

    private static final int MY_PERMISSION_REQUEST_CAMERA = 0;

    private TextView resultTextView;
    private QRCodeReaderView qrCodeReaderView;
    private PointsOverlayView   pointsOverlayView;

    private ViewGroup mainLayout;
    private boolean Provide_service;
    private Core app;
    private boolean isSinchronize;

    // handler for received data from service
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(BROADCAST_ACTION_ANSWER)) {
                final String master = intent.getStringExtra(EXTRA_MASTER);
                final String cmd = intent.getStringExtra(EXTRA_CMD);
                final byte[] answer = intent.getByteArrayExtra(EXTRA_ANSWER);
                if(master.equals("Scanner") && cmd.equals("GetHash") && Provide_service){
                    generate((app.myKey.first.toString())+(answer.toString()));
                    resultTextView.setText(app.myKey.first.toString()+answer.toString());
                }else if(!Provide_service){
                    byte[] provideRootHash = Utils.getBytesPart(resultTextView.getText().toString().getBytes(), 20, 20);
                    String URL="";
                    if(provideRootHash.equals(answer)){
                        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
                        String signalServer = sharedPref.getString("Signal_server_URL", "");
                        URL = Base64.encodeToString(signalServer.getBytes(), Base64.DEFAULT);
                    }
                    generate((app.myKey.first.toString())+(!URL.equals("")?URL.getBytes().toString():URL));
                }else{

                }
                //resultTextView.setText(param);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        app = (Core) getApplicationContext();

        isSinchronize = false;

        setContentView(R.layout.activity_scaner);
        mainLayout = (ViewGroup) findViewById(R.id.scaner_layout);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        Intent intent = getIntent();

        registerReceiver(mBroadcastReceiver, new IntentFilter(
                BROADCAST_ACTION_ANSWER));

        String message = intent.getStringExtra(MainActivity.EXTRA_MESSAGE);
        if(message.equals("Provide_service")){
            Provide_service = true;
            startActionGetHash(getApplicationContext(),"Scanner",0L);
        }else{
            Provide_service = false;
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            initQRCodeReaderView();
        } else {
            requestCameraPermission();
        }



    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override protected void onResume() {
        super.onResume();

        registerReceiver(mBroadcastReceiver, new IntentFilter(BROADCAST_ACTION_ANSWER));
        if (qrCodeReaderView != null) {
            qrCodeReaderView.startCamera();
        }
    }

    @Override protected void onPause() {
        super.onPause();
        unregisterReceiver(mBroadcastReceiver);
        if (qrCodeReaderView != null) {
            qrCodeReaderView.stopCamera();
        }
    }

    // Called when a QR is decoded
    // "text" : the text encoded in QR
    // "points" : points where QR control points are placed
    @Override public void onQRCodeRead(String text, PointF[] points) {
        //text= (String) stringFromJNI(text);
        resultTextView.setText(text);
        if(!Provide_service){
            startActionGetHash(getApplicationContext(),"Scanner",0L);
        }else{
            startActionSync(getApplicationContext(), false);
        }

        pointsOverlayView.setPoints(points);
    }

    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                                     @NonNull int[] grantResults) {
        AtomicBoolean ret= new AtomicBoolean(true);
        if (requestCode == MY_PERMISSION_REQUEST_CAMERA) {
            ret.set(false);
        }
        if(ret.get()){
            return;
        }
        if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Snackbar.make(mainLayout, "Permission was granted.", Snackbar.LENGTH_SHORT).show();
            if (requestCode == MY_PERMISSION_REQUEST_CAMERA) {
                initQRCodeReaderView();
            }
        } else {
            Snackbar.make(mainLayout, "Permission request was denied.", Snackbar.LENGTH_SHORT)
                    .show();
        }
    }

    private void requestCameraPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
            Snackbar.make(mainLayout, "Camera access is required to display the camera preview.",
                    Snackbar.LENGTH_INDEFINITE).setAction("OK", new View.OnClickListener() {
                @Override public void onClick(View view) {
                    ActivityCompat.requestPermissions(ScanerActivity.this, new String[] {
                            Manifest.permission.CAMERA
                    }, MY_PERMISSION_REQUEST_CAMERA);
                }
            }).show();
        } else {
            Snackbar.make(mainLayout, "Permission is not available. Requesting camera permission.",
                    Snackbar.LENGTH_SHORT).show();
            ActivityCompat.requestPermissions(this, new String[] {
                    Manifest.permission.CAMERA
            }, MY_PERMISSION_REQUEST_CAMERA);
        }
    }

    private void initQRCodeReaderView() {
        View content = getLayoutInflater().inflate(R.layout.content_decoder, mainLayout, true);

        qrCodeReaderView = (QRCodeReaderView) content.findViewById(R.id.qrdecoderview);
        resultTextView = (TextView) content.findViewById(R.id.result_text_view);
        pointsOverlayView = (PointsOverlayView) content.findViewById(R.id.points_overlay_view);
        qrCodeReaderView.setTorchEnabled(false);
        qrCodeReaderView.setQRDecodingEnabled(true);

        qrCodeReaderView.setAutofocusInterval(2000L);
        qrCodeReaderView.setOnQRCodeReadListener(this);
        //qrCodeReaderView.setBackCamera();
        qrCodeReaderView.setFrontCamera();
        qrCodeReaderView.startCamera();
    }

    public void generate(String args) {
        QRCodeWriter writer = new QRCodeWriter();
        try {
            BitMatrix bitMatrix = writer.encode(args, BarcodeFormat.QR_CODE, 512, 512);
            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bmp.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            ((ImageView) findViewById(R.id.image)).setImageBitmap(bmp);

        } catch (WriterException e) {
            e.printStackTrace();
        }
    }
}
