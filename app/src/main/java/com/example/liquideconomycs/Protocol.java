package com.example.liquideconomycs;

import com.google.common.primitives.Ints;

public class Protocol {

    public static byte getHashs = 0;
    public static byte Hashs = 1;

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
}
