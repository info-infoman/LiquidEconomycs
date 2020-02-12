package com.example.liquideconomycs;

import android.util.Pair;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import java.io.IOException;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Date;

import static org.bitcoinj.core.Utils.sha256hash160;

public class Utils {

    public static byte getHashs = 0;
    public static byte hashs = 1;

    public static byte ROOT = 1; //
    public static byte BRANCH = 2;
    public static byte LEAF = 3;

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





}
