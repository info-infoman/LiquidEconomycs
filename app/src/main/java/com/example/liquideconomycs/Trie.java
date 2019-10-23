package com.example.liquideconomycs;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

import static org.bitcoinj.core.Utils.sha256hash160;

public class Trie {
    private RandomAccessFile TrieNodes, Leaves, FreeSpaceNodes, FreeSpaceLeaves;
    private byte[] hashSize = new byte[20];
    private static byte NODE = 1;
    private static byte LEAF = 2;
    private static byte BRANCH = 3;
    private static String BREAK = "TRIE_BREAK_REDUCE";
    private static byte[] EMPTY_STRING = null;
    Trie(String nodeDir) throws FileNotFoundException {
        TrieNodes = new RandomAccessFile(nodeDir+ "/nodes.dat", "rw");
        Leaves = new RandomAccessFile(nodeDir+ "/leaves.dat", "rw");
        FreeSpaceNodes = new RandomAccessFile(nodeDir+ "/freeSpaceNodes.dat", "rw");
        FreeSpaceLeaves = new RandomAccessFile(nodeDir+ "/freeSpaceLeaves.dat", "rw");
    }

    //we need save 10 000 000 000+ account for all people
    // and we need have sync func like "all for all" between two people(personal accounts trie), who met by chance
    //node
    //key size(1)/key(0-19)/hash sha256 (32)/child pointers array(32)  /child array(1-256*8)
    //00         /00*19    /00*32           /00*32                     /0000000000000000 max 2133 byte (ideal ‭39 062 500‬ =~76GB)
    //account
    //key(1)/age(2)/
    //00    /0000  /max 3 (ideal 10000000000=30GB)
    //total 106GB(ideal trie for 10 000 000 000 accounts)
    //
    //we are save all change: 1st in a free space pointers at , 2st in apped bytes in files nodes.dat && leaves.dat
    //free space pointers register it is special buffer  for story free pointers in nodes.dat && leaves.dat
    //deserialize = /size(2 bytes)/point(8 bytes)/ for nodes.dat. For 10 000 000 pointers *10 bytes = ~ 95Mb
    //deserialize = /point(8 bytes)/ for leaves.dat. For 10 000 000 pointers *8 bytes = ~ 76Mb

    public boolean Insert(byte[] key, byte[] pos) throws IOException {
        ByteArrayInputStream fined = new ByteArrayInputStream(Find(key, pos));

    }

    public byte[] Find(byte[] key, byte[] pos) throws IOException {
        byte Type = key.length > 1 ? NODE : LEAF;
        if(Type==LEAF){
            Leaves.seek(Longs.fromByteArray(pos));
            byte[] Age = new byte[2];
            Leaves.read(Age, 1, 2);
            return Bytes.concat(Age,pos); //account age && position
        }else{
            TrieNodes.seek(Longs.fromByteArray(pos));
            byte KeySize = TrieNodes.readByte();
            TrieNodes.seek(KeySize+32); //hash
            byte[] ChildsPos=new byte[32];
            TrieNodes.read(ChildsPos,0,32);

            if(!CheckChild(ChildsPos, Ints.fromBytes((byte)0, (byte)0, (byte)0, key[0]))){
                ByteBuffer buffer = ByteBuffer.allocate(10);
                buffer.put(BRANCH);
                buffer.put(pos);
                buffer.put(key[0]);
                return buffer.array();//position && offsets key of node branch
            }else{
                TrieNodes.seek((key[0]*8)-8);
                byte[] p = new byte[8];
                TrieNodes.read(p, 0, 8); //get file point
                ByteBuffer buffer = ByteBuffer.allocate(KeySize-1);
                byte[] child = Find(buffer.put(key, KeySize+1, KeySize).array(), p);
                ByteBuffer rtBuffer = ByteBuffer.allocate(10+child.length);
                rtBuffer.put(NODE);
                rtBuffer.put(pos);
                rtBuffer.put(key[0]);
                rtBuffer.put(child);
                return rtBuffer.array();//position && offsets key && node child
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

    public byte[] GetHash(byte[] pos, byte type) throws IOException {
        byte[] Hash = new byte[32];
        if(type==NODE) {
            TrieNodes.seek(Longs.fromByteArray(pos));
            byte KeySize = TrieNodes.readByte();
            TrieNodes.read(Hash, KeySize, 32);
        }else if(type==LEAF){
            Leaves.seek(Longs.fromByteArray(pos));
            byte[] Key = new byte[1];
            Leaves.read(Key, 0, 1);
            byte[] Age = new byte[2];
            Leaves.read(Age, 0, 2);
            byte[] ChildArray= new byte[0];
            Hash = CalcHash(type, Key, ChildArray, Age);
        }else{
            Hash = null;
        }
        return Hash;
    }

    private byte[] CalcHash(byte type, byte[] key, byte[] childArray, byte[] age) throws IOException {
        byte[] Hash;
        if(type==NODE) {
            byte[] Digest=key;
            ByteBuffer buffer = ByteBuffer.allocate(8);
            for(int i = 0; i < childArray.length+1;) {
                Digest = Bytes.concat(Digest, GetHash(buffer.put(childArray, i, 8).array(), type));
                i = i + 8;
            }
            buffer.clear();
            Hash = sha256hash160(Digest);
        }else if(type==LEAF){
            Hash = sha256hash160(Bytes.concat(key, age));
        }else{
            Hash = null;
        }
        return Hash;
    }
}