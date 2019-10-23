package com.example.liquideconomycs;

import android.service.autofill.FieldClassification;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.BufferedInputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

import static org.bitcoinj.core.Utils.sha256hash160;

public class Trie {
    private RandomAccessFile TrieNodes;
    private byte[] hashSize = new byte[20];
    private static byte NODE = 1;
    private static byte ACCOUNT = 2;
    private static String BREAK = "TRIE_BREAK_REDUCE";
    private static byte[] EMPTY_STRING = null;
    Trie(String nodeDir) throws FileNotFoundException {
        TrieNodes = new RandomAccessFile(nodeDir+"/trieNodes.dat", "rw");
    }

    //we need save 10 000 000 000+ account for all people
    // and we need have sync func like "all for all" between two people(personal accounts trie), who met by chance
    //node
    //type(1)/key size(1)/key(0-19)                             /hash sha256 (32)/child lenght(1)/child array(1-256*8)
    //00     /00         /00000000000000000000000000000000000000/00x32           /00             /0000000000000000 max 2102 (ideal 10000000000=76GB)
    //account
    //type(1)/key(1)/age(2)  /
    //00     /00    /0000/max 4 (ideal 10000000000=37GB)
    //total 113GB(ideal trie for 10 000 000 000 accounts)

    //return account age or position of node(where we are stopped)
    public byte[] Find(byte[] key, byte[] pos) throws IOException {
        TrieNodes.seek(Longs.fromByteArray(pos));
        if(TrieNodes.readByte()==ACCOUNT){
            byte[] Age = new byte[2];
            TrieNodes.read(Age, 1, 2);
            return Age;
        }else{
            byte KeySize = TrieNodes.readByte();
            TrieNodes.seek(KeySize+32);
            byte[] ChildPos=new byte[32];
            TrieNodes.read(ChildPos,0,32);

            if(!CheckChild(ChildPos, Ints.fromBytes((byte)0, (byte)0, (byte)0, key[0]))){
                return pos;
            }else{
                TrieNodes.seek((key[0]*8)-8);
                byte[] p = new byte[8];
                TrieNodes.read(p, 0, 8);
                ByteBuffer buffer = ByteBuffer.allocate(KeySize-1);
                return Find(buffer.put(key, KeySize+1, KeySize).array(), p);
            }
        }
    }

    private boolean CheckChild(byte[]child, int key){
            byte mask=(byte)255; //1111 1111
            int del=(int)Math.floor(key/8);//0
            int pos=key-(del*8);//0
            if(del>pos) del=del-1;
            byte prepare=child[del];
            if(pos==2){
                prepare = (byte)(prepare<<1);
            }
            if(pos==3){
                prepare = (byte)(prepare<<2);
            }
            if(pos==4){
                prepare = (byte)(prepare<<3);
            }
            if(pos==5){
                prepare = (byte)(prepare<<4);
            }
            if(pos==6){
                prepare = (byte)(prepare<<5);//1100 0000
            }
            if(pos==7){
                prepare = (byte)(prepare<<6);//1100 0000
            }
            if(pos==0){//for pos 8
                prepare = (byte)(prepare<<7);
            }
            //for pos 1
            prepare = (byte)(prepare>>7);

            return prepare==mask;
    }

    public byte[] GetHash(byte[] pos) throws IOException {
        TrieNodes.seek(Longs.fromByteArray(pos));
        byte[] Hash = new byte[32];
        byte Type = TrieNodes.readByte();
        byte KeySize = TrieNodes.readByte();
        if(Type==NODE) {
            TrieNodes.read(Hash, KeySize, 32);
        }else if(Type==ACCOUNT){
            byte[] Key = new byte[KeySize];
            TrieNodes.read(Key, 0, KeySize);
            byte[] Age = new byte[2];
            TrieNodes.read(Age, 0, 2);
            byte[] ChildArray= new byte[0];
            Hash = CalcHash(Type, Key, ChildArray, Age);
        }else{
            return null;
        }
        return Hash;
    }

    private byte[] CalcHash(byte Type, byte[] Key, byte[] ChildArray, byte[] Age) throws IOException {
        byte[] Hash;
        if(Type==NODE) {
            byte[] Digest=Key;
            ByteBuffer buffer = ByteBuffer.allocate(8);
            for(int i = 0; i < ChildArray.length+1;) {
                Digest = Bytes.concat(Digest, GetHash(buffer.put(ChildArray, i, 8).array()));
                i = i + 8;
            }
            buffer.clear();
            Hash = sha256hash160(Digest);
        }else if(Type==ACCOUNT){
            Hash = sha256hash160(Bytes.concat(Key, Age));
        }else{
            return null;
        }
        return Hash;
    }







    private node getData(Long step, RandomAccessFile trieNodes) throws IOException {
        //long newLong = Longs.fromByteArray(oldLongByteArray);
        //int newInt = Ints.fromByteArray(oldIntByteArray);
        //trieNodes.seek(step);
        byte Type = trieNodes.readByte();
        byte KeySize = trieNodes.readByte();
        byte[] Key = new byte[KeySize];
        trieNodes.read(Key,0, KeySize);
        byte[] HashPos = new byte[8];
        byte childLenght = trieNodes.readByte();
        long childSize = 0L;
        byte[] bAge = new byte[2];
        int age = 0;
        if(bType.equals(NODE)){
            trieNodes.read(bHash,0, 8);
            trieNodes.read(bChildSize,0, 8);//node
            childSize = Longs.fromByteArray(bChildSize);
            step = step+KeySize+childSize+18;
        }else{
            trieNodes.read(bAge,0, 2);//node
            age = Ints.fromByteArray(bAge);
            step = step+KeySize+12;
        }


        return new node (bType, KeySize, bKey, bHash, childSize, age, step);
    }



    // a reduce implementation you can "break" out of
    private reduseCallBack reduce(byte[] accumulator, RandomAccessFile trieNodes, Long start, Long end , byte[] result) throws IOException {
        for (int i = 1; i < accumulator.length+1; i++) {
            reduseCallBack R = new reduseCallBack(result, Arrays.copyOfRange(accumulator, i-1, i), i-1, trieNodes, start, end);
            if (R.MSG == BREAK) break;
            return R;
        }
        return new reduseCallBack(result, null, 0, trieNodes, start, end);
    }

    class reduseCallBack {
        byte[] val;
        String MSG;
        int getIndex;
        reduseCallBack(byte[] newKey, byte[] letter, int currentIndex, RandomAccessFile trieNodes, Long start, Long end) throws IOException {

            if (newKey.length>0 && exist(newKey, trieNodes, start, end) !=-1) {
                getIndex = currentIndex; // save the current index so we know where to split the key
                MSG=BREAK;
            }
            if (newKey.length>0){
                ByteBuffer buffer = ByteBuffer.allocate(newKey.length+letter.length);
                val= buffer.put(newKey).put(letter).array();
            }else{
                val=letter;
            }

        }
    }

    private byte[] reduceReverse(byte[] end, byte[] callback){
        byte[] current;
        for (int i = end.length; i > 0; i--) {
            current = Arrays.copyOfRange(end, 0, i);
            String val = callback(current, end, i);
            if (val === BREAK) break
                    // if this is reached, it didn't break so return the original
                    // if the loop ends here since no match was found
                    current = end;

        }
        return current;
    }

    //return age
    public int get(byte[] key, RandomAccessFile trieNodes, Long start, Long end) throws IOException {
        // if the key exists already, return it
        start = exist(key, trieNodes, start, end);
        if(start!= -1){
            node d = getData(start, trieNodes);
            return d.age;
        }else{
            reduseCallBack getKey = reduce(key, trieNodes, start, end, EMPTY_STRING);
            int getIndex = getKey.getIndex;
            //console.log(data)
            Long step = exist(getKey.val, trieNodes, start, end);
            if ( step!=-1) {
                node d = getData(step, trieNodes);
                return gett(Arrays.copyOfRange(getKey.val, getIndex, getKey.val.length), trieNodes, d.step-d.childSize, d.step);
            } else {
                // no matches
                return 0;
            }
        }
    }

    private int gett(byte[] key, RandomAccessFile trieNodes, Long start, Long end) throws IOException {
        return get(key, trieNodes, start, end);
    }


    public static void main(String[] args) {
        byte key = 16;
        byte[] child=new byte[2];
        child[0]=(byte)255;
        child[1]=(byte)254;
        byte mask=1; //0001
        int f =(key/8)-1;
        int pos = key-(f*8);
        byte prepare=child[f];
        if(pos==1){
            prepare = (byte)(prepare>>7);
        }
        if(pos==2){
            prepare = (byte)(prepare<<1);
        }
        if(pos==3){
            prepare = (byte)(prepare<<2);
        }
        if(pos==4){
            prepare = (byte)(prepare<<3);
        }
        if(pos==5){
            prepare = (byte)(prepare<<4);
        }
        if(pos==6){
            prepare = (byte)(prepare<<5);
        }
        if(pos==7){
            prepare = (byte)(prepare<<6);
        }
        if(pos==8){
            prepare = (byte)(prepare<<7);
        }
        if(prepare==mask){
            System.out.println(1);
        }else{
            System.out.println(0);
        }

    }
}