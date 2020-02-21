package com.example.liquideconomycs;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PointF;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.dlazaro66.qrcodereaderview.QRCodeReaderView;
import com.google.android.material.snackbar.Snackbar;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import static com.example.liquideconomycs.SyncServiceIntent.startActionSync;
import static com.example.liquideconomycs.TrieServiceIntent.startActionFind;
import static com.example.liquideconomycs.TrieServiceIntent.startActionGetHash;
import static com.example.liquideconomycs.Utils.BROADCAST_ACTION_ANSWER;
import static com.example.liquideconomycs.Utils.EXTRA_ANSWER;
import static com.example.liquideconomycs.Utils.EXTRA_CMD;
import static com.example.liquideconomycs.Utils.EXTRA_MASTER;
import static com.example.liquideconomycs.Utils.EXTRA_MESSAGE;
import static com.example.liquideconomycs.Utils.hexToByte;

public class ScanerActivity extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback , QRCodeReaderView.OnQRCodeReadListener {

    private static final int MY_PERMISSION_REQUEST_CAMERA = 0;

    private TextView resultTextView;
    private QRCodeReaderView qrCodeReaderView;
    private PointsOverlayView   pointsOverlayView;

    private ViewGroup mainLayout;
    private boolean Provide_service;
    private Core app;

    // handler for received data from service
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Objects.equals(intent.getAction(), BROADCAST_ACTION_ANSWER)) {
                final String master = intent.getStringExtra(EXTRA_MASTER);
                final String cmd = intent.getStringExtra(EXTRA_CMD);

                if(master.equals("Scanner")){
                    if(cmd.equals("GetHash")){
                        final byte[] answer = intent.getByteArrayExtra(EXTRA_ANSWER);
                        if (!Provide_service){
                            assert app.myKey.first != null;
                            String source = Utils.byteToHex((byte[]) app.myKey.first)+" "+ Utils.byteToHex(answer);
                            generate(source);
                            resultTextView.setText(source);
                        }else{
                            String[] fields         = Utils.parseQRString(resultTextView.getText().toString());
                            byte[] accepterRootHash = Utils.hexToByte(fields[1]);
                            byte[] accepterPubKey   = Utils.hexToByte(fields[0]);
                            String signalServer     = null;
                            String token            = null;

                            if(!Arrays.equals(accepterRootHash, answer)){
                                SharedPreferences sharedPref    = PreferenceManager.getDefaultSharedPreferences(context);
                                signalServer                    = sharedPref.getString("Signal_server_URL", "");
                                token                           = sharedPref.getString("Signal_server_Token", "");
                                if(signalServer.equals("")||token.equals("")){
                                    Toast.makeText(getApplicationContext(), getResources().getString(R.string.errorGetSignalServerParam),Toast.LENGTH_LONG).show();
                                }else {
                                    startActionSync(getApplicationContext(), "Scanner", signalServer, accepterPubKey, token, true);
                                }
                            }
                            assert app.myKey.first != null;
                            generate(Utils.byteToHex((byte[]) app.myKey.first)+(signalServer!=null?" "+signalServer+" "+token:""));
                        }
                    }else if(cmd.equals("Find")){
                        final byte[] answer = intent.getByteArrayExtra(EXTRA_ANSWER);
                        if(Provide_service){

                            if(answer!=null) {
                                startActionGetHash(getApplicationContext(), "Scanner", 0L);
                            }else{
                                shakeIt();
                                DialogsFragment alert = new DialogsFragment("ScanerActivity", getResources().getString(R.string.Attention),
                                        getResources().getString(R.string.pubKeyNotFound));

                                FragmentManager manager = getSupportFragmentManager();
                                //myDialogFragment.show(manager, "dialog");

                                FragmentTransaction transaction = manager.beginTransaction();
                                alert.show(transaction, "dialog");
                            }
                        }

                    }else if(cmd.equals("Sync")){
                        final String answer = intent.getStringExtra(EXTRA_ANSWER);
                        Toast.makeText(getApplicationContext(), answer,Toast.LENGTH_LONG).show();
                    }

                }
                //resultTextView.setText(param);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Context context = getApplicationContext();
        app = (Core) context;

        setContentView(R.layout.activity_scaner);
        mainLayout = (ViewGroup) findViewById(R.id.scaner_layout);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        Intent intent = getIntent();
        registerReceiver(mBroadcastReceiver, new IntentFilter(BROADCAST_ACTION_ANSWER));

        String message = intent.getStringExtra(EXTRA_MESSAGE);
        if(message.equals("Provide_service")){
            Provide_service = true;
        }else{
            Provide_service = false;
            startActionGetHash(context,"Scanner",0L);
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

    @Override public void onQRCodeRead(String text, PointF[] points) {

        if (qrCodeReaderView != null) {
            qrCodeReaderView.stopCamera();
        }

        resultTextView.setText(text);
        if(Provide_service){
            String[] fields = Utils.parseQRString(resultTextView.getText().toString());
            startActionFind(getApplicationContext(),"Scanner", hexToByte(fields[0]),0L);
        }else{
            shakeIt();
            String[] fields = Utils.parseQRString(text);
            if(fields[1].equals("") || fields[2].equals("")){
                Toast.makeText(getApplicationContext(), getResources().getString(R.string.errorGetSignalServerParam),Toast.LENGTH_LONG).show();
            }else if(hexToByte(fields[0]).length!=20){
                Toast.makeText(getApplicationContext(), getResources().getString(R.string.errorReadpubKey),Toast.LENGTH_LONG).show();
            }else{
                startActionSync(getApplicationContext(), "Scanner", fields[1], hexToByte(fields[0]), fields[2],false);
            }

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

    // Vibrate for 150 milliseconds
    @SuppressLint("MissingPermission")
    private void shakeIt() {
        if (Build.VERSION.SDK_INT >= 26) {
            ((Vibrator) getSystemService(VIBRATOR_SERVICE)).vibrate(VibrationEffect.createOneShot(300,10));
        } else {
            ((Vibrator) getSystemService(VIBRATOR_SERVICE)).vibrate(300);
        }
    }

    public void okPubKeyNotFoundClicked() {
        startActionGetHash(getApplicationContext(), "Scanner", 0L);
    }
}
