package com.infoman.liquideconomycs;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;

import java.io.IOException;
import java.util.Arrays;
import java.util.Date;

import static com.infoman.liquideconomycs.Utils.ACTION_DELETE;
import static com.infoman.liquideconomycs.Utils.ACTION_FIND;
import static com.infoman.liquideconomycs.Utils.ACTION_GENERATE_ANSWER;
import static com.infoman.liquideconomycs.Utils.ACTION_GET_HASH;
import static com.infoman.liquideconomycs.Utils.ACTION_INSERT;
import static com.infoman.liquideconomycs.Utils.BRANCH;
import static com.infoman.liquideconomycs.Utils.BROADCAST_ACTION_ANSWER;
import static com.infoman.liquideconomycs.Utils.EXTRA_AGE;
import static com.infoman.liquideconomycs.Utils.EXTRA_ANSWER;
import static com.infoman.liquideconomycs.Utils.EXTRA_CMD;
import static com.infoman.liquideconomycs.Utils.EXTRA_MASTER;
import static com.infoman.liquideconomycs.Utils.EXTRA_MSG_TYPE;
import static com.infoman.liquideconomycs.Utils.EXTRA_PAYLOAD;
import static com.infoman.liquideconomycs.Utils.EXTRA_POS;
import static com.infoman.liquideconomycs.Utils.EXTRA_PUBKEY;
import static com.infoman.liquideconomycs.Utils.LEAF;
import static com.infoman.liquideconomycs.Utils.ROOT;
import static com.infoman.liquideconomycs.Utils.changeChildInMap;
import static com.infoman.liquideconomycs.Utils.checkExistChildInMap;
import static com.infoman.liquideconomycs.Utils.getBytesPart;
import static com.infoman.liquideconomycs.Utils.getChildPosInArray;
import static com.infoman.liquideconomycs.Utils.getChildPosInMap;
import static com.infoman.liquideconomycs.Utils.getChildsCountInMap;
import static com.infoman.liquideconomycs.Utils.getCommonKey;
import static org.bitcoinj.core.Utils.sha256hash160;

//TODO add max age field in leaf and branch node = max age in childs, for automate delete to old pubKey

public class TrieServiceIntent extends IntentService {

    private Core app;

    public TrieServiceIntent() {
        super("TrieServiceIntent");
    }

    // called by activity to communicate to service
    public static void startActionGetHash(Context context, String master, long pos) {
        Intent intent = new Intent(context, TrieServiceIntent.class)
            .setAction(ACTION_GET_HASH)
            .putExtra(EXTRA_MASTER, master)
            .putExtra(EXTRA_POS, pos);
        context.startService(intent);
    }

    // called by activity to communicate to service
    public static void startActionInsert(Context context, String master, byte[] pubKey, byte[] age) {
        Intent intent = new Intent(context, TrieServiceIntent.class)
            .setAction(ACTION_INSERT)
            .putExtra(EXTRA_MASTER, master)
            .putExtra(EXTRA_PUBKEY, pubKey)
            .putExtra(EXTRA_AGE, age);
        context.startService(intent);
    }

    // called by activity to communicate to service
    public static void startActionFind(Context context, String master, byte[] pubKey, long pos) {
        Intent intent = new Intent(context, TrieServiceIntent.class)
            .setAction(ACTION_FIND)
            .putExtra(EXTRA_MASTER, master)
            .putExtra(EXTRA_PUBKEY, pubKey)
            .putExtra(EXTRA_POS, pos);
        context.startService(intent);
    }

    // called by activity to communicate to service
    public static void startActionDelete(Context context, String master, byte[] pubKey, long pos) {
        Intent intent = new Intent(context, TrieServiceIntent.class)
            .setAction(ACTION_DELETE)
            .putExtra(EXTRA_MASTER, master)
            .putExtra(EXTRA_PUBKEY, pubKey)
            .putExtra(EXTRA_POS, pos);
        context.startService(intent);
    }

    // called by activity to communicate to service
    public static void startActionGenerateAnswer(Context context, String master, byte msgType, byte[] payload) {
        Intent intent = new Intent(context, TrieServiceIntent.class)
            .setAction(ACTION_GENERATE_ANSWER)
            .putExtra(EXTRA_MSG_TYPE, msgType)
            .putExtra(EXTRA_PAYLOAD, payload);
        context.startService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        //android.os.Debug.waitForDebugger();
        app = (Core) getApplicationContext();

    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_GET_HASH.equals(action)) {
                final String master = intent.getStringExtra(EXTRA_MASTER), cmd = "GetHash";
                final long pos = intent.getLongExtra(EXTRA_POS,0L);
                ////////////////////////////////////////////////////////////////
                try {
                    broadcastActionMsg(master, cmd, getHash(pos));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                ////////////////////////////////////////////////////////////////
            }

            if (ACTION_INSERT.equals(action)) {
                final String master = intent.getStringExtra(EXTRA_MASTER), cmd = "Insert";
                final byte[] key = intent.getByteArrayExtra(EXTRA_PUBKEY), value = intent.getByteArrayExtra(EXTRA_AGE);
                ////////////////////////////////////////////////////////////////
                try {
                    broadcastActionMsg(master, cmd, insert(key, value, 0L));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                ////////////////////////////////////////////////////////////////
            }

            if (ACTION_FIND.equals(action)) {
                final String master = intent.getStringExtra(EXTRA_MASTER), cmd = "Find";
                final byte[] key = intent.getByteArrayExtra(EXTRA_PUBKEY);
                final long pos = intent.getLongExtra(EXTRA_POS, 0L);
                ////////////////////////////////////////////////////////////////
                try {
                    broadcastActionMsg(master, cmd, find(key, pos));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                ////////////////////////////////////////////////////////////////
            }

            if (ACTION_DELETE.equals(action)) {
                final String master = intent.getStringExtra(EXTRA_MASTER), cmd = "Delete";
                final byte[] key = intent.getByteArrayExtra(EXTRA_PUBKEY);
                final long pos = intent.getLongExtra(EXTRA_POS, 0L);
                ////////////////////////////////////////////////////////////////
                try {
                    broadcastActionMsg(master, cmd, delete(key, pos));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                ////////////////////////////////////////////////////////////////
            }

            if (ACTION_GENERATE_ANSWER.equals(action)) {
                final byte msgType = intent.getByteExtra(EXTRA_MSG_TYPE, Utils.getHashs);
                final byte[] payload = intent.getByteArrayExtra(EXTRA_AGE);
                ////////////////////////////////////////////////////////////////
                try {
                    generateAnswer(msgType, payload);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                ////////////////////////////////////////////////////////////////
            }
        }
    }

    private void generateAnswer(byte msgType, byte[] payload) throws IOException {
        Context context = app.getApplicationContext();
        //todo
        if(msgType == Utils.getHashs){
            byte[] answer = new byte[0];
            for(int i=0;i < payload.length/8;i++){
                // todo return pos & type & map & array(pos+hash if it is BRANCH or age if it is LEAF)
                answer = Bytes.concat(answer, Utils.getBytesPart(payload,i*8, 8), getNodeMapAndHashsOrAges(Utils.getBytesPart(payload,i*8, 8)));
            }
            app.sendMsg(msgType, answer);
        }else{
            //нам прислали рание запрошенные узлы, необходимо их расшифровать
            for(int i = 0; i < payload.length;) {
                long pos                = Longs.fromByteArray(Utils.getBytesPart(payload, i, 8));
                //Получим полный префикс ключа в предидущем цикле перед запросом
                byte[] prefix           = app.getPrefixByPos(pos);
                //получить позицию узла в дереве по префиксу
                byte[] selfNodePos      = find(prefix, 0L);
                //если найден то получитм карту и хеши\возраста к ней
                byte[] selfNodeMap      = null;
                byte[] selfNodeHashOrAge = null;
                if(selfNodePos!=null){
                    byte[] selfNodeMapAndHashOrAge  = getNodeMapAndHashsOrAges(selfNodePos);
                    byte[] typeAndKeySize = Utils.getBytesPart(selfNodeMapAndHashOrAge, 0, 2);
                    if(typeAndKeySize[1]>0){
                        prefix              = Bytes.concat(prefix, Utils.getBytesPart(selfNodeMapAndHashOrAge, 2, typeAndKeySize[1])) ;
                        selfNodeMap         = Utils.getBytesPart(selfNodeMapAndHashOrAge, 2 + typeAndKeySize[1], 32);
                        selfNodeHashOrAge   = Utils.getBytesPart(selfNodeMapAndHashOrAge, 2 + typeAndKeySize[1] + 32 , selfNodeMapAndHashOrAge.length - (2 + typeAndKeySize[1] + 32));
                    }else{
                        selfNodeMap         = Utils.getBytesPart(selfNodeMapAndHashOrAge, 2 , 32);
                        selfNodeHashOrAge   = Utils.getBytesPart(selfNodeMapAndHashOrAge, 2  + 32 , selfNodeMapAndHashOrAge.length - (2 + 32));
                    }
                }else{
                    continue;
                }
                byte nodeType           = Utils.getBytesPart(payload, i+8, 1)[0];
                byte nodeKeySize        = Utils.getBytesPart(payload, i+8+1, 2)[0];
                int offLen              = (nodeType==BRANCH?20:2);
                byte[] childsMap        = Utils.getBytesPart(payload, i + 10 + nodeKeySize, 32);
                int childsCountInMap    = Utils.getChildsCountInMap(childsMap);
                int len                 = childsCountInMap * (nodeType==Utils.LEAF ? 2 : 28);
                byte[] childsArray      = Utils.getBytesPart(payload, i + 10 + nodeKeySize + 32, len);
                i                       = i + 10 + nodeKeySize + 32 + len;
                //todo В цикле  от 0 - 255 мы должны
                // 1) Если selfNodePos<>null и узел\возраст не найден в полученной карте, но есть в нашей, тогда ничего не делаем ибо оно есть у нас и не удалено автоматом по возрасту
                // 2) Если узел\возраст найден:
                // 3) Если это тип BRANCH и (selfNodePos<>null и узел в карте имеет хеш не равный нашему или selfNodePos==null) то
                // внести в базу (prefix + индекс позиции в карте) и позицию, добавить позицию в следующий запрос
                // 4) Если это тип LEAF и ((selfNodePos<>null и узел в карте имеет возраст не равный нашему  и возраст моложе нашего ) или selfNodePos==null)
                // то добавить в список на добавление(изменение) (prefix + индекс цикла) и возраст
                byte[] ask = new byte[0];
                for(int c = 0; c < 255; c++){
                    byte[] c_ = new byte[1];
                    c_[0] = (byte)c;
                    if(selfNodePos!=null && !checkExistChildInMap(childsMap, c) && checkExistChildInMap(selfNodeMap, c)){
                        //todo add to list delete
                        app.addPrefixByPos(0L, Bytes.concat(prefix,c_), null);
                    }
                    if(checkExistChildInMap(childsMap, c)){
                        if(nodeType==BRANCH){
                            if(selfNodePos == null || !Arrays.equals(
                                    Utils.getBytesPart(childsArray, (getChildPosInMap(childsMap, c) * 28) - offLen, offLen),
                                    Utils.getBytesPart(selfNodeHashOrAge, (getChildPosInMap(selfNodeMap, c) * offLen) - offLen, offLen)
                            )){
                                //todo add to table sync add to list new ask
                                long pos_ = Longs.fromByteArray(Utils.getBytesPart(childsArray, (getChildPosInMap(childsMap, c) * 28) - 28, 8));
                                app.addPrefixByPos(pos_, Bytes.concat(prefix,c_), null);
                                ask = Bytes.concat(ask, Longs.toByteArray(pos_));
                            }
                        }else{
                            byte[] childAge=Utils.getBytesPart(childsArray, (getChildPosInMap(childsMap, c) * offLen) - offLen, offLen);
                            if(selfNodePos == null ||
                                    (!Arrays.equals(childAge, Utils.getBytesPart(selfNodeHashOrAge, (getChildPosInMap(selfNodeMap, c) * offLen) - offLen, offLen))
                                            && Utils.compareDate(Utils.reconstructAgeFromBytes(childAge), Utils.reconstructAgeFromBytes(Utils.getBytesPart(selfNodeHashOrAge, (getChildPosInMap(selfNodeMap, c) * offLen) - offLen, offLen)))>0)
                            ){

                                //app.addPrefixByPos(0L, Bytes.concat(prefix,c_), childAge, false);
                                //todo add list add/update (check age)
                                SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
                                long maxAge = sharedPref.getLong("maxAge", 30);
                                if(Utils.compareDate(new Date(), Utils.reconstructAgeFromBytes(childAge))<maxAge && Utils.compareDate(new Date(), Utils.reconstructAgeFromBytes(childAge))>=0)
                                    startActionInsert(context, "Main", Bytes.concat(prefix,c_), childAge);

                            }
                        }
                    }
                }
                app.sendMsg(msgType, ask);
            }
        }


    }

    private byte[] getNodeMapAndHashsOrAges(byte[] selfNodePos) throws IOException {

        byte[] childsMap = new byte[32];
        byte[] typeAndKeySize = new byte[2];
        long pos = Longs.fromByteArray(selfNodePos);
        app.trie.seek(pos);
        app.trie.read(typeAndKeySize, 0,2);
        byte[] keyNode = new byte[typeAndKeySize[1]];
        app.trie.read(keyNode, 0, typeAndKeySize[1]);
        app.trie.seek(pos + 2 + typeAndKeySize[1] + 20); //skip hash
        app.trie.read(childsMap, 0, 32);
        int selfChildsCount = getChildsCountInMap(childsMap);
        int selfChildArraySize = selfChildsCount * (typeAndKeySize[0]==LEAF ? 2 : 8);
        byte[] selfChildArray = new byte[selfChildArraySize];
        app.trie.read(selfChildArray, 0, selfChildArraySize);
        byte[] type = new byte[1];
        type[0] = typeAndKeySize[0];
        if(typeAndKeySize[0]==LEAF){
            if(typeAndKeySize[1]>0)
                return Bytes.concat(typeAndKeySize, keyNode, childsMap, selfChildArray);
            else
                return Bytes.concat(typeAndKeySize, childsMap, selfChildArray);
        }else{
            for(int i = 0; i < selfChildArray.length;) {
                byte[] p = getBytesPart(selfChildArray, i, 8);
                if(p.length > 0){
                    childsMap = Bytes.concat(childsMap, getHash(Longs.fromByteArray(p)));
                }
                i = i + 8;
            }
            if(typeAndKeySize[1]>0)
                return Bytes.concat(typeAndKeySize, keyNode, childsMap);
            else
                return Bytes.concat(typeAndKeySize, childsMap);
        }
    }

    // called to send data to Activity
    public void broadcastActionMsg(String master, String cmd, byte[] answer) {
        Intent intent = new Intent(BROADCAST_ACTION_ANSWER)
            .putExtra(EXTRA_MASTER, master)
            .putExtra(EXTRA_CMD, cmd)
            .putExtra(EXTRA_ANSWER, answer);
        sendBroadcast(intent);
    }

    //return null if not found or (pos or age) if found
    private byte[] find(byte[] key, long pos) throws IOException {
        byte[] s=search(key, pos);

        if (s!=null && s.length>2 && key.length>1){
            return find(getBytesPart(key, 1, key.length - 1), Longs.fromByteArray(s));
        }else if(s!=null){
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
                    app.trie.seek(20 + getChildPosInArray((key[0] & 0xFF), BRANCH));
                    app.trie.write(posBytes);
                    app.trie.seek(20);
                    byte[] childArray = new byte[2048];
                    app.trie.read(childArray, 0, 2048);
                    hash = calcHash(ROOT, childArray);
                    app.trie.seek(0);
                    app.trie.write(hash);
                    return hash;
                }
            }else{
                app.trie.seek(pos);
                byte type = app.trie.readByte();
                byte keyNodeSize = app.trie.readByte();

                typeAndKeySize[0] = type;
                typeAndKeySize[1] = keyNodeSize;

                byte[] keyNode = new byte[keyNodeSize];
                app.trie.read(keyNode, 0, keyNodeSize);
                app.trie.seek(pos + 2 + keyNodeSize + 20); //skip hash
                app.trie.read(childsMap, 0, 32);
                byte[] suffixKey = getBytesPart(key, keyNodeSize, key.length - keyNodeSize);

                int selfChildsCount = getChildsCountInMap(childsMap);
                int selfChildArraySize = selfChildsCount * (type == LEAF ? 2 : 8);

                if(selfChildsCount==1){ //jast delete this node and return zero
                    //insert free space in db
                    app.addPosInFreeSpaceMap(pos, keyNodeSize, selfChildArraySize);
                    return new byte[8];

                }else {

                    app.trie.seek(pos + 2 + keyNodeSize + 20 + 32);
                    byte[] childArray = new byte[selfChildArraySize];
                    app.trie.read(childArray, 0, selfChildArraySize);
                    int insByte = (type==LEAF ? (key[key.length-1]&0xFF) :(suffixKey[0]&0xFF));

                    if (type != LEAF) {//delete leaf child and copy leaf to new place
                        int childPosInMap = getChildPosInMap(childsMap, insByte);
                        long posToDelete = pos + 2 + keyNodeSize + 20 + 32 + ((childPosInMap * 8) - 8);
                        app.trie.seek(posToDelete);
                        byte[] chPos = new byte[8];
                        app.trie.read(chPos, 0, 8);
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
                            app.addPosInFreeSpaceMap(pos, keyNodeSize, selfChildArraySize);
                            if((selfChildsCount-1)==1 && childArray.length == 8){//recover key if one stay child
                                pos = Longs.fromByteArray(childArray);
                                app.trie.seek(pos);
                                typeAndKeySize[0] = app.trie.readByte();
                                int oldKeySize = app.trie.readByte();
                                typeAndKeySize[1] = (byte)(oldKeySize+keyNodeSize);
                                byte[] oldKeyNode = new byte[oldKeySize];
                                app.trie.read(oldKeyNode, 0, oldKeySize);
                                app.trie.read(hash, 0, 20);
                                app.trie.read(childsMap, 0, 32);
                                selfChildsCount = getChildsCountInMap(childsMap);
                                selfChildArraySize = selfChildsCount * (typeAndKeySize[0] == LEAF ? 2 : 8);
                                byte[] newChildArray = new byte[selfChildArraySize];
                                app.trie.read(newChildArray, 0, selfChildArraySize);
                                app.addPosInFreeSpaceMap(pos, oldKeySize, selfChildArraySize);
                                return Longs.toByteArray(addRecordInFile(typeAndKeySize, (keyNode.length > 0 && oldKeyNode.length > 0 ? Bytes.concat(keyNode, oldKeyNode) : (keyNode.length > 0 ? keyNode : oldKeyNode)), hash, childsMap, newChildArray));
                            }else {//jast copy node in new place
                                return Longs.toByteArray(addRecordInFile(typeAndKeySize, keyNode, hash, childsMap, childArray));
                            }
                        }else{//changed child node
                            app.trie.seek(posToDelete);
                            app.trie.write(chPos);
                            app.trie.seek(pos + 2 + keyNodeSize + 20 + 32);
                            childArray = new byte[selfChildArraySize];
                            app.trie.read(childArray, 0, selfChildArraySize);
                            hash=calcHash(type, childArray);
                            app.trie.seek(pos + 2 + keyNodeSize);
                            app.trie.write(hash);
                            return Longs.toByteArray(pos);
                        }
                    }else{ // for leaf jast delete age and copy node to new place
                        int chp= getChildPosInMap(childsMap, insByte);
                        //delete in map
                        childsMap = changeChildInMap(childsMap, insByte, false);
                        byte[] before=(chp == 0 ? new byte[0] : getBytesPart(childArray,0,  (chp-1)*(type==LEAF ? 2 : 8)));
                        childArray = Bytes.concat(before, getBytesPart(childArray, before.length, childArray.length-before.length));
                        hash=calcHash(type, childArray);

                        app.addPosInFreeSpaceMap(pos, keyNodeSize, selfChildArraySize);
                        return Longs.toByteArray(addRecordInFile(typeAndKeySize, keyNode, hash, childsMap, childArray));
                    }
                }
            }
        }
        return null;
    }

    private byte[] insert(byte[] key, byte[] age, long pos) throws IOException {
        byte[] hash;
        byte[] childsMap = new byte[32];
        byte[] typeAndKeySize = new byte[2];

        if (app.trie.length() == 0) {
            app.trie.setLength(0);
            byte[] trieTmp = new byte[2068];
            app.trie.write(trieTmp);
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
                hash=calcHash(typeAndKeySize[0], age);
                pos = addRecordInFile(typeAndKeySize, lKey, hash, childsMap, age);
            }else{//insert in child & save in root
                lKey = getBytesPart(key, 1, key.length - 1);
                pos = Longs.fromByteArray(insert(lKey, age, Longs.fromByteArray(sResult)));
            }
            //save in root
            app.trie.seek(20+ getChildPosInArray((key[0]&0xFF), BRANCH));
            app.trie.write(Longs.toByteArray(pos));
            app.trie.seek(20);
            byte[] childArray= new byte[2048];
            app.trie.read(childArray,0,2048);
            hash=calcHash(ROOT, childArray);
            app.trie.seek(0);
            app.trie.write(hash);
            return hash;

        }else {
            if (sResult != null) {
                app.trie.seek(pos);
                byte type = app.trie.readByte();
                byte keyNodeSize = app.trie.readByte();
                byte[] keyNode = new byte[keyNodeSize];
                app.trie.read(keyNode, 0, keyNodeSize);
                app.trie.seek(pos + 2 + keyNodeSize + 20); //skip hash
                app.trie.read(childsMap, 0, 32);
                //Получим суффикс
                byte[] suffixKey = getBytesPart(key, keyNodeSize, key.length - keyNodeSize);

                int selfChildsCount = getChildsCountInMap(childsMap);
                int selfChildArraySize = selfChildsCount * (type==LEAF ? 2 : 8);

                int childPosInMap = getChildPosInMap(childsMap, (suffixKey[0] & 0xFF));
                if(childPosInMap==0){
                    Log.d("app.trie", String.valueOf((suffixKey[0] & 0xFF)));
                }
                long posToWrite = pos + 2 + keyNodeSize + 20 + 32 + (type==LEAF ? ((childPosInMap * 2) - 2) : ((childPosInMap * 8) - 8));
                app.trie.seek(posToWrite);

                if(type==LEAF){
                    app.trie.write(age);
                }else{
                    byte[] chPos=new byte[8];
                    app.trie.read(chPos, 0, 8);
                    //insert to child
                    chPos = insert(getBytesPart(suffixKey, 1, suffixKey.length-1), age, Longs.fromByteArray(chPos));
                    app.trie.seek(posToWrite);
                    app.trie.write(chPos);
                }

                app.trie.seek(pos + 2 + keyNodeSize + 20 + 32);
                byte[] childArray = new byte[selfChildArraySize];
                app.trie.read(childArray, 0, selfChildArraySize);
                hash = calcHash(type, childArray);
                app.trie.seek(pos + 2 + keyNodeSize);
                app.trie.write(hash);
                return Longs.toByteArray(pos);
            } else {

                app.trie.seek(pos);
                //прочитаем ключ, карту детей, число детей, суфикс префикс
                byte type = app.trie.readByte();
                byte keyNodeSize = app.trie.readByte();

                byte[] keyNode = new byte[keyNodeSize];
                app.trie.read(keyNode, 0, keyNodeSize);

                app.trie.seek(pos + 2 + keyNodeSize + 20); //skip hash
                app.trie.read(childsMap, 0, 32);
                //Получим префикс и суффикс искомого ключа
                byte[] preffixKey = getBytesPart(key, 0, keyNodeSize);

                byte[] suffixKey = getBytesPart(key, keyNodeSize, key.length - keyNodeSize);
                byte[] commonKey = getCommonKey(keyNode, preffixKey);

                int selfChildsCount = getChildsCountInMap(childsMap);
                int selfChildArraySize = selfChildsCount*(type==LEAF ? 2 : 8);

                byte[] selfChildArray= new byte[selfChildArraySize];
                app.trie.seek(pos+2+keyNodeSize+20+32);//go to childArray
                app.trie.read(selfChildArray,0,selfChildArraySize);

                //insert free space in db
                app.addPosInFreeSpaceMap(pos, keyNodeSize, selfChildArraySize);

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
                    hash=calcHash(typeAndKeySize[0], age);
                    long posLeaf = addRecordInFile(typeAndKeySize, leafKey_, hash, childsMapNew, age);

                    //COPY OLD NODE WITCH CORP(keyNode - common) KEY
                    byte[] oldLeafKey = getBytesPart(keyNode, commonKey.length , keyNodeSize - commonKey.length);
                    byte[] oldLeafKey_ = getBytesPart(oldLeafKey, 1 , oldLeafKey.length - 1);
                    typeAndKeySize[0] = type;
                    typeAndKeySize[1] = (byte)oldLeafKey_.length;
                    hash=calcHash(type, selfChildArray);
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
                        hash=calcHash(LEAF, age);
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
                    hash=calcHash(type, childArray);

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
        Cursor query = app.getFreeSpace(record.length);
        app.trie.seek(app.trie.length());
        long pos = app.trie.getFilePointer();
        if (query.moveToFirst()) {
            int posColIndex = query.getColumnIndex("pos");
            pos = query.getLong(posColIndex);
            app.deleteFreeSpace(pos);
            app.trie.seek(pos);
        }
        query.close();

        app.trie.write(record);
        return pos;
    }

    //return pos or age or null - if not found
    private byte[] search(byte[] key, long pos) throws IOException {
        //Если размер файла == 0  то вернем null
        if(app.trie.length()>0) {
            //если это корень то переместим курсор на позицию в массиве детей = первый байт ключа * 8 - 8
            //если содержимое != 0 то  начинаем искать там и вернем то что нашли + корень, иначе вернем корень
            if (pos == 0L) {
                app.trie.seek(20 + getChildPosInArray((key[0]&0xFF), BRANCH));
                byte[] childPos = new byte[8];
                app.trie.read(childPos, 0, 8);
                if (Longs.fromByteArray(childPos) != 0) {
                    return childPos;
                }
            } else {
                //иначе переместимся на позицию pos
                app.trie.seek(pos);
                //прочитаем ключ, карту дочерей, число дочерей, суфикс вносимого ключа
                byte type = app.trie.readByte();
                byte keyNodeSize = app.trie.readByte();
                byte[] keyNode = new byte[keyNodeSize];
                app.trie.read(keyNode, 0, keyNodeSize);
                app.trie.seek(pos + 2 + keyNodeSize + 20); //skip hash
                byte[] childsMap = new byte[32];
                app.trie.read(childsMap, 0, 32);
                //Получим префикс и суффикс искомого ключа

                byte[] preffixKey = getBytesPart(key, 0, keyNodeSize);
                byte[] suffixKey = getBytesPart(key, keyNodeSize, key.length - keyNodeSize);

                //found Если искомый ключ(его часть =  длинне ключа узла) = ключу узла и первый байт суффикса имеется в массиве дочерей то
                if(Arrays.equals(preffixKey, keyNode) && checkExistChildInMap(childsMap, (suffixKey[0] & 0xFF))) {
                    int childPosInMap = 0;
                    byte[] result;
                    if(type==BRANCH) {//ret pos
                        childPosInMap = getChildPosInMap(childsMap, (suffixKey[0] & 0xFF));
                        app.trie.seek(pos + 2 + keyNodeSize + 20 + 32 + ((childPosInMap * 8) - 8));
                        result = new byte[8];
                        app.trie.read(result, 0, 8);

                    }else {                  //ret age
                        childPosInMap = getChildPosInMap(childsMap, (suffixKey[0] & 0xFF));
                        app.trie.seek(pos + 2 + keyNodeSize + 20 + 32 + ((childPosInMap * 2) - 2));
                        result = new byte[2];
                        app.trie.read(result, 0, 2);
                    }
                    return result;
                }
            }
        }
        return null;
    }

    private byte[] getHash(long pos) throws IOException {

        byte[] hash = new byte[20];
        if (pos == 0L) {
            app.trie.seek(pos);
            app.trie.read(hash, 0, 20);
        } else {
            app.trie.seek(pos+1);
            byte keySize = app.trie.readByte();
            app.trie.seek(pos+ 2 + keySize);
            app.trie.read(hash, 0, 20);
        }
        return hash;

    }

    private byte[] calcHash(byte type, byte[] childArray) throws IOException {
        byte[] digest = new byte[0];
        if(type==BRANCH || type==ROOT) {
            for (int i = 0; i < childArray.length; ) {
                byte[] pos = getBytesPart(childArray, i, 8);
                if (pos.length > 0) {
                    digest = Bytes.concat(digest, getHash(Longs.fromByteArray(pos)));
                }
                i = i + (type == BRANCH || type == ROOT ? 8 : 2);
            }
            return sha256hash160(digest);
        }
        return sha256hash160(childArray);
    }
}
