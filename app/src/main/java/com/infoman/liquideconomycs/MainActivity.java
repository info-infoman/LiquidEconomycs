package com.infoman.liquideconomycs;

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
import android.view.animation.AnimationUtils;
import android.widget.Button;
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

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.SignatureDecodeException;

import java.util.Date;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import static com.infoman.liquideconomycs.Utils.BROADCAST_ACTION_ANSWER;
import static com.infoman.liquideconomycs.Utils.EXTRA_ANSWER;
import static com.infoman.liquideconomycs.Utils.EXTRA_CMD;
import static com.infoman.liquideconomycs.Utils.EXTRA_MASTER;
import static com.infoman.liquideconomycs.Utils.ageToBytes;
import static com.infoman.liquideconomycs.Utils.chekSig;
import static com.infoman.liquideconomycs.Utils.hexToByte;
import static org.bitcoinj.core.ECKey.ECDSASignature.decodeFromDER;

public class MainActivity extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback , QRCodeReaderView.OnQRCodeReadListener, NfcAdapter.CreateNdefMessageCallback{

    private static final int MY_PERMISSION_REQUEST_INTERNET = 0, MY_PERMISSION_REQUEST_CAMERA = 0, MY_PERMISSION_REQUEST_NFC = 0, MY_PERMISSION_REQUEST_FOREGROUND_SERVICE=0;

    private Core                app;
    private ViewGroup           mainLayout;
    private ToggleButton        tbResive, tbMade, tbwNFC, tbQR;
    private Button              startBtn;
    public TextView             resultTextView, notation, role_capture, scan_gen;
    private QRCodeReaderView    qrCodeReaderView;
    private boolean             provideService;//источник новых данных(тот кто предоставляет услугу\работу)
    private String[] fields;

    // Перехватывает события от фоновых сервисов
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Objects.equals(intent.getAction(), BROADCAST_ACTION_ANSWER)) {
                final String master = intent.getStringExtra(EXTRA_MASTER), cmd = intent.getStringExtra(EXTRA_CMD);
                if(master.equals("Main")){
                    if(cmd.equals("Find")){
                        final byte[] answer = intent.getByteArrayExtra(EXTRA_ANSWER);
                        if(provideService){
                            shakeIt();
                            if(answer!=null) {
                                Toast.makeText(context, getResources().getString(R.string.pubKeyFound),Toast.LENGTH_LONG).show();
                                //startActionInsert(getApplicationContext(), "Main", ECKey.fromPublicOnly(Utils.hexToByte(resultTextView.getText().toString())).getPubKeyHash(), ageToBytes());
                                fields = Utils.parseQRString(resultTextView.getText().toString());
                                byte[] accepterPubKey = Utils.hexToByte(fields[0]);
                                //insert
                                app.addClient(accepterPubKey);
                                //app.startActionSync(context, "Main", "", accepterPubKey, "", true);
                                //app.startActionStopSync(context);
                            }else{
                                DialogsFragment alert = new DialogsFragment(context, "MainActivity", 0);
                                FragmentManager manager = getSupportFragmentManager();
                                FragmentTransaction transaction = manager.beginTransaction();
                                alert.show(transaction, "dialog");
                            }
                        }
                    }/*else if(cmd.equals("Sync")){
                        final String answer = intent.getStringExtra(EXTRA_ANSWER);
                        Toast.makeText(context, answer,Toast.LENGTH_LONG).show();
                    }*/

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
        mainLayout = findViewById(R.id.main_layout);
        notation = findViewById(R.id.notation);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED) {} else {
            requestINTERNETPermission();
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.O ) {} else {
            requestFOREGROUND_SERVICEPermission();
        }

        registerReceiver(mBroadcastReceiver, new IntentFilter(BROADCAST_ACTION_ANSWER));

        Switch bSwitch = findViewById(R.id.bSwitch);
        tbwNFC = findViewById(R.id.tbNFC);
        tbQR = findViewById(R.id.tbQR);

        Switch aSwitch = findViewById(R.id.aSwitch);
        tbResive = findViewById(R.id.tbResive);
        tbMade = findViewById(R.id.tbMade);

        role_capture = findViewById(R.id.role_capture);
        scan_gen = findViewById(R.id.scan_gen);
        startBtn = findViewById(R.id.startScanner);

        Button settings = findViewById(R.id.settings);

        bSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if(isChecked){
                tbwNFC.setChecked(true);
                tbQR.setChecked(false);
            }else{
                tbwNFC.setChecked(false);
                tbQR.setChecked(true);
            }
            stopScanner();

        });

        aSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if(isChecked){
                tbResive.setChecked(false);
                tbMade.setChecked(true);
                provideService = true;
                role_capture.setText(getResources().getString(R.string.Provide_service));
                if((new Date().getTime() - app.dateTimeLastSync) / 1000 > 300){
                    app.startActionSync("Main", "", "", true);
                }
            }else{
                tbResive.setChecked(true);
                tbMade.setChecked(false);
                provideService = false;
                role_capture.setText(getResources().getString(R.string.Accept_service));
            }
            role_capture.startAnimation(AnimationUtils.loadAnimation(getApplicationContext(),
                    R.anim.zoom_in));
            stopScanner();
        });

        settings.setOnClickListener(v -> {
            Intent intent;
            intent = new Intent(getApplicationContext(), SettingsActivity.class);
            startActivity(intent);
        });

        startBtn.setOnClickListener(v -> {
            if(qrCodeReaderView == null) {
                startScanner();
            }else{
                stopScanner();
            }
        });

        provideService = false;
        role_capture.setText(getResources().getString(R.string.Accept_service));
        stopScanner();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.NFC) == PackageManager.PERMISSION_GRANTED) {
            NfcAdapter mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
            if(mNfcAdapter != null) {
                mNfcAdapter.setNdefPushMessageCallback(this, this);
            }
        } else {
            requestNFCPermission();
        }
    }

    @Override protected void onDestroy() {
        super.onDestroy();
    }

    @Override protected void onPause() {
        super.onPause();
        unregisterReceiver(mBroadcastReceiver);

        //if (tbQR.isChecked() && qrCodeReaderView != null) {
            stopScanner();
        //}

    }

    @Override protected void onResume() {
        super.onResume();

        Context context = getApplicationContext();
        app = (Core) context;

        registerReceiver(mBroadcastReceiver, new IntentFilter(BROADCAST_ACTION_ANSWER));

        //if (tbQR.isChecked() && qrCodeReaderView != null) {
        stopScanner();

        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(getIntent().getAction())) {
            try {
                handleNfcIntent(getIntent());
            } catch (SignatureDecodeException e) {
                e.printStackTrace();
            }
        }

        if((new Date().getTime() - app.dateTimeLastSync) / 1000 > 300 && provideService){
            app.startActionSync("Main", "","", true);
        }
        //}
    }

    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        AtomicBoolean ret= new AtomicBoolean(true);
        if (requestCode == MY_PERMISSION_REQUEST_CAMERA) {
            ret.set(false);
        }
        if (requestCode == MY_PERMISSION_REQUEST_INTERNET) {
            ret.set(false);
        }
        if (requestCode == MY_PERMISSION_REQUEST_NFC) {
            ret.set(false);
        }
        if (requestCode == MY_PERMISSION_REQUEST_FOREGROUND_SERVICE) {
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
            if (requestCode == MY_PERMISSION_REQUEST_NFC) {
                NfcAdapter mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
                if(mNfcAdapter != null) {
                    mNfcAdapter.setNdefPushMessageCallback(this, this);
                }
            }
        } else {
            Snackbar.make(mainLayout, "Permission request was denied.", Snackbar.LENGTH_SHORT)
                    .show();
        }
    }

    @Override public void onQRCodeRead(String text, PointF[] points) {
        stopScanner();
        try {
            codeReadTrigger(text);
        } catch (SignatureDecodeException e) {
            e.printStackTrace();
        }
        //pointsOverlayView.setPoints(points);
    }

    @SuppressLint("MissingPermission") private void shakeIt() {
        if (Build.VERSION.SDK_INT >= 26) {
            ((Vibrator) getSystemService(VIBRATOR_SERVICE)).vibrate(VibrationEffect.createOneShot(300, 10));
        } else {
            ((Vibrator) getSystemService(VIBRATOR_SERVICE)).vibrate(300);
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
                return new NdefMessage(NdefRecord.createMime(
                        "application/com.infoman.liquideconomycs", args.getBytes()));
                //return new NdefMessage(Utils.createNFCRecords(args));
        }else{
            if(!msg.equals(""))
                return new NdefMessage(NdefRecord.createMime(
                        "application/com.infoman.liquideconomycs", msg.getBytes()));
                //return new NdefMessage(Utils.createNFCRecords(msg));
        }
        return null;
    }

    @Override
    public void onNewIntent(Intent intent) {
        // onResume gets called after this to handle the intent
        super.onNewIntent(intent);
        setIntent(intent);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    private void requestINTERNETPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.INTERNET)) {
            Snackbar.make(mainLayout, getResources().getText(R.string.getINTERNETPermission),
                    Snackbar.LENGTH_INDEFINITE).setAction(getResources().getString(android.R.string.ok), view -> ActivityCompat.requestPermissions(MainActivity.this, new String[] {
                            Manifest.permission.INTERNET
                    }, MY_PERMISSION_REQUEST_INTERNET)).show();
        } else {
            Snackbar.make(mainLayout, getResources().getText(R.string.permission_is_not_available_INTERNET),
                    Snackbar.LENGTH_SHORT).show();
            ActivityCompat.requestPermissions(this, new String[] {
                    Manifest.permission.INTERNET
            }, MY_PERMISSION_REQUEST_INTERNET);
        }
    }

    private void requestFOREGROUND_SERVICEPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.FOREGROUND_SERVICE)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Snackbar.make(mainLayout, getResources().getText(R.string.getFOREGROUND_SERVICEPermission),
                        Snackbar.LENGTH_INDEFINITE).setAction(getResources().getString(android.R.string.ok), view -> ActivityCompat.requestPermissions(MainActivity.this, new String[] {
                                Manifest.permission.FOREGROUND_SERVICE
                        }, MY_PERMISSION_REQUEST_FOREGROUND_SERVICE)).show();
            }
        } else {
            Snackbar.make(mainLayout, getResources().getText(R.string.permission_is_not_available_FOREGROUND_SERVICE),
                    Snackbar.LENGTH_SHORT).show();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ActivityCompat.requestPermissions(this, new String[] {
                        Manifest.permission.FOREGROUND_SERVICE
                }, MY_PERMISSION_REQUEST_FOREGROUND_SERVICE);
            }
        }
    }

    private void requestCameraPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
            Snackbar.make(mainLayout, getResources().getText(R.string.getCAMERAPermission),
                    Snackbar.LENGTH_INDEFINITE).setAction(getResources().getString(android.R.string.ok), view -> ActivityCompat.requestPermissions(MainActivity.this, new String[] {
                            Manifest.permission.CAMERA
                    }, MY_PERMISSION_REQUEST_CAMERA)).show();
        } else {
            Snackbar.make(mainLayout, getResources().getText(R.string.permission_is_not_available_CAMERA),
                    Snackbar.LENGTH_SHORT).show();
            ActivityCompat.requestPermissions(this, new String[] {
                    Manifest.permission.CAMERA
            }, MY_PERMISSION_REQUEST_CAMERA);
        }
    }

    private void requestNFCPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.NFC)) {
            Snackbar.make(mainLayout, getResources().getText(R.string.getNFCPermission),
                    Snackbar.LENGTH_INDEFINITE).setAction(getResources().getString(android.R.string.ok), view -> ActivityCompat.requestPermissions(MainActivity.this, new String[] {
                            Manifest.permission.NFC
                    }, MY_PERMISSION_REQUEST_NFC)).show();
        } else {
            Snackbar.make(mainLayout, getResources().getText(R.string.permission_is_not_available_NFC),
                    Snackbar.LENGTH_SHORT).show();
            ActivityCompat.requestPermissions(this, new String[] {
                    Manifest.permission.NFC
            }, MY_PERMISSION_REQUEST_NFC);
        }
    }

    private void initQRCodeReaderView() {

        View content = getLayoutInflater().inflate(R.layout.content_decoder, mainLayout, true);

        qrCodeReaderView = findViewById(R.id.qrdecoderview);
        resultTextView = findViewById(R.id.result_text_view);
        PointsOverlayView pointsOverlayView = findViewById(R.id.points_overlay_view);
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

    //Тригер чтений QR кода
    //В случае если provideService то запускается проверка партнера на наличие в реестре
    //Иначе стартует сервис добавления\обновления идентификатора в дерево
    //и сервис синхронизации(останавливается по таймеру если синхронизация не начата)
    public void codeReadTrigger(String text) throws SignatureDecodeException {
        resultTextView.setText(text);
        String[] fields = Utils.parseQRString(text);
        if(provideService){
            if(fields.length != 2 || fields[1].equals("") || fields[0].equals("")) {
                Toast.makeText(getApplicationContext(), getResources().getString(R.string.ErrorReceivingPartnerData), Toast.LENGTH_LONG).show();}
            else{
                byte[] accepterPubKey = Utils.hexToByte(fields[0]);
                if(chekSig(accepterPubKey, decodeFromDER(Utils.hexToByte(fields[1])), accepterPubKey)) {
                        app.startActionFind("Main", ECKey.fromPublicOnly(accepterPubKey).getPubKeyHash(), 0L);
                }
                //TODO add uncheck msg
            }
        }else{
            shakeIt();
            if(fields.length < 3 || fields[1].equals("") || fields[2].equals("") || fields[0].equals("")){
               Toast.makeText(getApplicationContext(), getResources().getString(R.string.ErrorReceivingPartnerData),Toast.LENGTH_LONG).show();
            }else{
                byte[] providerPubKey = hexToByte(fields[0]);
                app.startActionInsert(ECKey.fromPublicOnly(providerPubKey).getPubKeyHash(), ageToBytes());
                app.addClient(providerPubKey);
                app.startActionSync("Main", fields[1], fields[2],false);
            }

        }
    }

    public void startScanner(){
        if(tbQR.isChecked() && qrCodeReaderView == null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                initQRCodeReaderView();
            } else {
                requestCameraPermission();
            }
        }
        scan_gen.setText(getResources().getString(R.string.QR_scanner));
        startBtn.setText(getResources().getString(R.string.stopScan));
    }

    public void stopScanner(){
        if(qrCodeReaderView != null) {
            qrCodeReaderView.stopCamera();
            qrCodeReaderView.setOnQRCodeReadListener(null);
            qrCodeReaderView.onDetachedFromWindow();
            qrCodeReaderView = null;
            clearQRCodeReaderViewView(mainLayout);
        }
        scan_gen.setText(getResources().getString(R.string.QR_generator));
        startBtn.setText(getResources().getString(R.string.startScan));
        generatePairingMsg();
    }

    public void generatePairingMsg() {
        ImageView img = findViewById(R.id.image);
        assert app.myKey.first != null;
        String msg = Utils.byteToHex((byte[]) app.myKey.first)+" ";
        if(!provideService) {
            //generate QR or NFC msg = pubKey & sig digest from pubKey
            assert app.myKey.second != null;
            msg = msg + Utils.byteToHex(ECKey.fromPrivate((byte[]) app.myKey.second).sign(Sha256Hash.wrap(Sha256Hash.hash((byte[]) app.myKey.first))).encodeToDER());

        }
        if(tbQR.isChecked()) {
            if (provideService) {
                SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                String signalServer = sharedPref.getString("Signal_server_URL", ""), token = sharedPref.getString("Signal_server_Token", "");
                assert signalServer != null;
                msg = msg + (!signalServer.equals("") ? signalServer + " " + token : "");
            }
            QRCodeWriter writer = new QRCodeWriter();
            try {
                BitMatrix bitMatrix = writer.encode(msg, BarcodeFormat.QR_CODE, 512, 512);
                int width = bitMatrix.getWidth(), height = bitMatrix.getHeight();
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

            scan_gen.setVisibility(View.VISIBLE);
            startBtn.setVisibility(View.VISIBLE);
            notation.setText(getResources().getString(R.string.annotation_qr));
        }else{
            scan_gen.setVisibility(View.INVISIBLE);
            startBtn.setVisibility(View.INVISIBLE);
            NfcAdapter mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
            if(mNfcAdapter != null) {
                //clear img
                img.setImageDrawable(null);
                Glide.with(getApplicationContext())
                        .asGif()
                        .load(R.drawable.nfc_img)
                        .into(img);
                notation.setText(getResources().getString(R.string.annotation_nfc));
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

    private void handleNfcIntent(Intent NfcIntent) throws SignatureDecodeException {
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