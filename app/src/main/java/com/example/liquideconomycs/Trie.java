package com.example.liquideconomycs;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.BitSet;

import static org.bitcoinj.core.Utils.sha256hash160;

public class Trie {
    private RandomAccessFile trie, freeSpace;
    private SQLiteDatabase db;
    private byte[] hashSize = new byte[20];
    private static byte[] rootPos = Longs.toByteArray(0L);
    private static byte ROOT = 1; //
    private static byte BRANCH = 2;
    private static byte LEAF = 3;
    private static byte FOUND = 0;
    private static byte NOTFOUND = 1;
    Trie(String nodeDir, SQLiteDatabase dataBase) throws FileNotFoundException {
        trie = new RandomAccessFile(nodeDir+ "/trie.dat", "rw");
        freeSpace = new RandomAccessFile(nodeDir+ "/freeSpace.dat", "rw");
        db = dataBase;
        ContentValues cv = new ContentValues();
    }

    //we need save 10 000 000 000+ account for all people
    // and we need have sync func like "all for all" between two people(personal accounts trie), who met by change

    //todo var 1:
    //ROOT(content child NODE)
    ///hash sha256hash160(20)/child point array(1-256*8)
    ///00*20                 /0000000000000000 total 2069 byte
    //BRANCH(content child BRANCH or LEAF)
    //type(1 byte)/key size(1 byte)/key(0-17 byte)/hash sha256hash160(20 byte)/childsMap(32 byte)  /childPointArray(1-256*8 byte)
    //00          /00              /00*17         /00*20                      /00*32               /00*8
    // max 2120 byte (min ‭153183(nodes) = ~309Mb)
    //
    //LEAF(content account age)
    //type(1 byte)/key size(1 byte)/key(0-18 byte)/hash sha256hash160(20 byte)/childsMap(32 byte)  /dataArray(1-256*2 byte)
    //00          /00               /00*18         /00*20                      /00*32               /00*2
    // max 585 byte (min 39062500(leafs) = ~21GB)
    //
    //total 22GB(ideal trie for 10 000 000 000 accounts)
    //


    //return pos or age or null if not found
    private byte[] search(byte[] key, long pos) throws IOException {
        //Если размер файла == 0  то вернем null
        if(trie.length()>0) {
            //если это корень то переместим курсор на позицию в массиве детей = первый байт ключа * 8 - 8
            //если содержимое != 0 то  начинаем искать там и вернем то что нашли + корень, иначе вернем корень
            if (pos == 0L) {
                trie.seek(20 + getKeyPos((key[0]&0xFF), BRANCH));
                byte[] childPos = new byte[8];
                trie.read(childPos, 0, 8);
                if (Longs.fromByteArray(childPos) != 0) {
                    return childPos;
                }
            } else {
                //иначе переместимся на позицию pos
                trie.seek(pos);
                //прочитаем ключ, карту дочерей, число дочерей, суфикс вносимого ключа
                byte type = trie.readByte();
                byte keyNodeSize = trie.readByte();
                byte[] keyNode = new byte[keyNodeSize];
                trie.read(keyNode, 0, keyNodeSize);
                trie.seek(trie.getFilePointer() + 20); //skip hash
                byte[] childsMap = new byte[32];
                trie.read(childsMap, 0, 32);
                //Получим префикс и суффикс искомого ключа
                if(key.length<=keyNodeSize){
                    Log.d("TRIE", String.valueOf(keyNodeSize));
                }
                byte[] preffixKey = getBytesPart(key, 0, keyNodeSize);
                byte[] suffixKey = getBytesPart(key, keyNodeSize, key.length - keyNodeSize);

                //found Если искомый ключ(его часть =  длинне ключа узла) = ключу узла и первый байт суффикса имеется в массиве дочерей то
                if(Arrays.equals(preffixKey, keyNode) && checkChild(childsMap, (suffixKey[0] & 0xFF))) {
                    int childPosInMap = 0;
                    byte[] result;
                    if(type==BRANCH) {//ret pos
                        childPosInMap = getChildPos(childsMap, (suffixKey[0] & 0xFF));
                        trie.seek(trie.getFilePointer() + getKeyPos(childPosInMap, type));
                        result = new byte[8];
                        trie.read(result, 0, 8);

                    }else {                  //ret age
                        childPosInMap = getChildPos(childsMap, (suffixKey[0] & 0xFF));
                        trie.seek(trie.getFilePointer() + getKeyPos(childPosInMap, type));
                        result = new byte[2];
                        trie.read(result, 0, 2);
                    }
                    return result;
                }
            }
        }
        return null;
    }

    public byte[] insert(byte[] key, byte[] age, long pos) throws IOException {

        byte[] hash;
        byte[] childsMap = new byte[32];
        byte[] typeAndKeySize = new byte[2];

        if (pos==0L) {
            if (trie.length() == 0) {
                trie.setLength(0);
                byte[] trieTmp = new byte[2068];
                trie.write(trieTmp);
                return insert(key, age, pos);
            }else{
                byte[] sPos=search(key, pos);
                byte[] lKey;

                if (sPos == null){//Create new LEAF witch age
                    lKey = getBytesPart(key, 1, key.length - 2);
                    typeAndKeySize = new byte[2];
                    typeAndKeySize[0] = LEAF;
                    typeAndKeySize[1] = (byte)lKey.length;
                    childsMap = addChildInMap(new byte[32], (key[key.length-1]&0xFF));//add age
                    hash=calcHash(typeAndKeySize[0], lKey, childsMap);
                    pos = addRecord(typeAndKeySize, lKey, hash, childsMap, age);
                }else{//insert in child & save in root
                    lKey = getBytesPart(key, 1, key.length - 1);
                    pos = Longs.fromByteArray(insert(lKey, age, Longs.fromByteArray(sPos)));
                }
                //save in root
                trie.seek(20+getKeyPos((key[0]&0xFF), BRANCH));
                trie.write(Longs.toByteArray(pos));
                trie.seek(20);
                byte[] childArray= new byte[2048];
                trie.read(childArray,0,2048);
                hash=calcHash(ROOT, new byte[1], childArray);
                trie.seek(0);
                trie.write(hash);
                return hash;
            }
        }else {
            byte[] sResult = search(key, pos);
            if (sResult != null) {
                if (sResult.length == 8) {//find child node
                    trie.seek(pos);
                    byte type = trie.readByte();
                    byte keyNodeSize = trie.readByte();
                    byte[] keyNode = new byte[keyNodeSize];
                    trie.read(keyNode, 0, keyNodeSize);
                    trie.seek(trie.getFilePointer() + 20); //skip hash
                    trie.read(childsMap, 0, 32);
                    //Получим суффикс
                    byte[] suffixKey = getBytesPart(key, keyNodeSize, key.length - keyNodeSize);

                    int selfChildsCount = getChildsCount(childsMap);
                    int selfChildArraySize = selfChildsCount * 8;

                    int childPosInMap = getChildPos(childsMap, (suffixKey[0] & 0xFF));
                    trie.seek(pos + 2 + keyNodeSize + 20 + 32 + getKeyPos(childPosInMap, type));
                    //insert to child
                    trie.write(insert(getBytesPart(suffixKey, 1, suffixKey.length-1), age, Longs.fromByteArray(sResult)));

                    trie.seek(pos + 2 + keyNodeSize + 20 + 32);
                    byte[] childArray = new byte[selfChildArraySize];
                    trie.read(childArray, 0, selfChildArraySize);
                    hash = calcHash(BRANCH, keyNode, childArray);
                    trie.seek(pos + 2 + keyNodeSize);
                    trie.write(hash);
                    return Longs.toByteArray(pos);

                } else {
                    return Longs.toByteArray(pos);//find age
                }
            } else {

                trie.seek(pos);
                //прочитаем ключ, карту детей, число детей, суфикс префикс
                byte type = trie.readByte();
                byte keyNodeSize = trie.readByte();

                byte[] keyNode = new byte[keyNodeSize];
                trie.read(keyNode, 0, keyNodeSize);

                trie.seek(trie.getFilePointer() + 20); //skip hash
                trie.read(childsMap, 0, 32);
                //Получим префикс и суффикс искомого ключа
                byte[] preffixKey = getBytesPart(key, 0, keyNodeSize);
                //todo: we need suffix or common key?
                byte[] suffixKey = getBytesPart(key, keyNodeSize, key.length - keyNodeSize);
                byte[] commonKey = getCommonKey(keyNode, preffixKey);

                int selfChildsCount = getChildsCount(childsMap);
                int selfChildArraySize = selfChildsCount*(type==LEAF ? 2 : 8);

                byte[] selfChildArray= new byte[selfChildArraySize];
                trie.seek(pos+2+keyNodeSize+20+32);//go to childArray
                trie.read(selfChildArray,0,selfChildArraySize);

                if(!Arrays.equals(preffixKey, keyNode)){//create sub node
                    //todo найдем свободное пространство и создадим новый лист
                    //выделим буфер размером длинна вносимого ключа - длинна общего ключа - 1 байт(для дочери)
                    byte[] leafKey = getBytesPart(key, commonKey.length, keyNode.length - commonKey.length);

                    typeAndKeySize[0] = LEAF;
                    typeAndKeySize[1] = (byte)leafKey.length;
                    byte[] childsMapNew=addChildInMap(new byte[32], (key[key.length-1]&0xFF));
                    hash=calcHash(typeAndKeySize[0], leafKey, childsMapNew);
                    childsMap = addChildInMap(new byte[32], (key[key.length-1]&0xFF));//add age
                    hash=calcHash(typeAndKeySize[0], leafKey, childsMap);

                    long posLeaf = addRecord(typeAndKeySize, leafKey, hash, childsMapNew, age);

                    //todo найдем свободное пространство
                    // создадим новый лист из текущего
                    // он будет содержать массив дочерей текущего листа и иметь суффикс от ключа  текущего листа за минусом общего

                    byte[] oldLeafKey = getBytesPart(keyNode, commonKey.length, keyNode.length - commonKey.length);
                    typeAndKeySize[0] = type;
                    typeAndKeySize[1] = (byte)oldLeafKey.length;
                    hash=calcHash(typeAndKeySize[0], oldLeafKey, (type==LEAF ? childsMap : selfChildArray));

                    long posOldLeaf = addRecord(typeAndKeySize, oldLeafKey, hash, childsMap, selfChildArray);

                    //todo найдем свободное пространство
                    // создадим новую ветку для обоих листов
                    // она будет содержать массив дочерей состоящий из созданных ранее листов и иметь ключ равный общему префиксу для обоих дочерей
                    typeAndKeySize[0] = BRANCH;
                    typeAndKeySize[1] = (byte)commonKey.length;
                    byte [] childArray;
                    if (leafKey[0]>oldLeafKey[0]){
                        childArray = Bytes.concat(Longs.toByteArray(posOldLeaf), Longs.toByteArray(posLeaf));
                    }else{
                        childArray = Bytes.concat(Longs.toByteArray(posLeaf), Longs.toByteArray(posOldLeaf));
                    }
                    // пересчитываем хеш и рекурсивно вносим позицию в вышестоящие узлы
                    hash=calcHash(typeAndKeySize[0], commonKey, childArray);
                    return Longs.toByteArray(addRecord(typeAndKeySize, commonKey, hash, addChildInMap(addChildInMap(new byte[32], (leafKey[0]&0xFF)), (oldLeafKey[0]&0xFF)), childArray));

                }else{//if isLeaf add age in node, else create leaf witch suffix key and add pos in node(branch)
                    trie.seek(trie.length());
                    long posLeaf=trie.getFilePointer();
                    if(type!=LEAF){
                        //todo найдем свободное пространство и создадим новый лист
                        //выделим буфер размером длинна вносимого ключа - длинна общего ключа - 1 байт(для дочери)
                        byte[] leafKey = getBytesPart(key, keyNode.length, (key.length-1) - keyNode.length);
                        typeAndKeySize[0] = LEAF;
                        typeAndKeySize[1] = (byte)leafKey.length;

                        byte[] childsMapNew=addChildInMap(new byte[32], (key[key.length-1]&0xFF));
                        hash=calcHash(typeAndKeySize[0], leafKey, childsMapNew);

                        posLeaf = addRecord(typeAndKeySize, leafKey, hash, childsMapNew, age);
                    }

                    //todo найдем свободное пространств
                    typeAndKeySize[0] = type;
                    typeAndKeySize[1] = (byte)keyNode.length;

                    childsMap = addChildInMap(childsMap, (suffixKey[0]&0xFF));
                    int chp=getChildPos(childsMap, (suffixKey[0]&0xFF));
                    byte[] before=getBytesPart(selfChildArray,0, (chp-1)*(type==LEAF ? 2 : 8));
                    byte [] childArray = Bytes.concat(before, (type==LEAF ? age : Longs.toByteArray(posLeaf)), getBytesPart(selfChildArray, before.length, selfChildArray.length-before.length));
                    // пересчитываем хеш и рекурсивно вносим позицию в вышестоящие узлы
                    hash=calcHash((type==LEAF ? LEAF : BRANCH), keyNode, (type==LEAF ? childsMap : childArray));

                    return Longs.toByteArray(addRecord(typeAndKeySize, keyNode, hash, childsMap, childArray));

                }
            }
        }
    }

    //Извлекает общий для selfKey и key префикс
    private byte[] getCommonKey(byte[] selfKey, byte[] key) {
        for(int i = 1; i < selfKey.length+1; i++){
            byte[] sK = getBytesPart(key, 0, selfKey.length-i);
            if(Arrays.equals(sK, getBytesPart(selfKey, 0, selfKey.length-i))){
                return sK;
            }
        }
        return null;
    }

    private int getKeyPos(int key, byte type){
        if(type==BRANCH){
            return (key==0?0:(key * 8) - 8);
        }else{
            return (key==0?0:(key * 2) - 2);
        }
    }

    private long addRecord(byte[] typeAndKeySize, byte[] key, byte[] hash, byte[] childsMap, byte[] childArray) throws IOException {
        byte[] record = Bytes.concat(typeAndKeySize, key, hash, childsMap, childArray);
        Cursor query = db.rawQuery("SELECT * FROM freeSpace where space="+record.length, null);
        long pos;
        if (query.moveToFirst()) {
            pos = query.getLong(1);
            db.delete("freeSpace",  "pos = ?", new String[] { String.valueOf(pos) });
            query.close();
            trie.seek(pos);
        }else{
            trie.seek(trie.length());
            pos = trie.getFilePointer();
        }
        trie.write(record);
        return pos;
    }

    private int getChildPos(byte[]childsMap, int key){
        int result=1;
        BitSet prepare = BitSet.valueOf(childsMap);
        for(int i = 0; i < key;i++){
            if(prepare.get(i)) result=result+1;
        }
        return result;
    }

    private int getChildsCount(byte[]childsMap){
        return BitSet.valueOf(childsMap).cardinality();
    }

    private boolean checkChild(byte[]childsMap, int key){
        BitSet prepare = BitSet.valueOf(childsMap);
        return prepare.get(key);
    }

    private byte[] addChildInMap(byte[]childsMap, int key){
        //byte[] result;
        ByteBuffer result = ByteBuffer.allocate(32);
        //byte[] oldLeafKey= boL.put(selfKey, commonKey.length, selfKey.length).array();
        boolean end = false;
        for(int i=0; i < 32; i++){
            if(key>(i*8)-1 && key<(i*8)+9){
                result.put(childsMap,0,i);//start
                //
                byte[] p = new byte[1];
                p[0]=childsMap[i];
                BitSet prepare1 = BitSet.valueOf(p);
                for(int b=0;b<8;b++) {
                    if ((i * 8) + b == key) {
                        prepare1.set(b);
                        break;
                    }
                }
                for(int c=0;c<256;c++){
                    p[0]=(byte)c;
                    BitSet prepare2 = BitSet.valueOf(p);
                    if(prepare1.equals(prepare2)){
                        result.put(p);
                        result.put(childsMap,result.position(),32-(i+1));//end
                        end=true;
                        break;
                    }
                }
                //
            }
            if(end){
                break;
            }
        }
        return result.array();
    }

    private byte[] getBytesPart(byte[]src, int off, int len){
        byte[] result= new byte[len];
        for(int i=0; i < result.length; i++){
            result[i]=src[off+i];
        }
        return result;
    }

    public byte[] getHash(long pos) throws IOException {

        byte[] hash = new byte[20];
        try{
            trie.seek(pos);
        }catch (Exception e) {
            Log.d("TRIE", String.valueOf(pos));
        }
        if (pos == 0L) {
            trie.read(hash, 0, 20);
        } else {
            trie.seek(pos+1);
            byte keySize = trie.readByte();
            trie.seek(trie.getFilePointer() + keySize);
            trie.read(hash, 0, 20);
        }
        return hash;

    }

    private byte[] calcHash(byte type, byte[] key, byte[] childArray) throws IOException {
        byte[] hash;
        if(type==BRANCH || type==ROOT) {
            byte[] digest=key;
            for(int i = 0; i < childArray.length;) {
                long pos = Longs.fromByteArray(getBytesPart(childArray, i, 8));
                if(pos!=0){
                    digest = Bytes.concat(digest, getHash(pos));
                }
                i = i + 8;
            }
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