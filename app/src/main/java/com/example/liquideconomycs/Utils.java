package com.example.liquideconomycs;

import com.google.common.primitives.Ints;

import java.util.Date;

public class Utils {

    public static byte getHashs = 0;
    public static byte hashs = 1;

    public static byte ROOT = 1; //
    public static byte BRANCH = 2;
    public static byte LEAF = 3;

    public static byte[]  getMsgType(byte[] msg) {
        return getBytesPart(msg, 0, 1);
    }
    public static int  getLength(byte[] msg) {
        return Ints.fromByteArray(getBytesPart(msg, 1, 4));
    }
    public static int getSigLength(byte[] msg) {
        return Ints.fromByteArray(getBytesPart(msg, 5, 4));
    }
    public static byte[] getSig(byte[] msg, int SigLength) {
        return getBytesPart(msg, 9, SigLength);
    }
    public static byte[] getPayload(byte[] msg, int sigLength, int length) {
        return getBytesPart(msg, 9+sigLength, length);
    }



    public static byte[] getBytesPart(byte[] src, int off, int len){
        byte[] result= new byte[len];
        System.arraycopy(src, off, result, 0, result.length);
        return result;
    }

    public static Date reconstructFromBytes(byte[] d) {
        long timestampRecovered = (d[0] << 8);
        timestampRecovered += d[1];
        timestampRecovered *= 86400000;
        return new Date(timestampRecovered);
    }

    public static byte[] dateTobytes(){
        long time = new Date().getTime();  // time in ms since epoch
        time /= 86400000; // ms in a day
        byte[]res = new byte[2];
        res[0] = (byte)(time >>> 8);
        res[1] = (byte)(time);
        return res;
    }
}
