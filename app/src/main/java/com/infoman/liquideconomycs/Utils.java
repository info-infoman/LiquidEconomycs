package com.infoman.liquideconomycs;

import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.SignatureDecodeException;

import java.util.Calendar;
import java.util.Date;

public class Utils {

    public static byte
            getHashs    = 0,
            hashs       = 1;

    public static final String
        ACTION_START_SYNC   = "com.infoman.liquideconomycs.action.start",
        EXTRA_SIGNAL_SERVER = "com.infoman.liquideconomycs.extra.signalServer",
        EXTRA_PROVIDE_SERVICE = "com.infoman.liquideconomycs.extra.provideService",
        EXTRA_TOKEN         = "com.infoman.liquideconomycs.extra.token";

    public static byte[] getBytesPart(byte[] src, int off, int len){
        byte[] result= new byte[len];
        System.arraycopy(src, off, result, 0, result.length);
        return result;
    }

    public static int getDayMilliByIndex_(int index){
        int year = 0;
        int month = 0;
        int day = 0;
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(Calendar.DAY_OF_MONTH, index);
        year = calendar.get(Calendar.YEAR) * 10000;
        month = (calendar.get(Calendar.MONTH) + 1) * 100;
        day = calendar.get(Calendar.DAY_OF_MONTH);
        return year + month + day;
    }

    public static boolean chekSig(byte[] pubKey, ECKey.ECDSASignature sig, byte[] payload) throws SignatureDecodeException {
        return ECKey.verify(Sha256Hash.hash(payload), sig, pubKey);
    }

    public static String[] parseQRString(String str){
        String delimeter = " ";
        return str.split(delimeter);
    }

    public static String byteToHex(byte[] b1) {
        StringBuilder strBuilder = new StringBuilder();
        for(byte val : b1) {
            strBuilder.append(String.format("%02x", val&0xff));
        }
        return strBuilder.toString();
    }

    public static byte[] hexToByte(String str) {

        byte[] val = new byte[str.length() / 2];
        for (int i = 0; i < val.length; i++) {
            int index = i * 2, j;
            try {
                j = Integer.parseInt(str.substring(index, index + 2), 16);
            }catch (NumberFormatException e){
                j = 0;
            }
            val[i] = (byte) j;
        }
        return val;
    }

    public static void startIntent(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
            return;
        }
        context.startService(intent);
    }

    public static long getRandomNumber(long min, long max) {
        return min + (long) (Math.random() * (max - min));
    }


}
