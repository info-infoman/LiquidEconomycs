package com.example.liquideconomycs;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
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
    private static byte FOUND = 0;
    private static byte NOTFOUND = 1;
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
        byte[] s = search(key, rootPos);
        if
        ByteArrayInputStream fined = new ByteArrayInputStream();
        long pos = recursiveInsert(key, age, fined, 0);
        fined.close();
        return getHash(pos);
    }

    private long recursiveInsert(byte[] key, byte[] age, ByteArrayInputStream fined, long pos) throws IOException {
        byte[] hash;
        BitSet childsMap = new BitSet(256);
        if(fined.available()>0){
            byte[] type=new byte[1];
            fined.read(type,0,1);
            byte[] found=new byte[1];
            fined.read(found,0,1);
            if(type[0]==ROOT){
                if(pos==0){
                    //todo find free space
                    trie.seek(trie.length()+1);
                    pos=trie.getFilePointer();
                    //
                    trie.write(LEAF);
                    ByteBuffer buffer = ByteBuffer.allocate(19);
                    byte[] lKey= buffer.put(key, 0, key.length-1).array();
                    trie.writeByte((byte)19);
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
                return pos;

            }else{
                byte[] selfPos= new byte[8];
                fined.read(selfPos,0,8);
                byte[] selfKeySize= new byte[1];
                fined.read(selfKeySize,0,1);
                byte[] selfKey= new byte[selfKeySize[0]];
                fined.read(selfKey,0,selfKeySize[0]);
                byte[] selfChildMap= new byte[32];
                fined.read(selfChildMap,0,32);
                byte[] selfChildsCount = new byte[4];
                fined.read(selfChildsCount,0,4);
                byte[] selfPartKeySize = new byte[1];
                fined.read(selfPartKeySize,0,1);
                byte[] selfPartKey = new byte[selfPartKeySize[0]];
                fined.read(selfPartKey,0,selfPartKeySize[0]);

                boolean sizeChange = false;

                if(type[0]==BRANCH){
                    //load ChildArray
                    int selfChildArraySize = Ints.fromByteArray(selfChildsCount)*8;
                    byte[] selfChildArray= new byte[selfChildArraySize];
                    trie.seek(Longs.fromByteArray(selfPos)+2+selfKeySize[0]+20+33);//go to childArray
                    trie.read(selfChildArray,0,selfChildArraySize);

                    if(pos==0){//if not found
                        //todo get commonKey for leaf key & end of parent key
                        byte[] commonKey = getCommonKey(selfKey, selfPartKey);

                        //todo find free space
                        trie.seek(trie.length()+1);
                        long posLeaf=trie.getFilePointer();
                        trie.write(LEAF);
                        ByteBuffer bL = ByteBuffer.allocate((selfPartKey.length - commonKey.length));
                        byte[] leafKey= bL.put(selfPartKey, commonKey.length, selfPartKey.length).array();
                        trie.writeByte(leafKey.length);
                        trie.write(leafKey);
                        hash=calcHash(LEAF, leafKey, age);
                        trie.write(hash);
                        childsMap.set(selfPartKey[selfPartKey.length-1], true);
                        trie.write(childsMap.toByteArray());
                        trie.write(age);

                        //todo create new branch from parent node(content selfChildArray from parent node and corp papent key)
                        //todo find free space
                        trie.seek(trie.length()+1);
                        long posBranch=trie.getFilePointer();
                        trie.write(BRANCH);
                        ByteBuffer bB = ByteBuffer.allocate((selfKey.length - commonKey.length));
                        byte[] branchKey= bB.put(selfKey, commonKey.length, selfKey.length).array();
                        trie.writeByte(branchKey.length);
                        trie.write(branchKey);
                        hash=calcHash(BRANCH, branchKey, selfChildArray);
                        trie.write(hash);
                        trie.write(selfChildMap);
                        trie.write(selfChildArray);

                        //rebuild old Branch
                        //todo clear old self pos && find free space
                        trie.seek(trie.length()+1);
                        pos=trie.getFilePointer();

                        trie.write(BRANCH);
                        trie.writeByte(commonKey.length);
                        trie.write(commonKey);
                        childsMap = new BitSet(256);//get clear child map
                        childsMap.set(leafKey[0], true);
                        childsMap.set(branchKey[0], true);
                        byte [] childArray;
                        if (leafKey[0]>branchKey[0]){
                            childArray = Bytes.concat(Longs.toByteArray(posBranch), Longs.toByteArray(posLeaf));
                        }else{
                            childArray = Bytes.concat(Longs.toByteArray(posLeaf), Longs.toByteArray(posBranch));
                        }
                        hash=calcHash(BRANCH, commonKey, childArray);
                        trie.write(hash);
                        trie.write(childsMap.toByteArray());
                        trie.write(childArray);
                        return recursiveInsert(key, age, fined, pos);
                    }else{
                        int childPosInMap = getChildPos(selfChildMap, Ints.fromBytes((byte) 0, (byte) 0, (byte) 0, key[0]));
                        trie.seek(Longs.fromByteArray(selfPos)+2+selfKeySize[0]+20+32+(childPosInMap*8)-8);
                        trie.write(Longs.toByteArray(pos));
                        trie.seek(Longs.fromByteArray(selfPos)+2+selfKeySize[0]+20+33);
                        trie.read(selfChildArray,0,selfChildArraySize);
                        hash=calcHash(BRANCH, selfKey, selfChildArray);
                        trie.seek(Longs.fromByteArray(selfPos)+2+selfKeySize[0]+1);
                        trie.write(hash);
                        return recursiveInsert(key, age, fined, Longs.fromByteArray(selfPos));
                    }
                }else{
                    //load ChildArray
                    int selfChildArraySize = Ints.fromByteArray(selfChildsCount)*2;
                    byte[] selfChildArray= new byte[selfChildArraySize];
                    trie.seek(Longs.fromByteArray(selfPos)+2+selfKeySize[0]+20+33);//go to childArray
                    trie.read(selfChildArray,0,selfChildArraySize);

                    if(selfPartKey!=selfKey){
                        byte[] commonKey = getCommonKey(selfKey, selfPartKey);
                        //todo find free space
                        trie.seek(trie.length()+1);
                        long posLeaf=trie.getFilePointer();
                        trie.write(LEAF);
                        ByteBuffer bL = ByteBuffer.allocate((selfPartKey.length - commonKey.length));
                        byte[] leafKey= bL.put(selfPartKey, commonKey.length, selfPartKey.length).array();
                        trie.writeByte(leafKey.length);
                        trie.write(leafKey);
                        hash=calcHash(LEAF, leafKey, age);
                        trie.write(hash);
                        childsMap.set(selfPartKey[selfPartKey.length-1], true);
                        trie.write(childsMap.toByteArray());
                        trie.write(age);

                        //todo create new leaf from parent node(content selfChildArray from parent node and corp papent key)
                        //todo find free space
                        trie.seek(trie.length()+1);
                        long posOldLeaf=trie.getFilePointer();
                        trie.write(LEAF);
                        ByteBuffer boL = ByteBuffer.allocate((selfKey.length - commonKey.length));
                        byte[] oldLeafKey= boL.put(selfKey, commonKey.length, selfKey.length).array();
                        trie.writeByte(oldLeafKey.length);
                        trie.write(oldLeafKey);
                        hash=calcHash(LEAF, oldLeafKey, selfChildArray);
                        trie.write(hash);
                        trie.write(selfChildMap);
                        trie.write(selfChildArray);

                        //rebuild old Leaf to branch
                        //todo clear old self pos && find free space
                        trie.seek(trie.length()+1);
                        pos=trie.getFilePointer();

                        trie.write(BRANCH);
                        trie.writeByte(commonKey.length);
                        trie.write(commonKey);
                        childsMap = new BitSet(256);//get clear child map
                        childsMap.set(leafKey[0], true);
                        childsMap.set(oldLeafKey[0], true);
                        byte [] childArray;
                        if (leafKey[0]>oldLeafKey[0]){
                            childArray = Bytes.concat(Longs.toByteArray(posOldLeaf), Longs.toByteArray(posLeaf));
                        }else{
                            childArray = Bytes.concat(Longs.toByteArray(posLeaf), Longs.toByteArray(posOldLeaf));
                        }
                        hash=calcHash(BRANCH, commonKey, childArray);
                        trie.write(hash);
                        trie.write(childsMap.toByteArray());
                        trie.write(childArray);
                        return recursiveInsert(key, age, fined, pos);
                    }else{
                        if(found[0] == FOUND) {
                            //todo insert suffix to childArray
                            int childPosInMap = getChildPos(selfChildMap, Ints.fromBytes((byte) 0, (byte) 0, (byte) 0, key[0]));
                            trie.seek(Longs.fromByteArray(selfPos) + 2 + selfKeySize[0] + 20 + 32 + (childPosInMap * 2) - 2);
                            trie.write(Longs.toByteArray(pos));
                            trie.seek(Longs.fromByteArray(selfPos) + 2 + selfKeySize[0] + 20 + 33);
                            trie.read(selfChildArray, 0, selfChildArraySize);
                            hash = calcHash(BRANCH, selfKey, selfChildArray);
                            trie.seek(Longs.fromByteArray(selfPos) + 2 + selfKeySize[0] + 1);
                            trie.write(hash);
                            return recursiveInsert(key, age, fined, Longs.fromByteArray(selfPos));
                        }else{
                            int size=0;
                            for(int i = 0; i<key[key.length-1];i++){
                                if(checkChild(selfChildMap, Ints.fromBytes((byte) 0, (byte) 0, (byte) 0, key[i]))){
                                    size=size+1;
                                }
                            }

                            byte[] childArray_1= new byte[size*2];
                            trie.seek(Longs.fromByteArray(selfPos) + 2 + selfKeySize[0] + 20 + 33);
                            trie.read(childArray_1, 0, size*2);


                            size=0;
                            for(int i = key[key.length]; i<257;i++){
                                if(checkChild(selfChildMap, Ints.fromBytes((byte) 0, (byte) 0, (byte) 0, key[i]))){
                                    size=size+1;
                                }
                            }

                            byte[] childArray_3= new byte[size*2];
                            trie.seek(Longs.fromByteArray(selfPos) + 2 + selfKeySize[0] + 20 + 32 + (childArray_1.length+1));
                            trie.read(childArray_3, 0, size*2);

                            //todo find free space
                            trie.seek(trie.length()+1);
                            long posLeaf=trie.getFilePointer();
                            trie.write(LEAF);
                            trie.writeByte(selfKeySize[0]);
                            trie.write(selfKey);
                            trie.write(new byte[20]);
                            childsMap = new BitSet(256);//get clear child map
                            childsMap.set(key[key.length-1], true);
                            trie.write(childsMap.toByteArray());
                            trie.write(childArray_1);
                            trie.write(age);
                            trie.write(childArray_3);

                            trie.seek(posLeaf + 2 + selfKeySize[0]);
                            hash=calcHash(LEAF, selfKey, Bytes.concat(childArray_1, age, childArray_3));
                            trie.write(hash);
                            return recursiveInsert(key, age, fined, posLeaf);
                        }
                    }
                }
            }
        }else{
            trie.setLength(0);
            trie.write(ROOT);
            byte[] trieTmp =new byte[2069];
            trie.write(trieTmp);
            trie.write(LEAF);
            ByteBuffer buffer = ByteBuffer.allocate(19);
            byte[] lKey= buffer.put(key, 0, 19).array();
            trie.writeByte(lKey.length);
            trie.write(lKey);
            byte[] tmpL=new byte[52];
            trie.write(tmpL);
            trie.seek(22+(key[0]*8));
            trie.write(Longs.toByteArray(2070));
            fined = new ByteArrayInputStream(search(key, rootPos));
            return recursiveInsert(key, age, fined, 0);
        }

    }

    private byte[] getCommonKey(byte[] selfKey, byte[] key) {
        for(int i = 1; i < selfKey.length; i++){
            ByteBuffer sK = ByteBuffer.allocate(selfKey.length-i);
            ByteBuffer k = ByteBuffer.allocate((key.length-i));
            sK.put(selfKey, 0, selfKey.length-i);
            k.put(key, 0, (key.length-i));
            if(sK.equals(k)){
                return sK.array();
            }
        }
        return null;
    }

    //
    public byte[] search(byte[] key, byte[] pos) throws IOException {
        if(trie.length()>0) {
            trie.seek(Longs.fromByteArray(pos));
            byte type = key.length == 20 ? ROOT : trie.readByte();
            if (type == ROOT) {
                trie.seek(trie.getFilePointer() + 21 + (key[0] * 8));
                byte[] childPos = new byte[8];
                trie.read(childPos, 0, 8);
                if (Longs.fromByteArray(childPos) != 0) {
                    byte[] child = search(key, childPos);
                    ByteBuffer rtBuffer = ByteBuffer.allocate(child.length + 1);
                    return rtBuffer.put(child).put(ROOT).array();
                }
                ByteBuffer rtBuffer = ByteBuffer.allocate(1);
                return rtBuffer.put(ROOT).array();
            } else {
                byte keySize = trie.readByte();
                byte[] keyNode = new byte[keySize];
                trie.read(keyNode, 0, keySize);
                //todo compare keyNode vs key
                trie.seek(trie.getFilePointer() + 21); //skip hash
                byte[] childsMap = new byte[32];
                trie.read(childsMap, 0, 32);
                int childsCount = getChildsCount(childsMap);

                ByteBuffer compareKey = ByteBuffer.allocate(keyNode.length);
                compareKey.put(key, 0, keyNode.length).array();

                ByteBuffer sKey = ByteBuffer.allocate(key.length - keyNode.length);
                byte[] suffixKey = sKey.put(key, keyNode.length, key.length).array();
                //found
                if (compareKey.put(key, 0, keyNode.length).array() == keyNode && checkChild(childsMap, Ints.fromBytes((byte) 0, (byte) 0, (byte) 0, suffixKey[0]))) {
                    int childPosInMap = getChildPos(childsMap, Ints.fromBytes((byte) 0, (byte) 0, (byte) 0, suffixKey[0]));

                    if (type == BRANCH) {
                        trie.seek(trie.getFilePointer() + (childPosInMap * 8) - 8);
                        byte[] childPos = new byte[8];
                        trie.read(childPos, 0, 8);
                        byte[] child = search(suffixKey, childPos);
                        ByteBuffer rtBuffer = ByteBuffer.allocate(child.length + 10 + keySize + 32 + 4 + 5);
                        //    child/ type/found/self pos        /keySize/keyNode/childsMap                                                       /childsCount
                        //ret ...00/00   /00   /0000000000000000/00     /00n    /0000000000000000000000000000000000000000000000000000000000000000/00000000
                        return rtBuffer.put(child).put(BRANCH).put(FOUND).put(pos).put(keySize).put(keyNode).put(childsMap).put(Ints.toByteArray(childsCount)).array();
                    } else {
                        trie.seek(trie.getFilePointer() + (childPosInMap * 2) - 2);
                        byte[] age = new byte[2];
                        trie.read(age, 0, 2);
                        ByteBuffer rtBuffer = ByteBuffer.allocate(10 + keySize + 32 + 4 + 5 + 2);
                        //    / type/found/self pos        /keySize/keyNode/childsMap                                                       /childsCount/age  /key.length/key
                        //ret /00   /00   /0000000000000000/00     /00n    /0000000000000000000000000000000000000000000000000000000000000000/00000000   /0000/00         /00...
                        return rtBuffer.put(LEAF).put(FOUND).put(pos).put(keySize).put(keyNode).put(childsMap).put(Ints.toByteArray(childsCount)).put(age).put((byte) key.length).put(key).array();
                    }
                }

                ByteBuffer rtBuffer = ByteBuffer.allocate(10 + keySize + 32 + 4 + 5);
                //    /type/not found/self pos        /keySize/keyNode/childsMap                                                       /childsCount/key.length/key
                //ret /00  /00       /0000000000000000/00     /00n    /0000000000000000000000000000000000000000000000000000000000000000/00000000   /00        /00...
                return rtBuffer.put(type).put(NOTFOUND).put(pos).put(keySize).put(keyNode).put(childsMap).put(Ints.toByteArray(childsCount)).put((byte) key.length).put(key).array();

            }
        }else{
            return null;
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

    public byte[] getHash(long pos) throws IOException {
        byte[] hash = new byte[20];
        trie.seek(pos);
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
                digest = Bytes.concat(digest, getHash(Longs.fromByteArray(buffer.put(childArray, i, 8).array())));
                i = i + 9;
            }
            buffer.clear();
            hash = sha256hash160(digest);
        }else if(type==LEAF){
            byte[] digest=Bytes.concat(key, childArray);
            hash = sha256hash160(digest);
        }else{
            hash = null;
        }
        return hash;
    }

}