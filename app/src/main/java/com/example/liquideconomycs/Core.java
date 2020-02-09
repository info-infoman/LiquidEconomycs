package com.example.liquideconomycs;

import android.app.IntentService;
import android.app.Notification;
import android.content.ContentValues;
import android.content.Intent;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;
import com.google.common.primitives.Shorts;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

import org.apache.http.message.BasicNameValuePair;
import org.bitcoinj.core.ECKey;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import static org.bitcoinj.core.Utils.sha256hash160;

public class Core extends IntentService {

    RandomAccessFile trie;
    SQLiteDatabase db;
    DBHelper dbHelper;
    ContentValues cv;
    private static byte ROOT = 1; //
    private static byte BRANCH = 2;
    private static byte LEAF = 3;

    public static final String EXTRA_MASTER = "com.example.liquideconomycs.Core.extra.MASTER";
    public static final String EXTRA_CMD = "com.example.liquideconomycs.Core.extra.CMD";

    //input fnc
    private static final String ACTION_GetHash = "com.example.liquideconomycs.Core.action.GetHash";
    private static final String ACTION_Insert = "com.example.liquideconomycs.Core.action.Insert";
    private static final String ACTION_Delete = "com.example.liquideconomycs.Core.action.Delete";
    private static final String ACTION_Find = "com.example.liquideconomycs.Core.action.Find";
    private static final String ACTION_Sync = "com.example.liquideconomycs.Core.action.Sync";

    private static final String ACTION_Test = "com.example.liquideconomycs.Core.action.Test";

    //input params
    private static final String EXTRA_POS = "com.example.liquideconomycs.Core.extra.POS";
    private static final String EXTRA_KEY = "com.example.liquideconomycs.Core.extra.KEY";
    private static final String EXTRA_VALUE = "com.example.liquideconomycs.Core.extra.VALUE";
    private static final String EXTRA_SIGNAL_SERVER = "com.example.liquideconomycs.Core.extra.SIGNAL_SERVER";

    public static final String BROADCAST_ACTION_ANSWER = "com.example.liquideconomycs.Core.broadcast_action.ANSWER";
    public static final String EXTRA_ANSWER = "com.example.liquideconomycs.Core.extra.ANSWER";


    public static void startActionTest(Context context, String master){
        Intent intent = new Intent(context, Core.class);
        intent.setAction(ACTION_Test);
        intent.putExtra(EXTRA_MASTER, master);
        context.startService(intent);
    }

    // called by activity to communicate to service
    public static void startActionGetHash(Context context, String master, long pos) {
        Intent intent = new Intent(context, Core.class);
        intent.setAction(ACTION_GetHash);
        intent.putExtra(EXTRA_MASTER, master);
        intent.putExtra(EXTRA_POS, pos);
        context.startService(intent);
    }

    // called by activity to communicate to service
    public static void startActionInsert(Context context, String master, byte[] key, byte[] value) {
        Intent intent = new Intent(context, Core.class);
        intent.setAction(ACTION_Insert);
        intent.putExtra(EXTRA_MASTER, master);
        intent.putExtra(EXTRA_KEY, key);
        intent.putExtra(EXTRA_VALUE, value);
        context.startService(intent);
    }

    public static void startActionSync(Context context, String signalServer, byte[] Key) {
        Intent intent = new Intent(context, Core.class);
        intent.setAction(ACTION_Sync);
        intent.putExtra(EXTRA_SIGNAL_SERVER, signalServer);
        intent.putExtra(EXTRA_KEY, Key);
        context.startService(intent);
    }

    public Core() throws FileNotFoundException {
        super("Core");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Context context = getApplicationContext();
        dbHelper        = new DBHelper(context);
        cv              = new ContentValues();
        db              = dbHelper.getWritableDatabase();
        try {
            trie = new RandomAccessFile(context.getFilesDir().getAbsolutePath()+ "/trie"+"/trie.dat", "rw");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        Log.i("Trie", "Service: Trie is create");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_Test.equals(action)) {
                final String master = intent.getStringExtra(EXTRA_MASTER);
                final String cmd = "Test";
                ////////////////////////////////////////////////////////////////
                //mTrie = new Trie(nodeDir);

                Notification.Builder builder = new Notification.Builder(getBaseContext())
                        .setTicker("Test") // use something from something from R.string
                        .setContentTitle("title") // use something from something from
                        .setContentText("text") // use something from something from
                        .setProgress(0, 0, true); // display indeterminate progress

                startForeground(9999, builder.build());

                for(int i=0;i<10512;i++){
                    ECKey myECKey=new ECKey();
                    byte[] myPrivKey = myECKey.getPrivKeyBytes();
                    byte[] myPubKey = myECKey.getPubKeyHash();

                    short age = 2;
                    try {
                        insert(myPubKey, Shorts.toByteArray(age), 0L);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                stopForeground(true);

                broadcastActionMsg(master, cmd, new byte[0]);
                ////////////////////////////////////////////////////////////////
            }

            if (ACTION_GetHash.equals(action)) {
                final String master = intent.getStringExtra(EXTRA_MASTER);
                final String cmd = "GetHash";
                final long pos = intent.getLongExtra(EXTRA_POS,0L);
                ////////////////////////////////////////////////////////////////
                try {
                    //mTrie = new Trie(nodeDir);
                    broadcastActionMsg(master, cmd, getHash(pos));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                ////////////////////////////////////////////////////////////////
            }

            if (ACTION_Insert.equals(action)) {
                final String master = intent.getStringExtra(EXTRA_MASTER);
                final String cmd = "Insert";
                final byte[] key = intent.getByteArrayExtra(EXTRA_KEY);
                final byte[] value = intent.getByteArrayExtra(EXTRA_VALUE);
                ////////////////////////////////////////////////////////////////
                try {
                    //mTrie = new Trie(nodeDir);
                    broadcastActionMsg(master, cmd, insert(key, value, 0L));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                ////////////////////////////////////////////////////////////////
            }

            if (ACTION_Delete.equals(action)) {
                final String master = intent.getStringExtra(EXTRA_MASTER);
                final String cmd = "Delete";
                final byte[] key = intent.getByteArrayExtra(EXTRA_KEY);
                ////////////////////////////////////////////////////////////////
                try {
                    //mTrie = new Trie(nodeDir);
                    broadcastActionMsg(master, cmd, delete(key, 0L));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                ////////////////////////////////////////////////////////////////
            }

            if (ACTION_Find.equals(action)) {
                final String master = intent.getStringExtra(EXTRA_MASTER);
                final String cmd = "Find";
                final byte[] key = intent.getByteArrayExtra(EXTRA_KEY);
                ////////////////////////////////////////////////////////////////
                try {
                    //mTrie = new Trie(nodeDir);
                    broadcastActionMsg(master, cmd, find(key, 0L));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                ////////////////////////////////////////////////////////////////
            }

            if (ACTION_Sync.equals(action)) {
                final String signalServer = intent.getStringExtra(EXTRA_SIGNAL_SERVER);
                final byte[] key = intent.getByteArrayExtra(EXTRA_KEY);
                final String master = intent.getStringExtra(EXTRA_MASTER);
                final String cmd = "Sync";
                ////////////////////////////////////////////////////////////////
                Notification.Builder builder = new Notification.Builder(getBaseContext())
                        .setTicker("Sync") // use something from something from R.string
                        .setContentTitle("liquid economycs") // use something from something from
                        .setContentText("Sync liquid base") // use something from something from
                        .setProgress(0, 0, true); // display indeterminate progress

                startForeground(9991, builder.build());

                //todo sync processor

                stopForeground(true);

                broadcastActionMsg(master, cmd, new byte[0]);
                ////////////////////////////////////////////////////////////////
            }
        }


    }

    // called to send data to Activity
    public void broadcastActionMsg(String master, String cmd, byte[] answer) {
        Intent intent = new Intent(BROADCAST_ACTION_ANSWER);
        intent.putExtra(EXTRA_MASTER, master);
        intent.putExtra(EXTRA_CMD, cmd);
        intent.putExtra(EXTRA_ANSWER, answer);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(this);
        bm.sendBroadcast(intent);
    }

    /////////////////TRIE//////////////////////////////////////////////////////////////////////////////
    private byte[] find(byte[] key, long pos) throws IOException {
        byte[] s=search(key, pos);

        if (s.length==8){
            return find(getBytesPart(key, 1, key.length - 1), pos);
        }else if(s.length==2){
            return s;
        }else{
            return null;
        }
    }

    //return null if not change(not found) or pos in file if change or hash if root
    private byte[] delete(byte[] key, long pos) throws IOException {
        byte[] hash;
        byte[] childsMap = new byte[32];
        byte[] typeAndKeySize = new byte[2];
        byte[] s=search(key, pos);
        if (s != null) {
            if(pos==0L) {
                byte[] dKey = getBytesPart(key, 1, key.length - 1);
                byte[] posBytes = delete(dKey, Longs.fromByteArray(s));
                if (posBytes != null) {
                    trie.seek(20 + getChildPosInArray((key[0] & 0xFF), BRANCH));
                    trie.write(posBytes);
                    trie.seek(20);
                    byte[] childArray = new byte[2048];
                    trie.read(childArray, 0, 2048);
                    hash = calcHash(ROOT, childArray);
                    trie.seek(0);
                    trie.write(hash);
                    return hash;
                }
            }else{
                trie.seek(pos);
                byte type = trie.readByte();
                byte keyNodeSize = trie.readByte();

                typeAndKeySize[0] = type;
                typeAndKeySize[1] = keyNodeSize;

                byte[] keyNode = new byte[keyNodeSize];
                trie.read(keyNode, 0, keyNodeSize);
                trie.seek(pos + 2 + keyNodeSize + 20); //skip hash
                trie.read(childsMap, 0, 32);
                byte[] suffixKey = getBytesPart(key, keyNodeSize, key.length - keyNodeSize);

                int selfChildsCount = getChildsCountInMap(childsMap);
                int selfChildArraySize = selfChildsCount * (type == LEAF ? 2 : 8);

                if(selfChildsCount==1){ //jast delete this node and return zero
                    //insert free space in db
                    addPosInFreeSpaceMap(pos, keyNodeSize, selfChildArraySize);
                    return new byte[8];

                }else {

                    trie.seek(pos + 2 + keyNodeSize + 20 + 32);
                    byte[] childArray = new byte[selfChildArraySize];
                    trie.read(childArray, 0, selfChildArraySize);
                    int insByte = (type==LEAF ? (key[key.length-1]&0xFF) :(suffixKey[0]&0xFF));

                    if (type != LEAF) {//delete leaf child and copy leaf to new place
                        int childPosInMap = getChildPosInMap(childsMap, insByte);
                        long posToDelete = pos + 2 + keyNodeSize + 20 + 32 + ((childPosInMap * 8) - 8);
                        trie.seek(posToDelete);
                        byte[] chPos = new byte[8];
                        trie.read(chPos, 0, 8);
                        //insert to child
                        chPos = delete(getBytesPart(suffixKey, 1, suffixKey.length - 1), Longs.fromByteArray(chPos));
                        if(chPos == null){//child not found
                            return null;
                        }else if(Longs.fromByteArray(chPos) == 0L){//deleted child node

                            int chp = getChildPosInMap(childsMap, insByte);
                            //delete in map
                            childsMap = changeChildInMap(childsMap, insByte, false);
                            //delete in array
                            byte[] before = (chp == 0 ? new byte[0] : getBytesPart(childArray, 0, (chp - 1) * 8));
                            childArray = Bytes.concat(before, getBytesPart(childArray, before.length, childArray.length - before.length));
                            // пересчитываем хеш и рекурсивно вносим позицию в вышестоящие узлы
                            hash = calcHash(type, childArray);
                            addPosInFreeSpaceMap(pos, keyNodeSize, selfChildArraySize);
                            if((selfChildsCount-1)==1 && childArray.length == 8){//recover key if one stay child
                                pos = Longs.fromByteArray(childArray);
                                trie.seek(pos);
                                typeAndKeySize[0] = trie.readByte();
                                int oldKeySize = trie.readByte();
                                typeAndKeySize[1] = (byte)(oldKeySize+keyNodeSize);
                                byte[] oldKeyNode = new byte[oldKeySize];
                                trie.read(oldKeyNode, 0, oldKeySize);
                                trie.read(hash, 0, 20);
                                trie.read(childsMap, 0, 32);
                                selfChildsCount = getChildsCountInMap(childsMap);
                                selfChildArraySize = selfChildsCount * (typeAndKeySize[0] == LEAF ? 2 : 8);
                                byte[] newChildArray = new byte[selfChildArraySize];
                                trie.read(newChildArray, 0, selfChildArraySize);
                                addPosInFreeSpaceMap(pos, oldKeySize, selfChildArraySize);
                                return Longs.toByteArray(addRecordInFile(typeAndKeySize, (keyNode.length > 0 && oldKeyNode.length > 0 ? Bytes.concat(keyNode, oldKeyNode) : (keyNode.length > 0 ? keyNode : oldKeyNode)), hash, childsMap, newChildArray));
                            }else {//jast copy node in new place
                                return Longs.toByteArray(addRecordInFile(typeAndKeySize, keyNode, hash, childsMap, childArray));
                            }
                        }else{//changed child node
                            trie.seek(posToDelete);
                            trie.write(chPos);
                            trie.seek(pos + 2 + keyNodeSize + 20 + 32);
                            childArray = new byte[selfChildArraySize];
                            trie.read(childArray, 0, selfChildArraySize);
                            hash=calcHash(type, childArray);
                            trie.seek(pos + 2 + keyNodeSize);
                            trie.write(hash);
                            return Longs.toByteArray(pos);
                        }
                    }else{ // for leaf jast delete age and copy node to new place
                        int chp= getChildPosInMap(childsMap, insByte);
                        //delete in map
                        childsMap = changeChildInMap(childsMap, insByte, false);
                        byte[] before=(chp == 0 ? new byte[0] : getBytesPart(childArray,0,  (chp-1)*(type==LEAF ? 2 : 8)));
                        childArray = Bytes.concat(before, getBytesPart(childArray, before.length, childArray.length-before.length));
                        hash=calcHash(type, childsMap);

                        addPosInFreeSpaceMap(pos, keyNodeSize, selfChildArraySize);
                        return Longs.toByteArray(addRecordInFile(typeAndKeySize, keyNode, hash, childsMap, childArray));
                    }
                }
            }
        }
        return null;
    }

    private byte[] insert(byte[] key, byte[] age, long pos) throws IOException {
        Log.d("TRIE", String.valueOf(1));
        byte[] hash;
        byte[] childsMap = new byte[32];
        byte[] typeAndKeySize = new byte[2];

        if (trie.length() == 0) {
            trie.setLength(0);
            byte[] trieTmp = new byte[2068];
            trie.write(trieTmp);
            return insert(key, age, pos);
        }

        byte[] sResult=search(key, pos);

        if (pos==0L) {
            byte[] lKey;
            if (sResult == null){//Create new LEAF witch age
                lKey = getBytesPart(key, 1, key.length - 2);
                typeAndKeySize[0] = LEAF;
                typeAndKeySize[1] = (lKey==null ? (byte)0 : (byte)lKey.length);
                childsMap = changeChildInMap(new byte[32], (key[key.length-1]&0xFF), true);//add age
                hash=calcHash(typeAndKeySize[0], childsMap);
                pos = addRecordInFile(typeAndKeySize, lKey, hash, childsMap, age);
            }else{//insert in child & save in root
                lKey = getBytesPart(key, 1, key.length - 1);
                pos = Longs.fromByteArray(insert(lKey, age, Longs.fromByteArray(sResult)));
            }
            //save in root
            trie.seek(20+ getChildPosInArray((key[0]&0xFF), BRANCH));
            trie.write(Longs.toByteArray(pos));
            trie.seek(20);
            byte[] childArray= new byte[2048];
            trie.read(childArray,0,2048);
            hash=calcHash(ROOT, childArray);
            trie.seek(0);
            trie.write(hash);
            return hash;

        }else {
            if (sResult != null) {
                trie.seek(pos);
                byte type = trie.readByte();
                byte keyNodeSize = trie.readByte();
                byte[] keyNode = new byte[keyNodeSize];
                trie.read(keyNode, 0, keyNodeSize);
                trie.seek(pos + 2 + keyNodeSize + 20); //skip hash
                trie.read(childsMap, 0, 32);
                //Получим суффикс
                byte[] suffixKey = getBytesPart(key, keyNodeSize, key.length - keyNodeSize);

                int selfChildsCount = getChildsCountInMap(childsMap);
                int selfChildArraySize = selfChildsCount * (type==LEAF ? 2 : 8);

                int childPosInMap = getChildPosInMap(childsMap, (suffixKey[0] & 0xFF));
                if(childPosInMap==0){
                    Log.d("TRIE", String.valueOf((suffixKey[0] & 0xFF)));
                }
                long posToWrite = pos + 2 + keyNodeSize + 20 + 32 + (type==LEAF ? ((childPosInMap * 2) - 2) : ((childPosInMap * 8) - 8));
                trie.seek(posToWrite);

                if(type==LEAF){
                    trie.write(age);
                }else{
                    byte[] chPos=new byte[8];
                    trie.read(chPos, 0, 8);
                    //insert to child
                    chPos = insert(getBytesPart(suffixKey, 1, suffixKey.length-1), age, Longs.fromByteArray(chPos));
                    trie.seek(posToWrite);
                    trie.write(chPos);
                }

                trie.seek(pos + 2 + keyNodeSize + 20 + 32);
                byte[] childArray = new byte[selfChildArraySize];
                trie.read(childArray, 0, selfChildArraySize);
                hash = calcHash(type, (type==LEAF ? childsMap : childArray));
                trie.seek(pos + 2 + keyNodeSize);
                trie.write(hash);
                return Longs.toByteArray(pos);
            } else {

                trie.seek(pos);
                //прочитаем ключ, карту детей, число детей, суфикс префикс
                byte type = trie.readByte();
                byte keyNodeSize = trie.readByte();

                byte[] keyNode = new byte[keyNodeSize];
                trie.read(keyNode, 0, keyNodeSize);

                trie.seek(pos + 2 + keyNodeSize + 20); //skip hash
                trie.read(childsMap, 0, 32);
                //Получим префикс и суффикс искомого ключа
                byte[] preffixKey = getBytesPart(key, 0, keyNodeSize);

                byte[] suffixKey = getBytesPart(key, keyNodeSize, key.length - keyNodeSize);
                byte[] commonKey = getCommonKey(keyNode, preffixKey);

                int selfChildsCount = getChildsCountInMap(childsMap);
                int selfChildArraySize = selfChildsCount*(type==LEAF ? 2 : 8);

                byte[] selfChildArray= new byte[selfChildArraySize];
                trie.seek(pos+2+keyNodeSize+20+32);//go to childArray
                trie.read(selfChildArray,0,selfChildArraySize);

                //insert free space in db
                addPosInFreeSpaceMap(pos, keyNodeSize, selfChildArraySize);

                byte[] retPos;
                byte[] childsMapNew;
                byte[] childArray;

                if(!Arrays.equals(preffixKey, keyNode)){//create sub node
                    //ADD NEW LEAF
                    assert commonKey != null;
                    byte[] leafKey = getBytesPart(key, commonKey.length , keyNodeSize - commonKey.length);
                    byte[] leafKey_ = getBytesPart(leafKey, 1 , leafKey.length - 1);
                    typeAndKeySize[0] = LEAF;
                    typeAndKeySize[1] = (byte)leafKey_.length;
                    childsMapNew = changeChildInMap(new byte[32], (key[key.length-1]&0xFF),true);
                    hash=calcHash(typeAndKeySize[0], childsMapNew);
                    long posLeaf = addRecordInFile(typeAndKeySize, leafKey_, hash, childsMapNew, age);

                    //COPY OLD NODE WITCH CORP(keyNode - common) KEY
                    byte[] oldLeafKey = getBytesPart(keyNode, commonKey.length , keyNodeSize - commonKey.length);
                    byte[] oldLeafKey_ = getBytesPart(oldLeafKey, 1 , oldLeafKey.length - 1);
                    typeAndKeySize[0] = type;
                    typeAndKeySize[1] = (byte)oldLeafKey_.length;
                    hash=calcHash(type, (type==LEAF ? childsMap : selfChildArray));
                    long posOldLeaf = addRecordInFile(typeAndKeySize, oldLeafKey_, hash, childsMap, selfChildArray);

                    //CREATE NEW BRANCH WITCH COMMON KEY AND CONTENT = NEW LEAF POSITION + OLD NODE POSITION
                    typeAndKeySize[0] = BRANCH;
                    typeAndKeySize[1] = (byte)commonKey.length;
                    if (leafKey[0]>oldLeafKey[0]){
                        childArray = Bytes.concat(Longs.toByteArray(posOldLeaf), Longs.toByteArray(posLeaf));
                    }else{
                        childArray = Bytes.concat(Longs.toByteArray(posLeaf), Longs.toByteArray(posOldLeaf));
                    }
                    hash=calcHash(BRANCH, childArray);

                    retPos = Longs.toByteArray(addRecordInFile(typeAndKeySize, commonKey, hash, changeChildInMap(changeChildInMap(new byte[32], (leafKey[0]&0xFF),true), (oldLeafKey[0]&0xFF),true), childArray));

                }else{//if isLeaf add age in node, else create leaf witch suffix key and add pos in node(branch)
                    long posLeaf=0L;
                    byte[] leafKey;
                    int insByte = 0;
                    if(type!=LEAF){
                        typeAndKeySize[0] = LEAF;
                        leafKey = getBytesPart(suffixKey, 1 , suffixKey.length - 1);
                        insByte = (suffixKey[0]&0xFF);
                        typeAndKeySize[1] = (byte)leafKey.length;
                        childsMapNew = changeChildInMap(new byte[32], (key[key.length-1]&0xFF),true);
                        hash=calcHash(LEAF, childsMapNew);
                        posLeaf = addRecordInFile(typeAndKeySize, leafKey, hash, childsMapNew, age);
                    }else{
                        insByte = (key[key.length-1]&0xFF);
                    }

                    typeAndKeySize[0] = type;
                    typeAndKeySize[1] = (byte)keyNode.length;
                    childsMap = changeChildInMap(childsMap, insByte,true);
                    int chp= getChildPosInMap(childsMap, insByte);
                    byte[] before=(chp == 0 ? new byte[0] : getBytesPart(selfChildArray,0,  (chp-1)*(type==LEAF ? 2 : 8)));
                    childArray = Bytes.concat(before, (type==LEAF ? age : Longs.toByteArray(posLeaf)), getBytesPart(selfChildArray, before.length, selfChildArray.length-before.length));
                    // пересчитываем хеш и рекурсивно вносим позицию в вышестоящие узлы
                    hash=calcHash(type, (type==LEAF ? childsMap : childArray));

                    retPos = Longs.toByteArray(addRecordInFile(typeAndKeySize, keyNode, hash, childsMap, childArray));

                }
                return retPos;
            }
        }
    }

    private long addRecordInFile(byte[] typeAndKeySize, byte[] key, byte[] hash, byte[] childsMap, byte[] childArray) throws IOException {
        byte[] record;
        if(typeAndKeySize[1]==0){
            record = Bytes.concat(typeAndKeySize, hash, childsMap, childArray);
        }else{
            record = Bytes.concat(typeAndKeySize, key, hash, childsMap, childArray);
        }
        Cursor query = db.rawQuery("SELECT * FROM freeSpace where space="+record.length, null);
        trie.seek(trie.length());
        long pos = trie.getFilePointer();
        if (query.moveToFirst()) {
            int posColIndex = query.getColumnIndex("pos");
            pos = query.getLong(posColIndex);
            db.delete("freeSpace",  "pos = ?", new String[] { String.valueOf(pos) });
            trie.seek(pos);
        }
        query.close();

        trie.write(record);
        return pos;
    }

    private void addPosInFreeSpaceMap(long pos, int keyNodeSize, int selfChildArraySize){
        cv.put("pos", pos);
        cv.put("space", 2+keyNodeSize+20+32+selfChildArraySize);
        db.insert("freeSpace", null, cv);
        cv.clear();
    }
    //return pos or age or null - if not found
    private byte[] search(byte[] key, long pos) throws IOException {
        //Если размер файла == 0  то вернем null
        if(trie.length()>0) {
            //если это корень то переместим курсор на позицию в массиве детей = первый байт ключа * 8 - 8
            //если содержимое != 0 то  начинаем искать там и вернем то что нашли + корень, иначе вернем корень
            if (pos == 0L) {
                trie.seek(20 + getChildPosInArray((key[0]&0xFF), BRANCH));
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
                trie.seek(pos + 2 + keyNodeSize + 20); //skip hash
                byte[] childsMap = new byte[32];
                trie.read(childsMap, 0, 32);
                //Получим префикс и суффикс искомого ключа

                byte[] preffixKey = getBytesPart(key, 0, keyNodeSize);
                byte[] suffixKey = getBytesPart(key, keyNodeSize, key.length - keyNodeSize);

                //found Если искомый ключ(его часть =  длинне ключа узла) = ключу узла и первый байт суффикса имеется в массиве дочерей то
                if(Arrays.equals(preffixKey, keyNode) && checkExistChildInMap(childsMap, (suffixKey[0] & 0xFF))) {
                    int childPosInMap = 0;
                    byte[] result;
                    if(type==BRANCH) {//ret pos
                        childPosInMap = getChildPosInMap(childsMap, (suffixKey[0] & 0xFF));
                        trie.seek(pos + 2 + keyNodeSize + 20 + 32 + ((childPosInMap * 8) - 8));
                        result = new byte[8];
                        trie.read(result, 0, 8);

                    }else {                  //ret age
                        childPosInMap = getChildPosInMap(childsMap, (suffixKey[0] & 0xFF));
                        trie.seek(pos + 2 + keyNodeSize + 20 + 32 + ((childPosInMap * 2) - 2));
                        result = new byte[2];
                        trie.read(result, 0, 2);
                    }
                    return result;
                }
            }
        }
        return null;
    }

    private byte[] getCommonKey(byte[] selfKey, byte[] key) {
        for(int i = 1; i < selfKey.length+1; i++){
            byte[] sK = getBytesPart(key, 0, selfKey.length-i);
            if(Arrays.equals(sK, getBytesPart(selfKey, 0, selfKey.length-i))){
                return sK;
            }
        }
        return null;
    }

    private int getChildPosInArray(int key, byte type){
        if(type==BRANCH){
            return (key==0?0:(key * 8) - 8);
        }else{
            return (key==0?0:(key * 2) - 2);
        }
    }

    private int getChildPosInMap(byte[]childsMap, int key){
        int result=0;
        BitSet prepare = BitSet.valueOf(childsMap);
        for(int i = 0; i < key+1;i++){
            if(prepare.get(i)) result=result+1;
        }
        return result;
    }

    private int getChildsCountInMap(byte[]childsMap){
        return BitSet.valueOf(childsMap).cardinality();
    }

    private boolean checkExistChildInMap(byte[]childsMap, int key){
        BitSet prepare = BitSet.valueOf(childsMap);
        return prepare.get(key);
    }

    private byte[] _changeChildInMap(byte[]childsMap, int key, boolean operation){
        BitSet prepare = BitSet.valueOf(childsMap);
        prepare.set(key, operation);
        return prepare.toByteArray();
    }

    private byte[] changeChildInMap(byte[]childsMap, int key, boolean operation){
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

    private byte[] getBytesPart(byte[]src, int off, int len){
        byte[] result= new byte[len];
        System.arraycopy(src, off, result, 0, result.length);
        return result;
    }

    private byte[] getHash(long pos) throws IOException {

        byte[] hash = new byte[20];
        if (pos == 0L) {
            trie.read(hash, 0, 20);
        } else {
            trie.seek(pos+1);
            byte keySize = trie.readByte();
            trie.seek(pos+ 2 + keySize);
            trie.read(hash, 0, 20);
        }
        return hash;

    }

    private byte[] calcHash(byte type, byte[] childArray) throws IOException {
        byte[] hash;
        byte[] digest = new byte[0];
        if(type==BRANCH || type==ROOT) {
            for(int i = 0; i < childArray.length;) {
                long pos = Longs.fromByteArray(getBytesPart(childArray, i, 8));
                if(pos!=0){
                    digest = Bytes.concat(digest, getHash(pos));
                }
                i = i + 8;
            }
            hash = sha256hash160(digest);
        }else if(type==LEAF){
            digest=Bytes.concat(digest, childArray);
            hash = sha256hash160(digest);
        }else{
            hash = null;
        }
        return hash;
    }


    //////////////SYNC/////////////////////////////////////////////////////////////////////////////////////
    private void sync(String hostPort, boolean master, byte key){

        //socket
        List<BasicNameValuePair> mExtraHeaders = Arrays.asList(new BasicNameValuePair("Cookie", "session=abcd"));
        WebSocketClient mClient = new WebSocketClient(new WebSocketClient.Listener() {

            private static final String TAG = "WebSocketClient";

            @Override
            public void onConnect() {
                Log.d(TAG, "Connected!");
                //wsOnConnected();
            }

            @Override
            public void onMessage(String message) {
                Log.d(TAG, String.format("Got string message! %s", message));
            }

            @Override
            public void onMessage(byte[] data) {
                Log.d(TAG, String.format("Got binary message! %s", data.toString()));
            }

            @Override
            public void onDisconnect(int code, String reason) {
                Log.d(TAG, String.format("Disconnected! Code: %d Reason: %s", code, reason));
            }

            @Override
            public void onError(Exception error) {
                Log.e(TAG, "Error!", error);
            }

        }, mExtraHeaders);
        mClient.connect(URI.create(hostPort));
    }
}