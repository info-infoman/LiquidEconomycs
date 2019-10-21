package com.example.liquideconomycs;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

public class Trie {
    RandomAccessFile TrieNodes;
    private byte[] hashSize = new byte[20];
    private static byte NODE = (byte)0xFF;
    private static byte ACCOUNT = (byte)0x01;
    private static String BREAK = "TRIE_BREAK_REDUCE";
    private static byte[] EMPTY_STRING = null;
    Trie(String nodeDir) throws FileNotFoundException {
        TrieNodes = new RandomAccessFile(nodeDir+"/trieNodes.dat", "rw");
    }

    //node
    //type(1)/key size(1)/key(0-19)                             /child lenght(1)/child array(1-256*8)/hash coordinate(8)/
    //00     /00         /00000000000000000000000000000000000000/00             /0000000000000000 /0000000000000000  / max 286 (ideal 10000000000=75GB)
    //account
    //type(1)/key(1)/age(4)  /
    //00     /00    /00000000/max 6 (ideal 10000000000=55GB)

    //hashNode
    //type(1)/key size(1)/key(0-31)                             /child lenght(1)/child array(1-256*8)
    //00     /00         /00000000000000000000000000000000000000/00             /0000000000000000  max 290 (ideal 10000000000=75GB)
    //hashAccount
    //type(1)/key(1)
    //00     /00    max 2 (ideal 10000000000=18GB)
    //summ = ~224GB (ideal trie per 10000000000 accounts story)


    class node {
        byte[]   bType;
        int      KeySize;
        byte[]   bKey;
        byte[]   bHash;
        long     childSize;
        int      age;
        long     step;

        node(byte[] a, int b, byte[] c, byte[] d, long e, int f, long g)
        {
            bType = a;
            KeySize = b;
            bKey = c;
            bHash = d;
            childSize = e;
            age = f;
            step = g;
        }
    }

    public byte[] getHash(long coordonate) throws IOException {
        ArrayList dHashes = new ArrayList();
        Long step;
        for (step = coordonate; step < TrieNodes.length();) {
            TrieNodes.seek(step);
            node NodeData = getData(step, TrieNodes);
            dHashes.add(NodeData.bHash);//old hash
            step = NodeData.step;
        }

        Collections.sort(dHashes, new Comparator<byte[]>() {
            @Override
            public int compare(byte[] x, byte[] y) {
                for (int i = x.length - 1; i >= 0; i--) {
                    int r = x[i] - y[i];
                    if (r != 0) {
                        return r;
                    }
                }
                return 0;
            }

        });

        ByteBuffer buffer = ByteBuffer.allocate(dHashes.size());
        for (int i = 0; i < dHashes.size(); i++) {
            buffer.put((byte[]) dHashes.get(i));
        }

        return  CRC64.fromBytes(buffer.array()).getBytes();
    }

    private node getData(Long step, RandomAccessFile trieNodes) throws IOException {
        //long newLong = Longs.fromByteArray(oldLongByteArray);
        //int newInt = Ints.fromByteArray(oldIntByteArray);
        //trieNodes.seek(step);
        byte[] bType = new byte[0];
        trieNodes.read(bType,0,1);
        //TrieNodes.seek(1);

        byte[] bKeySize = new byte[0];
        trieNodes.read(bKeySize,0,1);
        int KeySize = Ints.fromByteArray(bKeySize);

        //TrieNodes.seek(1);
        byte[] bKey = new byte[KeySize];
        trieNodes.read(bKey,0, KeySize);

        //TrieNodes.seek(KeySize);

        //TrieNodes.seek(8);
        byte[] bHash = new byte[8];
        byte[] bChildSize = new byte[8];
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

    private long exist(byte[] key, RandomAccessFile trieNodes, Long start, Long end) throws IOException {
        Long step=start;
        for (; step < end;) {
            trieNodes.seek(step);
            node d = getData(step, trieNodes);
            step=d.step;
            if(d.bKey.equals(key)) return step;
        }
        return -1;
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


}