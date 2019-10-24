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
    private RandomAccessFile trieNodes, leaves, freeSpaceNodes, freeSpaceLeaves;
    private byte[] hashSize = new byte[20];
    private static byte[] rootPos = Longs.toByteArray(0L);
    private static byte NODE = 1; //
    private static byte LEAF = 2;
    private static byte BRANCH = 3;
    private static byte ROOT = 4;
    private static String BREAK = "TRIE_BREAK_REDUCE";
    private static byte[] EMPTY_STRING = null;
    Trie(String nodeDir) throws FileNotFoundException {
        trieNodes = new RandomAccessFile(nodeDir+ "/nodes.dat", "rw");
        leaves = new RandomAccessFile(nodeDir+ "/leaves.dat", "rw");
        freeSpaceNodes = new RandomAccessFile(nodeDir+ "/freeSpaceNodes.dat", "rw");
        freeSpaceLeaves = new RandomAccessFile(nodeDir+ "/freeSpaceLeaves.dat", "rw");
    }

    //we need save 10 000 000 000+ account for all people
    // and we need have sync func like "all for all" between two people(personal accounts trie), who met by chance
    //       Lp       Ad         Bp      Lp  Ad         Bp     Lp   Ad         Bp   Lp   Ad
    //1)ROOT(FF)-FFFF(FF) 2)ROOT(FF)-FF(FF)-(FF) 3)ROOT(FF)-FF(FF)-(FF) 4)ROOT(FF)-(FF)(FF)-(FF)
    //                                     -(0F)                  -(OF)
    //                                                        (OF)-(OF)
    //
    //                                                                             RQ
    //ROOT(content BRANCH or LEAF)
    //hash sha256hash160(20)/child pointers array(32)  /child array(1-256*8)
    //00*20                 /00*32                     /0000000000000000 max 2100 byte
    //BRANCH(content LEAF)
    //type(1)/key size(1)/key(1-19)/hash sha256hash160(20)/leafPointers array(32)  /leafArray(1-256*8)
    //00     /00         /00*19    /00*20                 /00*32                   /00*8              max 2121 byte (ideal ‭39 062 500‬(leaves) =~301Mb)
    //LEAF(content account age)
    //type(1)/key size(1)/key(1-19)/hash sha256hash160(20)/agePointers array(32)  /ageArray(1-256*2)/
    //00     /00         /00*19    /00*20                 /00*32                  /00*2               max 585 (ideal 10000000000(account)=21GB)
    //total 22GB(ideal trie for 10 000 000 000 accounts)
    //
    //we are save all change: 1st in a free space pointers at , 2st in apped bytes in files nodes.dat && leaves.dat
    //free space pointers register it is special buffer  for story free pointers in nodes.dat && leaves.dat
    //deserialize = /size(2 bytes)/point(8 bytes)/ for nodes.dat. For 10 000 000 pointers *10 bytes = ~ 95Mb
    //deserialize = /point(8 bytes)/ for leaves.dat. For 10 000 000 pointers *8 bytes = ~ 76Mb

    public boolean insert(byte[] key, byte[] age) throws IOException {
        ByteArrayInputStream fined = new ByteArrayInputStream(find(key, rootPos));
        boolean result = recursiveInsert(key, age, fined);
        fined.close();
        return result;
    }

    private boolean recursiveInsert(byte[] key, byte[] age, ByteArrayInputStream fined) throws IOException {
        byte[] type=new byte[1];
        fined.read(type,0,1);
        byte[] pos=new byte[8];
        fined.read(pos,0,8);
        if(type[0]==LEAF){
            byte[] ageL=new byte[2];
            fined.read(ageL,0,2);
            if(ageL==age) return false;
            leaves.seek(Longs.fromByteArray(pos));
            leaves.write(age, 0,2);
            return true;
        }else{
            byte[] keyN=new byte[8];
            fined.read(keyN,0,8);
            if(type[0]==BRANCH){


            }else{

            }
        }
    }

    public byte[] find(byte[] key, byte[] pos) throws IOException {
        byte type = key.length > 1 ? key.length == 20 ? ROOT : NODE : LEAF;
        if(type==LEAF){
            leaves.seek(Longs.fromByteArray(pos));
            byte[] age = new byte[2];
            leaves.read(age, 0, 2);
            ByteBuffer buffer = ByteBuffer.allocate(11);
            return buffer.put(type).put(pos).put(age).array(); //account age && position
        }else{
            trieNodes.seek(Longs.fromByteArray(pos));
            if(type==ROOT){

            }
            byte keySize = trieNodes.readByte();
            byte[] keyN = new byte[keySize];
            trieNodes.read(keyN,0,keySize);
            trieNodes.seek(20); //hash
            byte[] childsPos=new byte[32];
            trieNodes.read(childsPos,0,32);
            int ChildsCount=getChildsCount(childsPos);
            byte[] childs=new byte[ChildsCount*8];
            trieNodes.read(childs,0,ChildsCount*8);

            if(!checkChild(childsPos, Ints.fromBytes((byte)0, (byte)0, (byte)0, key[0]))){
                ByteBuffer buffer = ByteBuffer.allocate(9+keySize+32+ChildsCount*8);
                return buffer.put(BRANCH).put(pos).put(keyN).put(childsPos).put(childs).array();//position & kay & offsets key of node branch
            }else{
                trieNodes.seek((key[0]*8)-8);
                byte[] p = new byte[8];
                trieNodes.read(p, 0, 8); //get file point
                ByteBuffer buffer = ByteBuffer.allocate(keySize-1);
                byte[] child = find(buffer.put(key, keySize+1, keySize).array(), p);
                ByteBuffer rtBuffer = ByteBuffer.allocate(10+child.length);
                return rtBuffer.put(NODE).put(pos).put(key[0]).put(child).array();//position && offsets key && node child
            }
        }
    }

    private int getChildsCount(byte[]child){
        int result=0;
        byte mask=(byte)255; //1111 1111
        for(int i=0;i<32;i++){
            byte prepare=child[i];
            for(i=0;i<7;i++){
                if(i==2){
                    prepare = (byte)(prepare<<1);
                }
                if(i==3){
                    prepare = (byte)(prepare<<2);
                }
                if(i==4){
                    prepare = (byte)(prepare<<3);
                }
                if(i==5){
                    prepare = (byte)(prepare<<4);
                }
                if(i==6){
                    prepare = (byte)(prepare<<5);//1100 0000
                }
                if(i==7){
                    prepare = (byte)(prepare<<6);//1100 0000
                }
                if(i==0){//for pos 8
                    prepare = (byte)(prepare<<7);
                }
                //for pos 1
                prepare = (byte)(prepare>>7);

                if(prepare==mask) result=result+1;
            }
        }
        return result;
    }

    private boolean checkChild(byte[]child, int key){
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

    public byte[] getHash(byte[] pos, byte type, byte[] key) throws IOException {
        byte[] hash = new byte[20];
        if(type==NODE) {
            trieNodes.seek(Longs.fromByteArray(pos));
            byte keySize = trieNodes.readByte();
            trieNodes.read(hash, keySize, 20);
        }else if(type==LEAF){
            //todo we are need to save key suffix in LEAF?
            leaves.seek(Longs.fromByteArray(pos));
            byte[] age = new byte[2];
            leaves.read(age, 0, 2);
            byte[] childArray= new byte[0];
            hash=calcHash(type, key, childArray, age);
        }else{
            hash = null;
        }
        return hash;
    }

    private byte[] calcHash(byte type, byte[] key, byte[] childArray, byte[] age) throws IOException {
        byte[] hash;
        if(type==NODE) {
            byte[] digest=key;
            ByteBuffer buffer = ByteBuffer.allocate(8);
            for(int i = 0; i < childArray.length+1;) {
                digest = Bytes.concat(digest, getHash(buffer.put(childArray, i, 8).array(), type, key));
                i = i + 8;
            }
            buffer.clear();
            hash = sha256hash160(digest);
        }else if(type==LEAF){
            hash = sha256hash160(Bytes.concat(key, age));
        }else{
            hash = null;
        }
        return hash;
    }
}