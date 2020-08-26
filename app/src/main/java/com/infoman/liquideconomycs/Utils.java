package com.infoman.liquideconomycs;

import android.content.res.AssetManager;
import android.nfc.NdefRecord;

import com.google.common.primitives.Bytes;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.SignatureDecodeException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static org.bitcoinj.core.ECKey.ECDSASignature.decodeFromDER;


public class Utils {
    public static byte
            getHashs    = 0,
            hashs       = 1,
            ROOT        = 1,
            BRANCH      = 2,
            LEAF        = 3;

    public static final String
        EXTRA_MESSAGE       = "com.infoman.liquideconomycs.message",
        EXTRA_MASTER        = "com.infoman.liquideconomycs.extra.mster",
        EXTRA_CMD           = "com.infoman.liquideconomycs.extra.cmd",
        //input fnc
        ACTION_GET_HASH     = "com.infoman.liquideconomycs.action.getHash",
        ACTION_INSERT       = "com.infoman.liquideconomycs.action.insert",
        ACTION_FIND         = "com.infoman.liquideconomycs.action.find",
        ACTION_DELETE       = "com.infoman.liquideconomycs.action.delete",
        ACTION_GENERATE_ANSWER = "com.infoman.liquideconomycs.action.getAnswer",
        ACTION_START        = "com.infoman.liquideconomycs.action.start",

        //input param
        EXTRA_SIGNAL_SERVER = "com.infoman.liquideconomycs.extra.signalServer",
        EXTRA_PROVIDE_SERVICE = "com.infoman.liquideconomycs.extra.provideService",
        EXTRA_TOKEN         = "com.infoman.liquideconomycs.extra.token",
        EXTRA_POS           = "com.infoman.liquideconomycs.extra.pos",
        EXTRA_PUBKEY        = "com.infoman.liquideconomycs.extra.pubKey",
        EXTRA_AGE           = "com.infoman.liquideconomycs.extra.age",
        EXTRA_MAX_AGE       = "com.infoman.liquideconomycs.extra.maxAge",
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
        long diffInMillies = Math.abs(newDate.getTime() - oldDate.getTime());
        return TimeUnit.DAYS.convert(diffInMillies, TimeUnit.MILLISECONDS);
    }

    public static Date reconstructAgeFromBytes(byte[] d) {
        long timestampRecovered = (d[0] << 8);
        timestampRecovered += d[1];
        timestampRecovered *= 86400000;
        return new Date(timestampRecovered);
    }

    public static byte[] ageToBytes(){
        long time = new Date().getTime();  // time in ms since epoch
        time /= 86400000; // ms in a day
        byte[]res = new byte[2];
        res[0] = (byte)(time >>> 8);
        res[1] = (byte)(time);
        return res;
    }

    public static byte[] getCommonKey(byte[] selfKey, byte[] key) {
        for(int i = 1; i < selfKey.length+1; i++){
            byte[] sK = getBytesPart(key, 0, selfKey.length-i);
            if(Arrays.equals(sK, getBytesPart(selfKey, 0, selfKey.length-i))){
                return sK;
            }
        }
        return null;
    }

    public static int getChildPosInArray(int key, byte type){
        if(type==BRANCH){
            return (key==0?0:(key * 8) - 8);
        }else{
            return (key==0?0:(key * 2) - 2);
        }
    }

    public static int getChildPosInMap(byte[]childsMap, int key){
        int result=0;
        BitSet prepare = BitSet.valueOf(childsMap);
        for(int i = 0; i < key+1;i++){
            if(prepare.get(i)) result=result+1;
        }
        return result;
    }

    public static int getChildsCountInMap(byte[]childsMap){
        return BitSet.valueOf(childsMap).cardinality();
    }

    public static boolean checkExistChildInMap(byte[]childsMap, int key){
        BitSet prepare = BitSet.valueOf(childsMap);
        return prepare.get(key);
    }

    public static byte[] changeChildInMap(byte[]childsMap, int key, boolean operation){
        for(int i=0; i < 32; i++){
            if(key>(i*8)-1 && key<(i*8)+9){
                byte[] p = new byte[1];
                p[0]=childsMap[i];
                BitSet prepare1 = BitSet.valueOf(p);
                for(int b=0;b<8;b++) {
                    if ((i * 8) + b == key) {
                        prepare1.set(b, operation);
                        return Bytes.concat(getBytesPart(childsMap,0,i),prepare1.toByteArray(),getBytesPart(childsMap,i+1,32-(i+1)));
                    }
                }
            }
        }
        return childsMap;//result.array();
    }

    public static byte[] Sig(byte[] privKey, byte[] digest) throws SignatureDecodeException {
        return ECKey.fromPrivate((byte[]) privKey).sign(Sha256Hash.wrap(Sha256Hash.hash((byte[]) digest))).encodeToDER();
    }

    public static boolean chekSig(byte[] pubKey, ECKey.ECDSASignature sig, byte[] payload) throws SignatureDecodeException {
        return ECKey.verify(Sha256Hash.hash(payload), sig, pubKey);
    }

    public static boolean copyAssetFolder(AssetManager assetManager, String fromAssetPath, String toPath) {
        try {
            String[] files = assetManager.list(fromAssetPath);
            boolean res = true;

            if (files.length==0) {
                //If it's a file, it won't have any assets "inside" it.
                res &= copyAsset(assetManager,
                        fromAssetPath,
                        toPath);
            } else {
                new File(toPath).mkdirs();
                for (String file : files)
                    res &= copyAssetFolder(assetManager,
                            fromAssetPath + "/" + file,
                            toPath + "/" + file);
            }
            return res;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean copyAsset(AssetManager assetManager, String fromAssetPath, String toPath) {
        InputStream in = null;
        OutputStream out = null;
        try {
            in = assetManager.open(fromAssetPath);
            new File(toPath).createNewFile();
            out = new FileOutputStream(toPath);
            copyFile(in, out);
            in.close();
            in = null;
            out.flush();
            out.close();
            out = null;
            return true;
        } catch(Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
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

    public static NdefRecord[] createNFCRecords(String msg) {

        NdefRecord[] records = new NdefRecord[1];

        byte[] payload = msg.getBytes(Charset.forName("UTF-8"));

        NdefRecord record = new NdefRecord(
                NdefRecord.TNF_WELL_KNOWN,  //Our 3-bit Type name format
                NdefRecord.RTD_TEXT,        //Description of our payload
                new byte[0],                //The optional id for our Record
                payload);                   //Our payload for the Record

        records[0] = record;

        return records;
    }



}
