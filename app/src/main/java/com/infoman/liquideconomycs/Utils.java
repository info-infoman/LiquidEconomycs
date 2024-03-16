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
        EXTRA_MASTER        = "com.infoman.liquideconomycs.extra.mster",
        EXTRA_CMD           = "com.infoman.liquideconomycs.extra.cmd",
        ACTION_INSERT       = "com.infoman.liquideconomycs.action.insert",
        ACTION_FIND         = "com.infoman.liquideconomycs.action.find",
        ACTION_GENERATE_ANSWER = "com.infoman.liquideconomycs.action.getAnswer",
        ACTION_START_SYNC   = "com.infoman.liquideconomycs.action.start",

    //input param
        EXTRA_SIGNAL_SERVER = "com.infoman.liquideconomycs.extra.signalServer",
        EXTRA_PROVIDE_SERVICE = "com.infoman.liquideconomycs.extra.provideService",
        EXTRA_TOKEN         = "com.infoman.liquideconomycs.extra.token",
        EXTRA_PUBKEY        = "com.infoman.liquideconomycs.extra.pubKey",
        EXTRA_AGE           = "com.infoman.liquideconomycs.extra.age",
        EXTRA_MSG_TYPE      = "com.infoman.liquideconomycs.extra.msgType",
        EXTRA_PAYLOAD       = "com.infoman.liquideconomycs.extra.payload",

        BROADCAST_ACTION_ANSWER = "com.infoman.liquideconomycs.broadcast_action.answer",
        EXTRA_ANSWER        = "com.infoman.liquideconomycs.extra.answer";

    public static byte[] getBytesPart(byte[] src, int off, int len){
        byte[] result= new byte[len];
        System.arraycopy(src, off, result, 0, result.length);
        return result;
    }

    public static long compareDate(Date newDate, Date oldDate){
        return  (newDate.getTime() - oldDate.getTime())/86400000;
    }

    public static boolean compareDate(byte[] d1, byte[] d2, long maxAge){
        return (Utils.compareDate(Utils.reconstructAgeFromBytes(d1), Utils.reconstructAgeFromBytes(d2)) > maxAge);
    }

    public static Date reconstructAgeFromBytes(byte[] d) {
        long timestampRecovered = ((d[0]&0xFF) << 8);
        timestampRecovered += d[1]&0xFF;
        timestampRecovered *= 86400000;
        return new Date(timestampRecovered);
    }

    public static long getDayMilliByIndex(int index){
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime().getTime()-(86400000L*index);
    }

    public static byte[] Sig(byte[] privKey, byte[] digest) {
        return ECKey.fromPrivate(privKey).sign(Sha256Hash.wrap(Sha256Hash.hash(digest))).encodeToDER();
    }

    public static boolean chekSig(byte[] pubKey, ECKey.ECDSASignature sig, byte[] payload) throws SignatureDecodeException {
        return ECKey.verify(Sha256Hash.hash(payload), sig, pubKey);
    }

    public static String[] parseQRString(String str){
        String delimeter = " "; // Разделитель
        return str.split(delimeter); // Разделения строки str с помощью метода split()
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

}
