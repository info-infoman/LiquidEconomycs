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
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
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

import com.bumptech.glide.Glide;
import com.dlazaro66.qrcodereaderview.QRCodeReaderView;
import com.google.android.material.snackbar.Snackbar;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.util.concurrent.atomic.AtomicBoolean;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import static com.example.liquideconomycs.SyncServiceIntent.startActionSync;
import static com.example.liquideconomycs.TrieServiceIntent.startActionFind;
import static com.example.liquideconomycs.TrieServiceIntent.startActionInsert;
import static com.example.liquideconomycs.Utils.BROADCAST_ACTION_ANSWER;
import static com.example.liquideconomycs.Utils.EXTRA_ANSWER;
import static com.example.liquideconomycs.Utils.EXTRA_CMD;
import static com.example.liquideconomycs.Utils.EXTRA_MASTER;
import static com.example.liquideconomycs.Utils.hexToByte;

public class MainActivity extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback , QRCodeReaderView.OnQRCodeReadListener, NfcAdapter.CreateNdefMessageCallback {

    private static final int MY_PERMISSION_REQUEST_INTERNET = 0;
    private static final int MY_PERMISSION_REQUEST_CAMERA = 0;

    private Core                app;
    private ViewGroup           mainLayout;
    private ToggleButton        tbResive, tbMade;
    private ToggleButton        tbwNFC, tbQR;
    public TextView            resultTextView;
    private TextView            notation;
    private QRCodeReaderView    qrCodeReaderView;
    private PointsOverlayView   pointsOverlayView;
    private boolean             provideService;
    public boolean              redyToNextScan;

    // handler for received data from service///////////////////////////////////////////////////////
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(BROADCAST_ACTION_ANSWER)) {
                final String master = intent.getStringExtra(EXTRA_MASTER);
                final String cmd = intent.getStringExtra(EXTRA_CMD);
                if(master.equals("Main")){
                    if(cmd.equals("Find")){
                        final byte[] answer = intent.getByteArrayExtra(EXTRA_ANSWER);
                        if(provideService){
                            shakeIt(300,10);
                            if(answer!=null) {
                                startActionSync(getApplicationContext(), "Main", "", Utils.hexToByte(resultTextView.getText().toString()), "", true);
                            }else{
                                DialogsFragment alert = new DialogsFragment("MainActivity", 0);
                                FragmentManager manager = getSupportFragmentManager();
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

    //Overrides/////////////////////////////////////////////////////////////////////////////////////

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context context = getApplicationContext();
        app = (Core) context;
        setContentView(R.layout.activity_main);
        mainLayout = (ViewGroup) findViewById(R.id.main_layout);
        notation = (TextView) findViewById(R.id.notation);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED) {} else {
            requestINTERNETPermission();
        }
        registerReceiver(mBroadcastReceiver, new IntentFilter(BROADCAST_ACTION_ANSWER));

        Switch bSwitch = findViewById(R.id.bSwitch);
        tbwNFC = findViewById(R.id.tbNFC);
        tbQR = findViewById(R.id.tbQR);

        Switch aSwitch = findViewById(R.id.aSwitch);
        tbResive = findViewById(R.id.tbResive);
        tbMade = findViewById(R.id.tbMade);

        Button settings = findViewById(R.id.settings);

        bSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked){
                    tbwNFC.setChecked(true);
                    tbQR.setChecked(false);
                }else{
                    tbwNFC.setChecked(false);
                    tbQR.setChecked(true);
                }
                initPairingInstrument();
                generatePairingMsg();
            }
        });

        aSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked){
                    tbResive.setChecked(false);
                    tbMade.setChecked(true);
                    provideService = true;
                    MainActivity.this.setTitle(R.string.app_name+"("+R.string.Provide_service+"(");
                }else{
                    tbResive.setChecked(true);
                    tbMade.setChecked(false);
                    provideService = false;
                    MainActivity.this.setTitle(R.string.app_name+"("+R.string.Accept_service+"(");
                }
                generatePairingMsg();
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

        provideService = false;

    }

    @Override protected void onDestroy() {
        super.onDestroy();
    }

    @Override protected void onPause() {
        super.onPause();
        unregisterReceiver(mBroadcastReceiver);

        initPairingInstrument();
    }

    @Override protected void onResume() {
        super.onResume();

        registerReceiver(mBroadcastReceiver, new IntentFilter(BROADCAST_ACTION_ANSWER));

        initPairingInstrument();
        generatePairingMsg();
    }

    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        AtomicBoolean ret= new AtomicBoolean(true);
        if (requestCode == MY_PERMISSION_REQUEST_CAMERA) {
            ret.set(false);
        }
        if (requestCode == MY_PERMISSION_REQUEST_INTERNET) {
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

    @Override public void onQRCodeRead(String text, PointF[] points) {
        if(redyToNextScan) {
            redyToNextScan = false;
            codeReadTrigger(text);
            pointsOverlayView.setPoints(points);
        }

    }

    @SuppressLint("MissingPermission") private void shakeIt(int s, int i) {
        if (Build.VERSION.SDK_INT >= 26) {
            ((Vibrator) getSystemService(VIBRATOR_SERVICE)).vibrate(VibrationEffect.createOneShot(s,i));
        } else {
            ((Vibrator) getSystemService(VIBRATOR_SERVICE)).vibrate(s);
        }
    }

    @Override public NdefMessage createNdefMessage(NfcEvent event) {
        assert app.myKey.first != null;
        String msg = Utils.byteToHex((byte[]) app.myKey.first);
        if(provideService){
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            String signalServer = sharedPref.getString("Signal_server_URL", "");
            String token = sharedPref.getString("Signal_server_Token", "");
            String args = msg + (signalServer != null ? " " + signalServer + " " + token : "");
            if(!msg.equals(""))
                return new NdefMessage(Utils.createNFCrecords(args));
        }else{
            if(!msg.equals(""))
                return new NdefMessage(Utils.createNFCrecords(msg));
        }
        return null;
    }

    @Override public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleNfcIntent(intent);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private void initPairingInstrument(){
        if (tbQR.isChecked() && qrCodeReaderView == null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    initQRCodeReaderView();
            } else {
                requestCameraPermission();
            }
        }else{
            if(qrCodeReaderView != null) {
                qrCodeReaderView.stopCamera();
                qrCodeReaderView.setOnQRCodeReadListener(null);
                qrCodeReaderView.onDetachedFromWindow();
                qrCodeReaderView = null;
                clearQRCodeReaderViewView(mainLayout);
                //ConstraintLayout.LayoutParams lp = new ConstraintLayout.LayoutParams(1, 1);
                //FrameLayout relativeLayout = this.mainLayout.findViewById(R.id.main_layout);
                //relativeLayout.setLayoutParams(lp);
            }
        }
    }

    private void requestINTERNETPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.INTERNET)) {
            Snackbar.make(mainLayout, getResources().getText(R.string.getINTERNETPermission),
                    Snackbar.LENGTH_INDEFINITE).setAction(getResources().getString(android.R.string.ok), new View.OnClickListener() {
                @Override public void onClick(View view) {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[] {
                            Manifest.permission.INTERNET
                    }, MY_PERMISSION_REQUEST_INTERNET);
                }
            }).show();
        } else {
            Snackbar.make(mainLayout, getResources().getText(R.string.permission_is_not_available_INTERNET),
                    Snackbar.LENGTH_SHORT).show();
            ActivityCompat.requestPermissions(this, new String[] {
                    Manifest.permission.INTERNET
            }, MY_PERMISSION_REQUEST_INTERNET);
        }
    }

    private void requestCameraPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
            Snackbar.make(mainLayout, getResources().getText(R.string.getCAMERAPermission),
                    Snackbar.LENGTH_INDEFINITE).setAction(getResources().getString(android.R.string.ok), new View.OnClickListener() {
                @Override public void onClick(View view) {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[] {
                            Manifest.permission.CAMERA
                    }, MY_PERMISSION_REQUEST_CAMERA);
                }
            }).show();
        } else {
            Snackbar.make(mainLayout, getResources().getText(R.string.permission_is_not_available_CAMERA),
                    Snackbar.LENGTH_SHORT).show();
            ActivityCompat.requestPermissions(this, new String[] {
                    Manifest.permission.CAMERA
            }, MY_PERMISSION_REQUEST_CAMERA);
        }
    }

    private void initQRCodeReaderView() {

        redyToNextScan = true;

        View content = getLayoutInflater().inflate(R.layout.content_decoder, mainLayout, true);

        qrCodeReaderView = (QRCodeReaderView) findViewById(R.id.qrdecoderview);
        resultTextView = (TextView) findViewById(R.id.result_text_view);
        pointsOverlayView = (PointsOverlayView) findViewById(R.id.points_overlay_view);
        qrCodeReaderView.setTorchEnabled(false);
        qrCodeReaderView.setQRDecodingEnabled(true);

        qrCodeReaderView.setAutofocusInterval(2000L);
        qrCodeReaderView.setOnQRCodeReadListener(this);
        //qrCodeReaderView.setBackCamera();
        qrCodeReaderView.setBackCamera();
        qrCodeReaderView.startCamera();

        //ConstraintLayout.LayoutParams lp = new ConstraintLayout.LayoutParams(150, 150);
        //FrameLayout relativeLayout = findViewById(R.id.main_layout);
        //relativeLayout.setLayoutParams(lp);
    }

    public void codeReadTrigger(String text){
        resultTextView.setText(text);
        if(provideService){
            byte[] accepterPubKey = Utils.hexToByte(resultTextView.getText().toString());
            if(accepterPubKey.length!=20){
                Toast.makeText(getApplicationContext(), getResources().getString(R.string.ErrorReceivingPartnerData),Toast.LENGTH_LONG).show();
            }
            startActionFind(getApplicationContext(),"Main", accepterPubKey,0L);
        }else{
            shakeIt(300,10);
            String[] fields = Utils.parseQRString(text);

            if(fields.length < 3 || fields[1].equals("") || fields[2].equals("") || hexToByte(fields[0]).length!=20){
               Toast.makeText(getApplicationContext(), getResources().getString(R.string.ErrorReceivingPartnerData),Toast.LENGTH_LONG).show();
            }else{
                startActionInsert(this, "Main", hexToByte(fields[0]), Utils.ageToBytes());
                startActionSync(getApplicationContext(), "Main", fields[1], hexToByte(fields[0]), fields[2],false);
            }

        }
    }

    public void generatePairingMsg() {
        ImageView img = (ImageView) findViewById(R.id.image);
        assert app.myKey.first != null;
        String msg = Utils.byteToHex((byte[]) app.myKey.first);

        if(tbQR.isChecked()) {
            if (provideService) {
                SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                String signalServer = sharedPref.getString("Signal_server_URL", "");
                String token = sharedPref.getString("Signal_server_Token", "");

                msg = msg + (signalServer != null ? " " + signalServer + " " + token : "");
            }

            QRCodeWriter writer = new QRCodeWriter();
            try {
                BitMatrix bitMatrix = writer.encode(msg, BarcodeFormat.QR_CODE, 512, 512);
                int width = bitMatrix.getWidth();
                int height = bitMatrix.getHeight();
                Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
                for (int x = 0; x < width; x++) {
                    for (int y = 0; y < height; y++) {
                        bmp.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                    }
                }
                img.setImageBitmap(bmp);

            } catch (WriterException e) {
                e.printStackTrace();
            }

            notation.setText(getResources().getString(R.string.annotation_qr));
        }else{
            NfcAdapter mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
            if(mNfcAdapter != null) {
                //clear img
                img.setImageDrawable(null);
                Glide.with(getApplicationContext())
                        .asGif()
                        .load(R.drawable.nfc_img)
                        .into(img);
                notation.setText(getResources().getString(R.string.annotation_nfc));
                mNfcAdapter.setNdefPushMessageCallback(this, this);
            }else{
                img.setImageResource(R.drawable.nfc_error_img);
                notation.setText(getResources().getString(R.string.error_nfc));
            }

        }

    }

    private void clearQRCodeReaderViewView(ViewGroup v) {
        boolean doBreak = false;
        while (!doBreak) {
            int childCount = v.getChildCount();
            int i;
            for(i=0; i<childCount; i++) {
                View currentChild = v.getChildAt(i);
                // Change ImageView with your desired type view
                if (currentChild instanceof com.dlazaro66.qrcodereaderview.QRCodeReaderView) {
                    v.removeView(currentChild);
                    break;
                }else{
                    if(currentChild instanceof ViewGroup) {
                        clearQRCodeReaderViewView((ViewGroup) currentChild);
                    }
                }
            }

            if (i == childCount) {
                doBreak = true;
            }
        }
    }

    private void handleNfcIntent(Intent NfcIntent) {
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(NfcIntent.getAction())) {
            Parcelable[] receivedArray =
                    NfcIntent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);

            if(receivedArray != null) {
                NdefMessage receivedMessage = (NdefMessage) receivedArray[0];
                NdefRecord[] attachedRecords = receivedMessage.getRecords();

                for (NdefRecord record:attachedRecords) {
                    String string = new String(record.getPayload());
                    //Make sure we don't pass along our AAR (Android Application Record)
                    if (string.equals(getPackageName())) { continue; }
                    codeReadTrigger(string);
                }
            }
        }
    }
}