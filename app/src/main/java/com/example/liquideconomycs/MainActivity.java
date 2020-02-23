package com.example.liquideconomycs;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
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
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.dlazaro66.qrcodereaderview.QRCodeReaderView;
import com.google.android.material.snackbar.Snackbar;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import static com.example.liquideconomycs.SyncServiceIntent.*;
import static com.example.liquideconomycs.TrieServiceIntent.*;
import static com.example.liquideconomycs.Utils.*;

public class MainActivity extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback , QRCodeReaderView.OnQRCodeReadListener {

    private static final int MY_PERMISSION_REQUEST_INTERNET = 0;
    private static final int MY_PERMISSION_REQUEST_CAMERA = 0;

    private ViewGroup mainLayout;
    private Switch aSwitch;
    private ToggleButton tbResive, tbMade;

    private Switch bSwitch;
    private ToggleButton tbwNFC, tbQR;

    private Button settings;

    private TextView resultTextView;
    private QRCodeReaderView qrCodeReaderView;
    private PointsOverlayView   pointsOverlayView;

    private boolean Provide_service;
    private Core app;

    // handler for received data from service
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(BROADCAST_ACTION_ANSWER)) {
                final String master = intent.getStringExtra(EXTRA_MASTER);
                final String cmd = intent.getStringExtra(EXTRA_CMD);
                if(master.equals("Main")){
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
                                    startActionSync(getApplicationContext(), "Main", signalServer, accepterPubKey, token, true);
                                }
                            }
                            assert app.myKey.first != null;
                            generate(Utils.byteToHex((byte[]) app.myKey.first)+(signalServer!=null?" "+signalServer+" "+token:""));
                        }
                    }else if(cmd.equals("Find")){
                        final byte[] answer = intent.getByteArrayExtra(EXTRA_ANSWER);
                        if(Provide_service){

                            if(answer!=null) {
                                startActionGetHash(getApplicationContext(), "Main", 0L);
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

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context context = getApplicationContext();
        app = (Core) context;

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED) {
            initQRCodeReaderView();
        } else {
            requestCameraPermission();
            requestINTERNETPermission();
        }

        registerReceiver(mBroadcastReceiver, new IntentFilter(BROADCAST_ACTION_ANSWER));

        setContentView(R.layout.activity_main);
        mainLayout = (ViewGroup) findViewById(R.id.main_layout);

        bSwitch = findViewById(R.id.bSwitch);
        tbwNFC = findViewById(R.id.tbNFC);
        tbQR = findViewById(R.id.tbQR);

        aSwitch = findViewById(R.id.aSwitch);
        tbResive = findViewById(R.id.tbResive);
        tbMade = findViewById(R.id.tbMade);

        settings = findViewById(R.id.settings);

        bSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked){
                    tbwNFC.setChecked(false);
                    tbQR.setChecked(true);
                }else{
                    tbwNFC.setChecked(true);
                    tbQR.setChecked(false);
                }
            }
        });

        aSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked){
                    tbResive.setChecked(false);
                    tbMade.setChecked(true);

                    Provide_service = true;
                }else{
                    tbResive.setChecked(true);
                    tbMade.setChecked(false);

                    Provide_service = false;
                    startActionGetHash(getApplicationContext(),"Main",0L);
                }
            }
        });

        settings.setOnClickListener(new CompoundButton.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent;
                intent = new Intent(getApplicationContext(), SettingsActivity.class);
                startActivity(intent);
            }
        });

        Provide_service = false;
        startActionGetHash(getApplicationContext(),"Main",0L);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override protected void onPause() {
        super.onPause();
        unregisterReceiver(mBroadcastReceiver);

        if (qrCodeReaderView != null) {
            qrCodeReaderView.stopCamera();
        }
    }

    @Override protected void onResume() {
        super.onResume();

        registerReceiver(mBroadcastReceiver, new IntentFilter(BROADCAST_ACTION_ANSWER));

        if (qrCodeReaderView != null) {
            qrCodeReaderView.startCamera();
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                                     @NonNull int[] grantResults) {
        AtomicBoolean ret= new AtomicBoolean(true);
        if (requestCode == MY_PERMISSION_REQUEST_INTERNET || requestCode == MY_PERMISSION_REQUEST_CAMERA) {
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

    private void requestINTERNETPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.INTERNET)) {
            Snackbar.make(mainLayout, "INTERNET access is required to display the INTERNET preview.",
                    Snackbar.LENGTH_INDEFINITE).setAction("OK", new View.OnClickListener() {
                @Override public void onClick(View view) {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[] {
                            Manifest.permission.INTERNET
                    }, MY_PERMISSION_REQUEST_INTERNET);
                }
            }).show();
        } else {
            Snackbar.make(mainLayout, "Permission is not available. Requesting INTERNET permission.",
                    Snackbar.LENGTH_SHORT).show();
            ActivityCompat.requestPermissions(this, new String[] {
                    Manifest.permission.INTERNET
            }, MY_PERMISSION_REQUEST_INTERNET);
        }
    }

    private void requestCameraPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
            Snackbar.make(mainLayout, "Camera access is required to display the camera preview.",
                    Snackbar.LENGTH_INDEFINITE).setAction("OK", new View.OnClickListener() {
                @Override public void onClick(View view) {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[] {
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
        //qrCodeReaderView.setTorchEnabled(false);
        //qrCodeReaderView.setQRDecodingEnabled(true);

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

    @Override public void onQRCodeRead(String text, PointF[] points) {

        if (qrCodeReaderView != null) {
            qrCodeReaderView.stopCamera();
        }

        resultTextView.setText(text);
        if(Provide_service){
            String[] fields = Utils.parseQRString(resultTextView.getText().toString());
            startActionFind(getApplicationContext(),"Main", hexToByte(fields[0]),0L);
        }else{
            shakeIt();
            String[] fields = Utils.parseQRString(text);
            if(fields[1].equals("") || fields[2].equals("")){
                Toast.makeText(getApplicationContext(), getResources().getString(R.string.errorGetSignalServerParam),Toast.LENGTH_LONG).show();
            }else if(hexToByte(fields[0]).length!=20){
                Toast.makeText(getApplicationContext(), getResources().getString(R.string.errorReadpubKey),Toast.LENGTH_LONG).show();
            }else{
                startActionSync(getApplicationContext(), "Main", fields[1], hexToByte(fields[0]), fields[2],false);
            }

        }
        pointsOverlayView.setPoints(points);
    }

    @SuppressLint("MissingPermission")
    private void shakeIt() {
        if (Build.VERSION.SDK_INT >= 26) {
            ((Vibrator) getSystemService(VIBRATOR_SERVICE)).vibrate(VibrationEffect.createOneShot(300,10));
        } else {
            ((Vibrator) getSystemService(VIBRATOR_SERVICE)).vibrate(300);
        }
    }

    public void okPubKeyNotFoundClicked() {
        startActionGetHash(getApplicationContext(), "Main", 0L);
    }
}