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


public class Utils {
    public static byte getHashs = 0;
    public static byte hashs = 1;

    public static byte ROOT = 1; //
    public static byte BRANCH = 2;
    public static byte LEAF = 3;

    public static final String EXTRA_MESSAGE = "com.infoman.liquideconomycs.MESSAGE";

    public static final String EXTRA_MASTER = "com.infoman.liquideconomycs.extra.MASTER";
    public static final String EXTRA_CMD = "com.infoman.liquideconomycs.extra.CMD";
    //input fnc
    public static final String ACTION_GetHash = "com.infoman.liquideconomycs.action.GetHash";
    public static final String ACTION_Insert = "com.infoman.liquideconomycs.action.Insert";
    public static final String ACTION_Find = "com.infoman.liquideconomycs.action.Find";
    public static final String ACTION_Delete = "com.infoman.liquideconomycs.action.Delete";
    public static final String ACTION_GenerateAnswer = "com.infoman.liquideconomycs.action.GetAnswer";

    //input params
    public static final String ACTION_Start = "com.infoman.liquideconomycs.action.Start";
    public static final String EXTRA_SIGNAL_SERVER = "com.infoman.liquideconomycs.extra.SIGNAL_SERVER";
    public static final String EXTRA_Provide_service = "com.infoman.liquideconomycs.extra.Provide_service";
    public static final String EXTRA_Token = "com.infoman.liquideconomycs.extra.Token";;
    public static final String EXTRA_POS = "com.infoman.liquideconomycs.extra.POS";
    public static final String EXTRA_PUBKEY = "com.infoman.liquideconomycs.extra.PUBKEY";
    public static final String EXTRA_AGE = "com.infoman.liquideconomycs.extra.AGE";
    public static final String EXTRA_MSGTYPE = "com.infoman.liquideconomycs.extra.MSGTYPE";
    public static final String EXTRA_PAYLOAD = "com.infoman.liquideconomycs.extra.PAYLOAD";

    public static final String BROADCAST_ACTION_ANSWER = "com.infoman.liquideconomycs.broadcast_action.ANSWER";
    public static final String EXTRA_ANSWER = "com.infoman.liquideconomycs.extra.ANSWER";

    public static byte[] getBytesPart(byte[] src, int off, int len){
        byte[] result= new byte[len];
        System.arraycopy(src, off, result, 0, result.length);
        return result;
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

    public static byte[] sigMsg(byte[] privKey, byte msgType, byte[] payload) {
        //flip type
        byte[] digest = new byte[1];
        digest[0] = msgType;
        digest = Sha256Hash.hash(Bytes.concat(digest, payload));
        ECKey key = ECKey.fromPrivate(privKey);
        ECKey.ECDSASignature sig = key.sign(Sha256Hash.wrap(digest));
        return sig.encodeToDER();
    }

    public static boolean chekSigMsg(byte[] pubKey, byte[] sig, byte msgType, byte[] payload) throws SignatureDecodeException {
        byte[] digest = new byte[1];
        digest[0] = msgType;
        digest = Sha256Hash.hash(Bytes.concat(digest, payload));
        ECKey publicKey = ECKey.fromPublicOnly(pubKey);
        return publicKey.verify(digest,sig);
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
            int index = i * 2;
            int j;
            try {
                j = Integer.parseInt(str.substring(index, index + 2), 16);
            }catch (NumberFormatException e){
                j = 0;
            }
            val[i] = (byte) j;
        }
        return val;
    }

    public static NdefRecord[] createNFCrecords(String msg) {

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
