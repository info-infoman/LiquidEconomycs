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

import org.bitcoinj.core.SignatureDecodeException;

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
import static com.infoman.liquideconomycs.Utils.ACTION_DELETE_OLDEST;
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
    private int waitingIntentCount = 0;
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
    public static void startActionGenerateAnswer(Context context, byte msgType, byte[] payload) {
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
        //android.os.Debug.waitForDebugger();
        //ROOT(content BRANCHs & LEAFs)
        //age(2)/hash sha256hash160(20) /child point array(1-256*8)
        //0000  /00*20                  /0000000000000000 max 2070 byte
        //BRANCH(content child BRANCHs & LEAFs)
        //age(2)/type(1)/key size(1)/key(0-18)/hash sha256hash160(20)/childsMap(32)  /nodePointArray(1-256*8)
        //0000  /00     /00         /00*18    /00*20                 /00*32         /00*8               max 2104 byte (ideal ‭153 185(branchs) =~307Mb)
        //LEAF(content accounts key suffix & age)
        //age(2)/type(1)/key size(1)/key(0-18)/hash sha256hash160(20)/childsMap(32)  /age Array(1-256*2)/
        //0000  /00     /00         /00*18    /00*20                 /00*32          /00*2               max 568 byte (ideal ‭39 062 500‬(leafs)=20GB)
        //total 21GB(ideal trie for 10 000 000 000 accounts)
        //
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        waitingIntentCount++;
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            waitingIntentCount--;
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
                final byte[] payload = intent.getByteArrayExtra(EXTRA_PAYLOAD);
                ////////////////////////////////////////////////////////////////
                try {
                    broadcastActionMsg("Trie", "Answer", generateAnswer(msgType, payload));
                } catch (IOException | SignatureDecodeException e) {
                    e.printStackTrace();
                }
                ////////////////////////////////////////////////////////////////
            }

            if (ACTION_DELETE_OLDEST.equals(action)) {
                try {
                    while (deleteOldest(0L, new byte[1])){}
                } catch (IOException e) {
                    e.printStackTrace();
                }
                ////////////////////////////////////////////////////////////////
            }

            if(waitingIntentCount==0) {
                //optimize free space in db
                optimize();
            }
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

    //todo add age sort for sync priority

    private byte[] generateAnswer(byte msgType, byte[] payload) throws IOException, SignatureDecodeException {
        Context context = app.getApplicationContext();
        byte[] answer = new byte[0];
        if (null == payload) payload = new byte[8];
        if(msgType == Utils.getHashs){
            //payload = array[pos...]
            for(int i=0;i < payload.length/8;i++){
                // todo return pos & type & map & array(pos+hash if it is BRANCH or age if it is LEAF)
                answer = Bytes.concat(answer, Utils.getBytesPart(payload,i*8, 8), getNodeMapAndHashesOrAges(Utils.getBytesPart(payload,i*8, 8)));
            }
            byte[] type = new byte[1];
            type[0] = Utils.hashs;
            answer = Bytes.concat(type, answer);
            //app.addSyncMsg(answer);
            //app.sendMsg(Utils.hashs, answer);
        }else{
            //payload = pos+typeAndKeySize+keyNode+childMap+childArray[pos+hash(BRANCH\ROOT)... or age(LEAF)...]
            //если ранее запрашивался корень то сравним с хешем нашего корня и если совпадает то ничего не делаем
            //если нет то построим карту своего корня и пройдемся по узлам как обычно
            //нам прислали рание запрошенные узлы, необходимо их расшифровать
            for(int i = 0; i < payload.length;) {
                //node from payload
                long pos                    = Longs.fromByteArray(Utils.getBytesPart(payload, i, 8));
                byte[] nodeTypeAndKeySize   = Utils.getBytesPart(payload, i+8, 2);
                int offLen                  = (nodeTypeAndKeySize[0]!=LEAF?20:2);
                byte[] childsMap            = Utils.getBytesPart(payload, i + 10 + nodeTypeAndKeySize[1], 32);
                int childsCountInMap        = Utils.getChildsCountInMap(childsMap);
                int len                     = childsCountInMap * (nodeTypeAndKeySize[0]==Utils.LEAF ? 2 : 28);
                byte[] childsArray          = Utils.getBytesPart(payload, i + 10 + nodeTypeAndKeySize[1] + 32, len);
                i                           = i + 10 + nodeTypeAndKeySize[1] + 32 + len;
                //self node
                byte[] selfNodePos,
                        selfPrefix,
                        selfNodeMapAndHashOrAge,
                        selfTypeAndKeySize,
                        selfNodeMap,
                        selfNodeHashsOrAges;
                if (nodeTypeAndKeySize[0]== ROOT){
                    selfPrefix = new byte[0];
                    selfNodePos = new byte[8];
                    selfNodeMapAndHashOrAge = getNodeMapAndHashesOrAges(selfNodePos);
                    selfTypeAndKeySize = Utils.getBytesPart(selfNodeMapAndHashOrAge, 0, 2);
                    selfNodeMap = Utils.getBytesPart(selfNodeMapAndHashOrAge, 2, 32);
                    selfNodeHashsOrAges = Utils.getBytesPart(selfNodeMapAndHashOrAge, 2 + 32, selfNodeMapAndHashOrAge.length - (2 + 32));
                }else {
                    //Получим полный префикс ключа в предидущем цикле перед запросом
                    //Префикс нарастает по мере движения в глюбь дерева
                    selfPrefix = app.getPrefixByPos(pos);
                    //todo delete old prefix
                    //получить позицию узла в дереве по префиксу
                    selfNodePos = find(selfPrefix, 0L);
                    //если найден то получитм карту и хеши\возраста к ней
                    if (selfNodePos != null) {
                        selfNodeMapAndHashOrAge = getNodeMapAndHashesOrAges(selfNodePos);
                        selfTypeAndKeySize = Utils.getBytesPart(selfNodeMapAndHashOrAge, 0, 2);
                        if (selfTypeAndKeySize[1] > 0) {
                            selfNodeMap = Utils.getBytesPart(selfNodeMapAndHashOrAge, 2 + selfTypeAndKeySize[1], 32);
                            selfNodeHashsOrAges = Utils.getBytesPart(selfNodeMapAndHashOrAge, 2 + selfTypeAndKeySize[1] + 32, selfNodeMapAndHashOrAge.length - (2 + selfTypeAndKeySize[1] + 32));
                        } else {
                            selfNodeMap = Utils.getBytesPart(selfNodeMapAndHashOrAge, 2, 32);
                            selfNodeHashsOrAges = Utils.getBytesPart(selfNodeMapAndHashOrAge, 2 + 32, selfNodeMapAndHashOrAge.length - (2 + 32));
                        }
                    } else {
                        continue;
                    }
                }
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
                    byte[] newPrefix =  selfTypeAndKeySize[0] != ROOT ? Bytes.concat(selfPrefix,c_):c_;
                    if(selfNodePos!=null && !checkExistChildInMap(childsMap, c) && checkExistChildInMap(selfNodeMap, c)){
                        //todo add to list delete
                        app.addPrefixByPos(0L, newPrefix, null);
                    }
                    if(checkExistChildInMap(childsMap, c)){
                        if(nodeTypeAndKeySize[0]==BRANCH){
                            if(selfNodePos == null || !Arrays.equals(
                                    Utils.getBytesPart(childsArray, (getChildPosInMap(childsMap, c) * 28) - offLen, offLen),
                                    Utils.getBytesPart(selfNodeHashsOrAges, (getChildPosInMap(selfNodeMap, c) * 28) - offLen, offLen)
                            )){
                                //todo add to table sync add to list new ask
                                long pos_ = Longs.fromByteArray(Utils.getBytesPart(childsArray, (getChildPosInMap(childsMap, c) * 28) - 28, 8));
                                app.addPrefixByPos(pos_, newPrefix, null);
                                ask = Bytes.concat(ask, Longs.toByteArray(pos_));
                            }
                        }else{
                            byte[] childAge=Utils.getBytesPart(childsArray, (getChildPosInMap(childsMap, c) * offLen) - offLen, offLen);
                            if(selfNodePos == null ||
                                    (!Arrays.equals(childAge, Utils.getBytesPart(selfNodeHashsOrAges, (getChildPosInMap(selfNodeMap, c) * offLen) - offLen, offLen))
                                            && Utils.compareDate(Utils.reconstructAgeFromBytes(childAge), Utils.reconstructAgeFromBytes(Utils.getBytesPart(selfNodeHashsOrAges, (getChildPosInMap(selfNodeMap, c) * offLen) - offLen, offLen)))>0)
                            ){
                                //app.addPrefixByPos(0L, Bytes.concat(prefix,c_), childAge, false);
                                //todo add list add/update (check age)
                                //todo add check newPrefix length
                                SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
                                long maxAge = sharedPref.getLong("maxAge", 30);
                                if(Utils.compareDate(new Date(), Utils.reconstructAgeFromBytes(childAge))<maxAge && Utils.compareDate(new Date(), Utils.reconstructAgeFromBytes(childAge))>=0)
                                    startActionInsert(context, "Main", newPrefix, childAge);

                            }
                        }
                    }
                }
                //app.sendMsg(msgType, ask);
                //app.addSyncMsg(ask);
                byte[] type = new byte[1];
                type[0] = Utils.getHashs;
                answer = Bytes.concat(type, ask);
            }
        }
        return Bytes.concat(answer);
    }

    //Получает typeAndKeySize+keyNode+childMap+childArray[pos+hash(BRANCH\ROOT)... or age(LEAF)...]
    // узла по позиции в файле дерева
    private byte[] getNodeMapAndHashesOrAges(byte[] selfNodePos) throws IOException {
        long pos = Longs.fromByteArray(selfNodePos);
        byte[] childMap         = new byte[32],
                typeAndKeySize  = new byte[2],
                keyNode,
                selfChildArray  = new byte[0],
                childPos        = new byte[8],
                result          = new byte[0];

        typeAndKeySize[0] = (byte)1;
        if(pos == 0L){
            //создать корневые параметры
            keyNode = new byte[0];
            app.trie.seek(pos+2+20);
            for (int i = 0; i < 256; i++) {
                app.trie.read(childPos, 0, 8);
                if (!Arrays.equals(childPos, new byte[8])) {
                    childMap = changeChildInMap(childMap, (i&0xFF),true);
                    selfChildArray = Bytes.concat(selfChildArray, childPos);
                }
            }
        }else {
            app.trie.seek(pos+2);
            app.trie.read(typeAndKeySize, 0, 2);
            keyNode = new byte[typeAndKeySize[1]];
            app.trie.read(keyNode, 0, typeAndKeySize[1]);
            app.trie.seek(pos + 4 + typeAndKeySize[1] + 20); //skip hash
            app.trie.read(childMap, 0, 32);
            int selfChildrenCount = getChildsCountInMap(childMap);
            int selfChildArraySize = selfChildrenCount * (typeAndKeySize[0] == LEAF ? 2 : 8);
            selfChildArray = new byte[selfChildArraySize];
            app.trie.read(selfChildArray, 0, selfChildArraySize);
        }
        if (typeAndKeySize[0] == LEAF) {
            result = selfChildArray;
        } else {
            for (int i = 0; i < selfChildArray.length; ) {
                childPos = getBytesPart(selfChildArray, i, 8);
                if (childPos.length > 0) {
                    //map+array[pos+hash]
                    result = Bytes.concat(result, childPos, getHash(Longs.fromByteArray(childPos)));
                }
                i = i + 8;
            }
        }
        if (typeAndKeySize[1] > 0)
            return Bytes.concat(typeAndKeySize, keyNode, childMap, result);
        else
            return Bytes.concat(typeAndKeySize, childMap, result);
    }

    private byte[] getOldestNodeAge(byte[] oldestNodeAge, byte type, byte[] childArray) throws IOException {

        for(int i = 0; i < (type==LEAF? childArray.length/2: childArray.length/8);) {
            byte[] pos = getBytesPart(childArray, i, (type==LEAF? 2 : 8));
            if (pos.equals(new byte[(type == LEAF ? 2 : 8)]))
                continue;

            byte[] childNodeAge=new byte[2];

            if(type==LEAF){
                childNodeAge = pos;
            }else{
                app.trie.seek(Longs.fromByteArray(pos));
                app.trie.read(childNodeAge, 0,2);
            }

            oldestNodeAge =(Utils.compareDate(Utils.reconstructAgeFromBytes(oldestNodeAge), Utils.reconstructAgeFromBytes(childNodeAge)) > 0 ? childNodeAge: oldestNodeAge);
        }
        return oldestNodeAge;
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

    //return pos or age or null - if not found
    private byte[] search(byte[] key, long pos) throws IOException {
        //Если размер файла == 0  то вернем null
        if(app.trie.length()>0) {
            //если это корень то переместим курсор на позицию в массиве детей = первый байт ключа * 8 - 8
            //если содержимое != 0 то  начинаем искать там и вернем то что нашли + корень, иначе вернем корень
            if (pos == 0L) {
                app.trie.seek(22 + getChildPosInArray((key[0]&0xFF), BRANCH));
                byte[] childPos = new byte[8];
                app.trie.read(childPos, 0, 8);
                if (Longs.fromByteArray(childPos) != 0) {
                    return childPos;
                }
            } else {
                //иначе переместимся на позицию pos пропустим возраст
                app.trie.seek(2 + pos);
                //прочитаем ключ, карту дочерей, число дочерей, суфикс вносимого ключа
                byte type = app.trie.readByte();
                byte keyNodeSize = app.trie.readByte();
                byte[] keyNode = new byte[keyNodeSize];
                app.trie.read(keyNode, 0, keyNodeSize);
                app.trie.seek(pos + 4 + keyNodeSize + 20); //skip hash
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
                        app.trie.seek(pos + 4 + keyNodeSize + 20 + 32 + ((childPosInMap * 8L) - 8));
                        result = new byte[8];
                        app.trie.read(result, 0, 8);

                    }else {                  //ret age
                        childPosInMap = getChildPosInMap(childsMap, (suffixKey[0] & 0xFF));
                        app.trie.seek(pos + 4 + keyNodeSize + 20 + 32 + ((childPosInMap * 2L) - 2));
                        result = new byte[2];
                        app.trie.read(result, 0, 2);
                    }
                    return result;
                }
            }
        }
        return null;
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
                    app.trie.seek(0);
                    byte[] nodeAge = new byte[2];
                    app.trie.read(nodeAge, 0, 2);

                    app.trie.seek(22 + getChildPosInArray((key[0] & 0xFF), BRANCH));
                    app.trie.write(posBytes);
                    app.trie.seek(22);
                    byte[] childArray = new byte[2048];
                    app.trie.read(childArray, 0, 2048);
                    hash = calcHash(ROOT, childArray);
                    app.trie.seek(0);
                    byte[] oldestNodeAge = getOldestNodeAge(nodeAge, ROOT, childArray);
                    if(Utils.compareDate(Utils.reconstructAgeFromBytes(nodeAge), Utils.reconstructAgeFromBytes(oldestNodeAge)) > 0)
                        app.trie.write(oldestNodeAge);
                    app.trie.write(hash);
                    return hash;
                }
            }else{
                app.trie.seek(pos);
                byte[] nodeAge = new byte[2];
                app.trie.read(nodeAge, 0, 2);

                byte type = app.trie.readByte();
                byte keyNodeSize = app.trie.readByte();

                typeAndKeySize[0] = type;
                typeAndKeySize[1] = keyNodeSize;

                byte[] keyNode = new byte[keyNodeSize];
                app.trie.read(keyNode, 0, keyNodeSize);
                app.trie.seek(pos + 4 + keyNodeSize + 20); //skip hash
                app.trie.read(childsMap, 0, 32);
                byte[] suffixKey = getBytesPart(key, keyNodeSize, key.length - keyNodeSize);

                int selfChildsCount = getChildsCountInMap(childsMap);
                int selfChildArraySize = selfChildsCount * (type == LEAF ? 2 : 8);

                if(selfChildsCount==1){ //jast delete this node and return zero
                    //insert free space in db
                    app.addPosInFreeSpaceMap(pos, keyNodeSize, selfChildArraySize);
                    return new byte[8];

                }else {
                    app.trie.seek(pos);
                    app.trie.read(nodeAge, 0, 2);

                    app.trie.seek(pos + 4 + keyNodeSize + 20 + 32);
                    byte[] childArray = new byte[selfChildArraySize];
                    app.trie.read(childArray, 0, selfChildArraySize);
                    int insByte = (type==LEAF ? (key[key.length-1]&0xFF) :(suffixKey[0]&0xFF));

                    if (type != LEAF) {//delete leaf child and copy leaf to new place
                        int childPosInMap = getChildPosInMap(childsMap, insByte);
                        long posToDelete = pos + 4 + keyNodeSize + 20 + 32 + ((childPosInMap * 8L) - 8);
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
                                app.trie.read(nodeAge, 0, 2);
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
                                byte[] oldestNodeAge = getOldestNodeAge(nodeAge, typeAndKeySize[0], newChildArray);
                                return Longs.toByteArray(addRecordInFile(oldestNodeAge, typeAndKeySize, (keyNode.length > 0 && oldKeyNode.length > 0 ? Bytes.concat(keyNode, oldKeyNode) : (keyNode.length > 0 ? keyNode : oldKeyNode)), hash, childsMap, newChildArray));
                            }else {//jast copy node in new place
                                byte[] oldestNodeAge = getOldestNodeAge(nodeAge, BRANCH, childArray);
                                return Longs.toByteArray(addRecordInFile(oldestNodeAge, typeAndKeySize, keyNode, hash, childsMap, childArray));
                            }
                        }else{//changed child node
                            app.trie.seek(posToDelete);
                            app.trie.write(chPos);
                            app.trie.seek(pos + 4 + keyNodeSize + 20 + 32);
                            childArray = new byte[selfChildArraySize];
                            app.trie.read(childArray, 0, selfChildArraySize);
                            hash=calcHash(type, childArray);
                            byte[] oldestNodeAge = getOldestNodeAge(nodeAge, BRANCH, childArray);
                            app.trie.seek(pos);
                            app.trie.write(oldestNodeAge);
                            app.trie.seek(pos + 4 + keyNodeSize);
                            app.trie.write(hash);
                            return Longs.toByteArray(pos);
                        }
                    }else{ // for leaf jast delete age and copy node to new place
                        int chp= getChildPosInMap(childsMap, insByte);
                        //delete in map
                        childsMap = changeChildInMap(childsMap, insByte, false);
                        byte[] before=(chp == 0 ? new byte[0] : getBytesPart(childArray,0,  (chp-1)*2));
                        childArray = Bytes.concat(before, getBytesPart(childArray, before.length, childArray.length-before.length));
                        hash=calcHash(type, childsMap);
                        byte[] oldestNodeAge = getOldestNodeAge(nodeAge, LEAF, childArray);
                        app.addPosInFreeSpaceMap(pos, keyNodeSize, selfChildArraySize);
                        return Longs.toByteArray(addRecordInFile(oldestNodeAge, typeAndKeySize, keyNode, hash, childsMap, childArray));
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
            byte[] trieTmp = new byte[2070];
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
                hash=calcHash(typeAndKeySize[0], childsMap);
                pos = addRecordInFile(age, typeAndKeySize, lKey, hash, childsMap, age);
            }else{//insert in child & save in root
                lKey = getBytesPart(key, 1, key.length - 1);
                pos = Longs.fromByteArray(insert(lKey, age, Longs.fromByteArray(sResult)));
            }
            //save in root
            //if node-age is younger inserted age then change nodeAge to age
            app.trie.seek(0);
            byte[] nodeAge = new byte[2];
            app.trie.read(nodeAge, 0, 2);
            if(Utils.compareDate(Utils.reconstructAgeFromBytes(nodeAge), Utils.reconstructAgeFromBytes(age))>0) {
                app.trie.seek(0);
                app.trie.write(age);
            }
            app.trie.seek(22+ getChildPosInArray((key[0]&0xFF), BRANCH));
            app.trie.write(Longs.toByteArray(pos));
            app.trie.seek(22);
            byte[] childArray= new byte[2048];
            app.trie.read(childArray,0,2048);
            hash=calcHash(ROOT, childArray);
            app.trie.seek(2);
            app.trie.write(hash);
            return hash;

        }else {
            if (sResult != null) {
                app.trie.seek(pos);
                byte[] nodeAge = new byte[2];
                app.trie.read(nodeAge, 0, 2);

                byte type = app.trie.readByte();
                byte keyNodeSize = app.trie.readByte();
                byte[] keyNode = new byte[keyNodeSize];
                app.trie.read(keyNode, 0, keyNodeSize);
                app.trie.seek(pos + 4 + keyNodeSize + 20); //skip hash
                app.trie.read(childsMap, 0, 32);
                //Получим суффикс
                byte[] suffixKey = getBytesPart(key, keyNodeSize, key.length - keyNodeSize);

                int selfChildsCount = getChildsCountInMap(childsMap);
                int selfChildArraySize = selfChildsCount * (type==LEAF ? 2 : 8);

                int childPosInMap = getChildPosInMap(childsMap, (suffixKey[0] & 0xFF));
                if(childPosInMap==0){
                    Log.d("app.trie", String.valueOf((suffixKey[0] & 0xFF)));
                }
                long posToWrite = pos + 4 + keyNodeSize + 20 + 32 + (type==LEAF ? ((childPosInMap * 2L) - 2) : ((childPosInMap * 8L) - 8));
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

                //if node-age is younger inserted age then change nodeAge to age
                if(Utils.compareDate(Utils.reconstructAgeFromBytes(nodeAge), Utils.reconstructAgeFromBytes(age))>0) {
                    app.trie.seek(pos);
                    app.trie.write(age);
                }
                app.trie.seek(pos + 4 + keyNodeSize + 20 + 32);
                byte[] childArray = new byte[selfChildArraySize];
                app.trie.read(childArray, 0, selfChildArraySize);
                hash = calcHash(type, type==LEAF ? childsMap : childArray);
                app.trie.seek(pos + 4 + keyNodeSize);
                app.trie.write(hash);
                return Longs.toByteArray(pos);
            } else {

                app.trie.seek(pos);
                byte[] nodeAge = new byte[2];
                app.trie.read(nodeAge, 0, 2);
                //прочитаем ключ, карту детей, число детей, суфикс префикс
                byte type = app.trie.readByte();
                byte keyNodeSize = app.trie.readByte();

                byte[] keyNode = new byte[keyNodeSize];
                app.trie.read(keyNode, 0, keyNodeSize);

                app.trie.seek(pos + 4 + keyNodeSize + 20); //skip hash
                app.trie.read(childsMap, 0, 32);
                //Получим префикс и суффикс искомого ключа
                byte[] preffixKey = getBytesPart(key, 0, keyNodeSize);

                byte[] suffixKey = getBytesPart(key, keyNodeSize, key.length - keyNodeSize);
                byte[] commonKey = getCommonKey(keyNode, preffixKey);

                int selfChildsCount = getChildsCountInMap(childsMap);
                int selfChildArraySize = selfChildsCount*(type==LEAF ? 2 : 8);

                byte[] selfChildArray= new byte[selfChildArraySize];
                app.trie.seek(pos + 4 + keyNodeSize + 20 + 32);//go to childArray
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
                    hash=calcHash(typeAndKeySize[0], childsMapNew);
                    long posLeaf = addRecordInFile(age, typeAndKeySize, leafKey_, hash, childsMapNew, age);

                    //COPY OLD NODE WITCH CORP(keyNode - common) KEY
                    byte[] oldLeafKey = getBytesPart(keyNode, commonKey.length , keyNodeSize - commonKey.length);
                    byte[] oldLeafKey_ = getBytesPart(oldLeafKey, 1 , oldLeafKey.length - 1);
                    typeAndKeySize[0] = type;
                    typeAndKeySize[1] = (byte)oldLeafKey_.length;
                    hash=calcHash(type, type==LEAF ? childsMap : selfChildArray);

                    long posOldLeaf = addRecordInFile(nodeAge, typeAndKeySize, oldLeafKey_, hash, childsMap, selfChildArray);

                    //CREATE NEW BRANCH WITCH COMMON KEY AND CONTENT = NEW LEAF POSITION + OLD NODE POSITION
                    typeAndKeySize[0] = BRANCH;
                    typeAndKeySize[1] = (byte)commonKey.length;
                    if (leafKey[0]>oldLeafKey[0]){
                        childArray = Bytes.concat(Longs.toByteArray(posOldLeaf), Longs.toByteArray(posLeaf));
                    }else{
                        childArray = Bytes.concat(Longs.toByteArray(posLeaf), Longs.toByteArray(posOldLeaf));
                    }
                    hash=calcHash(BRANCH, childArray);

                    retPos = Longs.toByteArray(addRecordInFile(
                            (Utils.compareDate(Utils.reconstructAgeFromBytes(nodeAge), Utils.reconstructAgeFromBytes(age))>0 ? age : nodeAge),
                            typeAndKeySize,
                            commonKey,
                            hash,
                            changeChildInMap(changeChildInMap(new byte[32], (leafKey[0]&0xFF),true), (oldLeafKey[0]&0xFF),true),
                            childArray)
                    );

                }else{//if is Leaf add age in node, else create leaf witch suffix key and add pos in node(branch)
                    long posLeaf=0L;
                    byte[] leafKey;
                    int insByte;
                    if(type!=LEAF){
                        typeAndKeySize[0] = LEAF;
                        leafKey = getBytesPart(suffixKey, 1 , suffixKey.length - 1);
                        insByte = (suffixKey[0]&0xFF);
                        typeAndKeySize[1] = (byte)leafKey.length;
                        childsMapNew = changeChildInMap(new byte[32], (key[key.length-1]&0xFF),true);
                        hash=calcHash(LEAF, childsMapNew);
                        posLeaf = addRecordInFile(age, typeAndKeySize, leafKey, hash, childsMapNew, age);
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
                    hash=calcHash(type, type==LEAF ? childsMap : childArray);

                    retPos = Longs.toByteArray(addRecordInFile(
                            (Utils.compareDate(Utils.reconstructAgeFromBytes(nodeAge), Utils.reconstructAgeFromBytes(age))>0 ? age : nodeAge),
                            typeAndKeySize, keyNode, hash, childsMap, childArray));

                }
                return retPos;
            }
        }
    }

    private byte[] getHash(long pos) throws IOException {

        byte[] hash = new byte[20];
        if (pos == 0L) {
            app.trie.seek(pos+2);
        } else {
            app.trie.seek(pos+3);
            byte keySize = app.trie.readByte();
            app.trie.seek(pos+ 4 + keySize);
        }
        app.trie.read(hash, 0, 20);
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

    private long addRecordInFile(byte[] age, byte[] typeAndKeySize, byte[] key, byte[] hash, byte[] childsMap, byte[] childArray) throws IOException {
        byte[] record;
        if(typeAndKeySize[1]==0){
            record = Bytes.concat(age, typeAndKeySize, hash, childsMap, childArray);
        }else{
            record = Bytes.concat(age, typeAndKeySize, key, hash, childsMap, childArray);
        }
        Cursor query = app.getFreeSpace(record.length);
        app.trie.seek(app.trie.length());
        long pos = app.trie.getFilePointer();
        if (query.getCount() > 0 && query.moveToFirst()) {
            int posColIndex = query.getColumnIndex("pos");
            int spaceColIndex = query.getColumnIndex("space");
            long p = query.getLong(posColIndex);
            int s = query.getInt(spaceColIndex);
            if( p > 0 ) {
                app.deleteFreeSpace(p, record.length, s);
                app.trie.seek(p);
                pos=p;
            }
        }
        query.close();

        app.trie.write(record);
        return pos;
    }

    private void optimize() {
        Cursor query = app.getFreeSpaceWitchCompress();
        int s, ss;
        long p, sp;
        if (query.getCount() > 0) {
            while (query.moveToNext()) {
                p = query.getLong(query.getColumnIndex("pos"));
                s = query.getInt(query.getColumnIndex("space"));
                sp = query.getLong(query.getColumnIndex("Second_pos"));
                ss = query.getInt(query.getColumnIndex("Second_space"));
                Cursor checkExistQueryP = app.checkExistFreeSpace(p);
                Cursor checkExistQuerySP = app.checkExistFreeSpace(sp);
                if (checkExistQueryP.getCount() > 0 && checkExistQuerySP.getCount() > 0) {
                    app.insertFreeSpaceWitchCompressTrieFile(p, s, sp, ss);
                }
            }
            query.close();
            optimize();
        }
        query.close();
    }

    private boolean deleteOldest(long pos, byte[] key) throws IOException {
        Context context = app.getApplicationContext();
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        long maxAge = Long.parseLong(sharedPref.getString("maxAge", "30"));
        byte[] nodeAge = new byte[2];
        app.trie.seek(pos);
        app.trie.read(nodeAge, 0, 2);
        if (Utils.compareDate(new Date(), Utils.reconstructAgeFromBytes(nodeAge)) > maxAge) {
            if (pos == 0L) {
                byte[] childPos = new byte[8];
                app.trie.seek(54);
                for(int i=0;i==255;i++) {
                    app.trie.seek(pos + (i*8));
                    app.trie.read(childPos, 0, 8);
                    if (Longs.fromByteArray(childPos) != 0) {
                        key[0] = (byte) i;
                        if(deleteOldest(Longs.fromByteArray(childPos), key)) return true;
                    }
                }
            }else{
                byte[] childsMap = new byte[32];
                byte type = app.trie.readByte();
                byte keyNodeSize = app.trie.readByte();
                byte[] keyNode = new byte[keyNodeSize];
                app.trie.read(keyNode, 0, keyNodeSize);
                byte[] fullKey = Bytes.concat(key, keyNode);
                app.trie.seek(pos + 4 + keyNodeSize + 20); //skip hash
                app.trie.read(childsMap, 0, 32);
                int childPosInMap;
                for(int i=0;i==255;i++) {
                    childPosInMap = getChildPosInMap(childsMap, i);
                    if(childPosInMap>0) {
                        byte[] sKey=new byte[1];
                        sKey[0] = (byte) i;
                        app.trie.seek(pos + 4 + keyNodeSize + 20 + 32 + (((long) childPosInMap * (type == LEAF ? 2 : 8)) - (type == LEAF ? 2 : 8)));
                        byte[] childPos = new byte[(type == LEAF ? 2 : 8)];
                        app.trie.read(childPos, 0, (type == LEAF ? 2 : 8));
                        if(type==LEAF){
                            if (Utils.compareDate(new Date(), Utils.reconstructAgeFromBytes(childPos)) > maxAge) {
                                delete(Bytes.concat(fullKey, sKey), 0L);
                                return true;
                            }
                        }else{
                            if(deleteOldest(Longs.fromByteArray(childPos), Bytes.concat(keyNode, sKey))) return true;
                        }
                    }
                }
            }
        }
        return false;
    }

}
