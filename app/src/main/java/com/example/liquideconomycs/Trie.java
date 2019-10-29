package com.example.liquideconomycs;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.BitSet;

import static org.bitcoinj.core.Utils.sha256hash160;

public class Trie {
    private RandomAccessFile trie, freeSpace;
    private byte[] hashSize = new byte[20];
    private static byte[] rootPos = Longs.toByteArray(0L);
    private static byte ROOT = 1; //
    private static byte BRANCH = 2;
    private static byte LEAF = 3;
    private static String BREAK = "TRIE_BREAK_REDUCE";
    private static byte[] EMPTY_STRING = null;
    Trie(String nodeDir) throws FileNotFoundException {
        trie = new RandomAccessFile(nodeDir+ "/trie.dat", "rw");
        freeSpace = new RandomAccessFile(nodeDir+ "/freeSpace.dat", "rw");
    }

    //we need save 10 000 000 000+ account for all people
    // and we need have sync func like "all for all" between two people(personal accounts trie), who met by chance
    //Compress by key & adaptive points size & save age in leaf (Lp - leaf point, Bp - branch point, Ad - account data):
    //     Bp     Lp  Ad      Bp  Bp    Lp  Ad       Bp  Bp  Bp  lp  Ad
    //1)R(FF)FFFF(FF)(FF) 2)R(FF)(FF)FF(FF)(FF) 3)R (FF)(FF)(FF)(FF)(FF) 1) insert FFFFFFFFFF
    //                          \     \  \(0F)         \  \    \  \(0F) 2) insert FFF0FFFFFF, FFFFFFFF0F, FFFFFFAFFF
    //                           |     |                |  |    |       3) insert FFFFRQAFFF
    //                           |     Lp  Ad           |  |    |Lp  Ad
    //                           |    (AF)(FF)          |  \    (AF)(FF)
    //                           |                      |   Lp    Ad
    //                           |LP     Ad           Lp|  (RQ)AF(FF)
    //                          (F0)FFFF(FF)          (F0)FFFF(FF)
    //if key size 20 and age 2:
    // Lp-76b            Bp-143b Lp-191b        Bp-222b Lp-249b
    // total insert 22*5 = 110 byte, trie size 471 byte (but this is a lazy expansion, when the tree is compacted, compression will occur)
    //ROOT(content BRANCH)
    //type(1)/hash sha256hash160(20)/child point array(1-256*8)
    //00     /00*20                 /0000000000000000 max 2069 byte
    //BRANCH(content child BRANCH & LEAF)
    //type(1)/key size(1)/key(1-17)/hash sha256hash160(20)/childsMap(32)  /leafPointArray(1-256*8)
    //00     /00         /00*19    /00*20                 /00*32          /00*8              max 2121 byte (ideal ‭39 062 500‬(leaves) =~301Mb)
    //LEAF(content account age)
    //type(1)/key size(1)/key(1-18)/hash sha256hash160(20)/childsMap(32)  /ageArray(1-256*2)/
    //00     /00         /00*20    /00*20                 /00*32          /00*2               max 585 (ideal 10000000000(account)=21GB)
    //total 22GB(ideal trie for 10 000 000 000 accounts)
    //
    //we are save all change: 1st in a free space pointers at , 2st in apped bytes in files trie.dat
    //free space pointers register it is special buffer  for story free pointers in trie.dat
    //deserialize = /size(2 bytes)/point(8 bytes)/ for trie.dat. For 10 000 000 pointers *10 bytes = ~ 95Mb

    public byte[] insert(byte[] key, byte[] age) throws IOException {
        ByteArrayInputStream fined = new ByteArrayInputStream(search(key, rootPos));
        byte[] result = recursiveInsert(key, age, fined, 0);
        fined.close();
        return result;
    }

    private byte[] recursiveInsert(byte[] key, byte[] age, ByteArrayInputStream fined, long pos) throws IOException {
        byte[] hash;
        BitSet childsMap = new BitSet(256);
        if(fined.available()>0){
            byte[] type=new byte[1];
            fined.read(type,0,1);
            if(type[0]==ROOT){
                if(pos==0){
                    //todo find free space
                    trie.seek(trie.length()+1);
                    pos=trie.getFilePointer();
                    //
                    trie.write(LEAF);
                    ByteBuffer buffer = ByteBuffer.allocate(18);
                    byte[] lKey= buffer.put(key, 1, 18).array();
                    trie.writeByte(lKey.length);
                    trie.write(lKey);
                    hash=calcHash(LEAF, lKey, age);
                    trie.write(hash);
                    childsMap.set(key[19], true);
                    trie.write(childsMap.toByteArray());
                    trie.write(age);
                }
                //set pos
                trie.seek(22+((key[0]*8)-8));
                trie.write(Longs.toByteArray(pos));
                trie.seek(22);
                byte[] childArray= new byte[2048];
                trie.read(childArray,0,2048);
                trie.seek(2);
                hash=calcHash(ROOT, null, childArray);
                trie.write(hash);
                return hash;

            }else{
                byte[] selfPos= new byte[8];
                fined.read(selfPos,0,8);
                byte[] selfKeySize= new byte[1];
                fined.read(selfKeySize,0,1);
                byte[] selfKey= new byte[selfKeySize[0]];
                fined.read(selfKey,0,selfKeySize[0]);
                byte[] selfChildMap= new byte[32];
                fined.read(selfChildMap,0,32);
                byte[] selfChildArraySize= new byte[8];
                fined.read(selfChildArraySize,0,8);
                byte[] selfChildArray= new byte[Ints.fromByteArray(selfChildArraySize)];
                fined.read(selfChildArray,0,Ints.fromByteArray(selfChildArraySize));
                if(type[0]==BRANCH){
                    if(pos==0){
                        //todo find free space
                        trie.seek(trie.length()+1);
                        pos=trie.getFilePointer();
                        //
                        trie.write(LEAF);
                        ByteBuffer buffer = ByteBuffer.allocate(18);
                        byte[] lKey= buffer.put(key, 1, 18).array();
                        trie.writeByte(lKey.length);
                        trie.write(lKey);
                        hash=calcHash(LEAF, lKey, age);
                        trie.write(hash);

                        childsMap.set(key[19], true);
                        trie.write(childsMap.toByteArray());
                        trie.write(age);
                    }
                    childsMap.valueOf(selfChildMap);
                    childsMap.set(selfKey[selfKeySize[0]-1], true);

                    trie.seek(Longs.fromByteArray(selfPos)+2+selfKeySize[0]+21);//go to child map
                    trie.write(childsMap.toByteArray());
                    //todo create new childs array
                    //todo create new hash
                    //todo if fined is available()>0 recursiveInsert()

                    return res;

                }else{

                    return true;
                }
            }
        }else{
            trie.setLength(0);
            trie.write(ROOT);
            byte[] trieTmp =new byte[2069];
            trie.write(trieTmp);
            trie.write(LEAF);
            ByteBuffer buffer = ByteBuffer.allocate(18);
            byte[] lKey= buffer.put(key, 1, 18).array();
            trie.writeByte(lKey.length);
            trie.write(lKey);
            byte[] tmpL=new byte[52];
            trie.write(tmpL);
            trie.seek(22+(key[0]*8));
            trie.write(Longs.toByteArray(2070));
            return insert(key, age);
        }

    }

    public byte[] search(byte[] key, byte[] pos) throws IOException {
        trie.seek(Longs.fromByteArray(pos));
        byte type = key.length == 20 ? ROOT : trie.readByte();
        if(type==ROOT){
            trie.seek(trie.getFilePointer()+21+(key[0]*8));
            byte[] childPos=new byte[8];
            trie.read(childPos,0,8);
            if(Ints.fromByteArray(childPos)>0){
                ByteBuffer buffer = ByteBuffer.allocate(key.length-1);
                byte[] child = search(buffer.put(key, key.length+1, key.length).array(), childPos);
                ByteBuffer rtBuffer = ByteBuffer.allocate(2+child.length);
                //todo key?
                return rtBuffer.put(child).put(ROOT).put(key[0]).array();
            }
            ByteBuffer rtBuffer = ByteBuffer.allocate(2);
            return rtBuffer.put(ROOT).put(key[0]).array();
        }else{
            byte keySize = trie.readByte();
            byte[] keyNode = new byte[keySize];
            trie.read(keyNode,0,keySize);
            //todo compare keyNode vs key
            trie.seek(trie.getFilePointer()+21); //skip hash
            byte[] childsMap=new byte[32];
            trie.read(childsMap,0,32);
            int childsCount=getChildsCount(childsMap);
            byte[] childsArraySize= type == BRANCH ? Ints.toByteArray(childsCount*8) : Ints.toByteArray(childsCount*2);
            byte[] childs = type == BRANCH ? new byte[childsCount*8] : new byte[childsCount*2];
            trie.read(childs,0,childs.length);
            //search exist true of prefix of key in childsMap
            //if not found return position node & keyNode(full key node) & childsMap & childsArray(node pointers or age)
            //else search in child, return prefix [0] of keyNode
            if(!checkChild(childsMap, Ints.fromBytes((byte)0, (byte)0, (byte)0, key[0]))){
                ByteBuffer buffer = ByteBuffer.allocate(10+keySize+32+childsArraySize.length+childs.length);
                return buffer.put(type).put(pos).put(keySize).put(keyNode).put(childsMap).put(childsArraySize).put(childs).array();
            }else{
                int childPosInMap = getChildPos(childsMap, Ints.fromBytes((byte)0, (byte)0, (byte)0, key[0]));
                trie.seek(trie.getFilePointer() - childs.length);//go back to childsArray[0]
                if(type == BRANCH){
                    trie.seek(trie.getFilePointer()+(childPosInMap*8)-8);
                    byte[] childPos = new byte[8];
                    trie.read(childPos, 0, 8);
                    ByteBuffer buffer = ByteBuffer.allocate(keySize-1);
                    byte[] child = search(buffer.put(key, 1, keySize).array(), childPos);
                    ByteBuffer rtBuffer = ByteBuffer.allocate(10+keySize+32+childsArraySize.length+childs.length+child.length);
                    //todo key?
                    return rtBuffer.put(child).put(BRANCH).put(pos).put(keySize).put(keyNode).put(childsMap).put(childsArraySize).put(childs).array();
                }else{
                    //todo key?
                    trie.seek(trie.getFilePointer()+(childPosInMap*2)-2);
                    byte[] childAge = new byte[2];
                    trie.read(childAge, 0, 2);
                    ByteBuffer rtBuffer = ByteBuffer.allocate(10+keySize+2+32+childsArraySize.length+childs.length);
                    return rtBuffer.put(LEAF).put(pos).put(keySize).put(keyNode).put(childAge).put(childsMap).put(childsArraySize).put(childs).array();
                }
            }
        }
    }

    private int getChildPos(byte[]childsMap, int key){
        int result=0;
        byte mask=(byte)255; //1111 1111
        for(int s=0;s<33;s++){
            byte prepare=childsMap[s];
            for(int i=0;i<7;i++){
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

                if((s*8)+i==key) return result;
            }
        }
        return result;
    }

    private int getChildsCount(byte[]childsMap){
        int result=0;
        byte mask=(byte)255; //1111 1111
        for(int s=0;s<32;s++){
            byte prepare=childsMap[s];
            for(int i=0;i<7;i++){
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

    private boolean checkChild(byte[]childsMap, int key){
            byte mask=(byte)255; //1111 1111
            int del=(int)Math.floor(key/8);//0
            int pos=key-(del*8);//0
            if(del>pos) del=del-1;
            byte prepare=childsMap[del];
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

    public byte[] getHash(byte[] pos) throws IOException {
        byte[] hash = new byte[20];
        trie.seek(Longs.fromByteArray(pos));
        byte type = trie.readByte();
        if(type==ROOT){
            trie.read(hash, 0, 20);
        }else {
            byte keySize = trie.readByte();
            trie.seek(trie.getFilePointer()+keySize+1);
            trie.read(hash, 0, 20);
        }
        return hash;
    }

    private byte[] calcHash(byte type, byte[] key, byte[] childArray) throws IOException {
        byte[] hash;
        if(type==BRANCH || type==ROOT) {
            byte[] digest=key;
            ByteBuffer buffer = ByteBuffer.allocate(8);
            for(int i = 0; i < childArray.length+1;) {
                digest = Bytes.concat(digest, getHash(buffer.put(childArray, i, 8).array()));
                i = i + 9;
            }
            buffer.clear();
            hash = sha256hash160(digest);
        }else if(type==LEAF){
            byte[] digest=key;
            ByteBuffer buffer = ByteBuffer.allocate(2);
            for(int i = 0; i < childArray.length+1;) {
                digest = Bytes.concat(digest, getHash(buffer.put(childArray, i, 2).array()));
                i = i + 3;
            }
            buffer.clear();
            hash = sha256hash160(digest);
        }else{
            hash = null;
        }
        return hash;
    }

}