package com.infoman.liquideconomycs;

import android.annotation.TargetApi;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;
import com.google.common.primitives.Shorts;

import org.bitcoinj.core.SignatureDecodeException;

import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import static androidx.core.app.NotificationCompat.PRIORITY_LOW;
import static com.infoman.liquideconomycs.Utils.ACTION_DELETE;
import static com.infoman.liquideconomycs.Utils.ACTION_FIND;
import static com.infoman.liquideconomycs.Utils.ACTION_GENERATE_ANSWER;
import static com.infoman.liquideconomycs.Utils.ACTION_GET_HASH;
import static com.infoman.liquideconomycs.Utils.ACTION_INSERT;
import static com.infoman.liquideconomycs.Utils.BRANCH;
import static com.infoman.liquideconomycs.Utils.BROADCAST_ACTION_ANSWER;
import static com.infoman.liquideconomycs.Utils.ACTION_STOP_SERVICE;
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
import static com.infoman.liquideconomycs.Utils.getChildPosInROOT;
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

    @Override
    public void onCreate() {
        super.onCreate();
        //android.os.Debug.waitForDebugger();

        app = (Core) getApplicationContext();
        Log.d("app.trie", "Create!"+app.waitingIntentCount);
        if(app.waitingIntentCount !=0) return;

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
        ////////////////////////////////////////////////////////////////

        String channel;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            channel = createChannel();
        else {
            channel = "";
        }

        NotificationCompat.Builder builder = null; // display indeterminate progress
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            builder = new NotificationCompat.Builder(getBaseContext(), channel)
                    .setTicker("TrieIntent") // use something from something from R.string
                    .setContentTitle("LE trie service") // use something from something from
                    .setContentText("Sync trie") // use something from something from
                    .setProgress(0, 0, true)
                    .setPriority(PRIORITY_LOW)
                    .setCategory(Notification.CATEGORY_SERVICE);
        }

        assert builder != null;
        startForeground(9992, builder.build());
    }

    public void onDestroy() {
        super.onDestroy();
        Log.d("app.trie", "destroy!"+app.waitingIntentCount);
        // cancel any running threads here
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        app.waitingIntentCount++;
        Log.d("app.trie", "start!"+app.waitingIntentCount+" "+intent.getAction());
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {

            app.waitingIntentCount--;
            Log.d("app.trie", "onHandleIntent!"+app.waitingIntentCount+" "+intent.getAction());
            final String action = intent.getAction();
            if (ACTION_GET_HASH.equals(action)) {
                final String master = intent.getStringExtra(EXTRA_MASTER), cmd = "GetHash";
                final long pos = intent.getLongExtra(EXTRA_POS,0L);
                ////////////////////////////////////////////////////////////////
                try {
                    app.broadcastActionMsg(master, cmd, getHash(pos));
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
                    app.broadcastActionMsg(master, cmd, insert(key, value, 0L, new byte[0]));
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
                    app.broadcastActionMsg(master, cmd, find(key, pos));
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
                    app.broadcastActionMsg(master, cmd, delete(key, pos));
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
                    app.broadcastActionMsg("Trie", "Answer", generateAnswer(msgType, payload));
                } catch (IOException | SignatureDecodeException e) {
                    e.printStackTrace();
                }
                ////////////////////////////////////////////////////////////////
            }

            if (ACTION_STOP_SERVICE.equals(action)) {
                try {
                    while(app.waitingIntentCount!=0){}
                    app.waitingIntentCount++;
                    //get Oldest Key
                    deleteOldest(0L, new byte[1]);

                    app.optimize();

                    Cursor query = app.getPubKeysForDelete();
                    while (query.moveToNext()) {
                        int pubKeyColIndex = query.getColumnIndex("pubKey");
                        delete(query.getBlob(pubKeyColIndex), 0L);
                    }
                    query.close();

                    app.clearTableForDelete();

                    app.optimize();

                    app.waitingIntentCount--;
                } catch (IOException e) {
                    e.printStackTrace();
                }
                app.clearPrefixTable();
                stopSelf();
                stopForeground(true);
                ////////////////////////////////////////////////////////////////
            }

        }
    }

    //todo add age sort for sync priority
    //todo изолировать запись
    private byte[] generateAnswer(byte msgType, byte[] payload) throws IOException, SignatureDecodeException {
        Context context = app.getApplicationContext();
        boolean exist, existSelfChild;
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
        }else{
            //payload = pos+typeAndKeySize+keyNode+childMap+childArray[pos+hash(BRANCH\ROOT)... or age(LEAF)...]
            //если ранее запрашивался корень то сравним с хешем нашего корня и если совпадает то ничего не делаем
            //если нет то построим карту своего корня и пройдемся по узлам как обычно
            //нам прислали рание запрошенные узлы, необходимо их расшифровать
            for(int i = 0; i < payload.length;) {
                //node from payload
                exist                       = false;
                long pos                    = Longs.fromByteArray(Utils.getBytesPart(payload, i, 8));
                byte[] nodeTypeAndKeySize   = Utils.getBytesPart(payload, i+8, 2);
                int offLen                  = (nodeTypeAndKeySize[0]!=LEAF?20:2);
                byte[] key                  = Utils.getBytesPart(payload, i + 10, nodeTypeAndKeySize[1]);
                byte[] childsMap            = Utils.getBytesPart(payload, i + 10 + nodeTypeAndKeySize[1], 32);
                int childsCountInMap        = Utils.getChildsCountInMap(childsMap);
                int len                     = childsCountInMap * (nodeTypeAndKeySize[0]==Utils.LEAF ? 2 : 28);
                byte[] childsArray          = Utils.getBytesPart(payload, i + 10 + nodeTypeAndKeySize[1] + 32, len);
                //next index of data-part in payload
                i                           = i + 10 + nodeTypeAndKeySize[1] + 32 + len;
                //self node
                byte[] selfNodePos= new byte[8],
                        selfPrefix = new byte[0],
                        selfNodeMapAndHashOrAge,
                        selfTypeAndKeySize = new byte[2],
                        selfNodeMap = new byte[32],
                        selfNodeHashsOrAges = new byte[0];
                if (nodeTypeAndKeySize[0]== ROOT){
                    selfNodeMapAndHashOrAge = getNodeMapAndHashesOrAges(selfNodePos);
                    selfTypeAndKeySize = Utils.getBytesPart(selfNodeMapAndHashOrAge, 0, 2);
                    selfNodeMap = Utils.getBytesPart(selfNodeMapAndHashOrAge, 2, 32);
                    selfNodeHashsOrAges = Utils.getBytesPart(selfNodeMapAndHashOrAge, 2 + 32, selfNodeMapAndHashOrAge.length - (2 + 32));
                }else {
                    //Получим полный префикс ключа в предидущем цикле перед запросом
                    //Префикс нарастает по мере движения в глюбь дерева

                    Cursor query = app.getPrefixByPos(pos);
                    if (query.getCount() > 0) {
                        while (query.moveToNext()) {
                            selfPrefix = query.getBlob(query.getColumnIndex("prefix"));
                            exist = query.getInt(query.getColumnIndex("exist")) == 1;
                        }
                    }
                    query.close();
                    // Если запрошеный узел у нас есть, то на основании префикса получим карту и детей узла с этим префиксом
                    //получить позицию узла в дереве по префиксу
                    if (exist) {
                        for (int k = 0; k < selfPrefix.length; k++) {
                            selfNodePos = searchPos(selfPrefix[k], selfNodePos);
                        }
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
                }
                //todo В цикле  от 0 - 255 мы должны обойти все дочерние узлы
                // 1) Если узел\возраст есть в полученой карте:
                // 1.1) Если это тип BRANCH\ROOT и (узел в карте имеет хеш не равный нашему или дочерний узел не найден в нашей карте) то
                // внести в базу (prefix + индекс цикла - позиция в карте) и позицию, добавить позицию в следующий запрос
                // 1.2) Если это тип LEAF и ((узел в карте имеет возраст не равный нашему  и возраст моложе нашего ) или возраст не найден в нашей карте)
                // то добавить/изменить по(prefix + индекс цикла - позиция в карте) новый возраст
                byte[] ask = new byte[0];

                for(int c = 0; c < 256; c++){
                    byte[] c_ = new byte[1];
                    c_[0] = (byte)c;
                    if (!checkExistChildInMap(childsMap, c)) {
                        continue;
                    }
                    byte[] newPrefix;
                    if(exist || selfTypeAndKeySize[0] == ROOT) {
                        newPrefix = selfTypeAndKeySize[0] != ROOT ? Bytes.concat(selfPrefix, key, c_) : c_;
                        existSelfChild = checkExistChildInMap(selfNodeMap, c);
                        if (nodeTypeAndKeySize[0] == LEAF) {
                            byte[] childAge = Utils.getBytesPart(childsArray, (getChildPosInMap(childsMap, c) * offLen) - offLen, offLen);
                            if (!existSelfChild ||
                                    (!Arrays.equals(childAge, Utils.getBytesPart(selfNodeHashsOrAges, (getChildPosInMap(selfNodeMap, c) * offLen) - offLen, offLen))
                                            && Utils.compareDate(Utils.reconstructAgeFromBytes(childAge), Utils.reconstructAgeFromBytes(Utils.getBytesPart(selfNodeHashsOrAges, (getChildPosInMap(selfNodeMap, c) * offLen) - offLen, offLen))) > 0L)
                            ) {
                                //todo add check newPrefix length
                                SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
                                long maxAge = Long.parseLong(sharedPref.getString("maxAge", "30"));
                                if (Utils.compareDate(new Date(), Utils.reconstructAgeFromBytes(childAge)) < maxAge && Utils.compareDate(new Date(), Utils.reconstructAgeFromBytes(childAge)) >= 0L)
                                    app.startActionInsert(context, "Main", newPrefix, childAge);

                            }
                        } else {
                            if (!existSelfChild || !Arrays.equals(
                                    Utils.getBytesPart(childsArray, (getChildPosInMap(childsMap, c) * 28) - offLen, offLen),
                                    Utils.getBytesPart(selfNodeHashsOrAges, (getChildPosInMap(selfNodeMap, c) * 28) - offLen, offLen)
                            )) {
                                //todo add to table sync add to list new ask
                                long pos_ = Longs.fromByteArray(Utils.getBytesPart(childsArray, (getChildPosInMap(childsMap, c) * 28) - 28, 8));
                                app.addPrefixByPos(pos_, newPrefix, null, existSelfChild);
                                ask = Bytes.concat(ask, Longs.toByteArray(pos_));
                            }
                        }
                    }else{
                        //если это новый узел то если это ветвь то продолжаем запросы
                        if(nodeTypeAndKeySize[0] != LEAF) {
                            long pos_ = Longs.fromByteArray(Utils.getBytesPart(childsArray, (getChildPosInMap(childsMap, c) * 28) - 28, 8));
                            app.addPrefixByPos(pos_, Bytes.concat(selfPrefix, key, c_), null, false);
                            ask = Bytes.concat(ask, Longs.toByteArray(pos_));
                        }else{
                            // иначе добавляем новые ключи с возрастами
                            byte[] childAge = Utils.getBytesPart(childsArray, (getChildPosInMap(childsMap, c) * offLen) - offLen, offLen);
                            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
                            long maxAge = Long.parseLong(sharedPref.getString("maxAge", "30"));
                            //todo add check newPrefix length
                            if (Utils.compareDate(new Date(), Utils.reconstructAgeFromBytes(childAge)) < maxAge && Utils.compareDate(new Date(), Utils.reconstructAgeFromBytes(childAge)) >= 0L){
                                app.startActionInsert(context, "Main", Bytes.concat(selfPrefix, key, c_), childAge);
                                Log.d("app.trie update ", String.valueOf(Bytes.concat(selfPrefix,key, c_)));
                            }

                        }
                    }
                }
                byte[] type = new byte[1];
                type[0] = Utils.getHashs;
                app.broadcastActionMsg("Trie", "Answer", Bytes.concat(type, ask));
            }
        }
        return answer;
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
                childPos = getBytesPart(selfChildArray, i*8, 8);
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

        for(int i = 0; i < (type==LEAF? childArray.length/2: childArray.length/8);i++) {
            byte[] pos = getBytesPart(childArray, i*(type==LEAF? 2 : 8), (type==LEAF? 2 : 8));
            if (type == LEAF ? Shorts.fromByteArray(pos) == 0 : Longs.fromByteArray(pos)== 0L)
                continue;

            byte[] childNodeAge=new byte[2];

            if(type==LEAF){
                childNodeAge = pos;
            }else{
                app.trie.seek(Longs.fromByteArray(pos));
                app.trie.read(childNodeAge, 0,2);
            }

            oldestNodeAge =(Utils.compareDate(Utils.reconstructAgeFromBytes(oldestNodeAge), Utils.reconstructAgeFromBytes(childNodeAge)) > 0L ? childNodeAge: oldestNodeAge);
        }
        return oldestNodeAge;
    }

    //return null if not found or (pos or age) if found
    private byte[] find(byte[] key, long pos) throws IOException {
        byte[] s=search(key, pos);

        if (s!=null && s.length>2 && key.length>1){
            return find(getBytesPart(key, 1, key.length - 1), Longs.fromByteArray(s));
        }else return s;
    }

    //return pos or age or null - if not found
    private byte[] search(byte[] key, long pos) throws IOException {
        //Если размер файла == 0  то вернем null
        if(app.trie.length()>0) {
            //если это корень то переместим курсор на позицию в массиве детей = первый байт ключа * 8 - 8
            //если содержимое != 0 то  начинаем искать там и вернем то что нашли + корень, иначе вернем корень
            if (pos == 0L) {
                app.trie.seek(22 + getChildPosInROOT((key[0]&0xFF)));
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
                    int childPosInMap;
                    byte[] result;
                    childPosInMap = getChildPosInMap(childsMap, (suffixKey[0] & 0xFF));
                    if(type==BRANCH) {//ret pos
                        app.trie.seek(pos + 4 + keyNodeSize + 20 + 32 + ((childPosInMap * 8L) - 8));
                        result = new byte[8];
                        app.trie.read(result, 0, 8);

                    }else {                  //ret age
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

    //return pos or age or null - if not found
    private byte[] searchPos(byte key, byte[] sPos) throws IOException {
        long pos = Longs.fromByteArray(sPos);
        byte[] result = null;
        //Если размер файла == 0  то вернем null
        if(app.trie.length()>0) {
            //если это корень то переместим курсор на позицию в массиве детей = первый байт ключа * 8 - 8
            //если содержимое != 0 то  начинаем искать там и вернем то что нашли + корень, иначе вернем корень
            byte[] childPos = new byte[8];
            if (pos == 0L) {
                app.trie.seek(22 + getChildPosInROOT(key&0xFF));
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
                int childPosInMap = getChildPosInMap(childsMap, key&0xFF);

                if(type==BRANCH) {//ret pos
                    app.trie.seek(pos + 4 + keyNodeSize + 20 + 32 + ((childPosInMap * 8L) - 8));
                }
            }
            app.trie.read(childPos, 0, 8);
            if (Longs.fromByteArray(childPos) != 0) {
                result = childPos;
            }
        }
        return result;
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
                if (posBytes == null)
                    posBytes = new byte[8];
                //todo изолировать запись
                app.trie.seek(0);
                byte[] nodeAge = new byte[2];
                app.trie.read(nodeAge, 0, 2);

                app.trie.seek(22 + getChildPosInROOT((key[0] & 0xFF)));
                app.trie.write(posBytes);
                app.trie.seek(22);
                byte[] childArray = new byte[2048];
                app.trie.read(childArray, 0, 2048);
                hash = calcHash(ROOT, new byte[0], childArray);
                app.trie.seek(0);
                byte[] oldestNodeAge = getOldestNodeAge(nodeAge, ROOT, childArray);
                if(Utils.compareDate(Utils.reconstructAgeFromBytes(nodeAge), Utils.reconstructAgeFromBytes(oldestNodeAge)) > 0L)
                    app.trie.write(oldestNodeAge);
                app.trie.seek(2);
                app.trie.write(hash);
                return hash;
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

                }else{//if childs > 1
                    app.trie.seek(pos + 4 + keyNodeSize + 20 + 32);
                    byte[] childArray = new byte[selfChildArraySize];
                    app.trie.read(childArray, 0, selfChildArraySize);
                    int delByte = (type==LEAF ? (key[key.length-1]&0xFF) :(suffixKey[0]&0xFF));

                    if (type != LEAF) {//for BRANCH
                        int childPosInMap = getChildPosInMap(childsMap, delByte);
                        long posToDelete = pos + 4 + keyNodeSize + 20 + 32 + ((childPosInMap * 8) - 8);
                        app.trie.seek(posToDelete);
                        byte[] chPos = new byte[8];
                        app.trie.read(chPos, 0, 8);
                        //delete in child
                        chPos = delete(getBytesPart(suffixKey, 1, suffixKey.length - 1), Longs.fromByteArray(chPos));
                        if(chPos == null || Longs.fromByteArray(chPos) == 0L){//deleted child node
                            int chp = getChildPosInMap(childsMap, delByte);
                            //delete in map
                            childsMap = changeChildInMap(childsMap, delByte, false);
                            //delete in array
                            byte[] before = (chp == 0 ? new byte[0] : getBytesPart(childArray, 0, (chp - 1) * 8));
                            childArray = Bytes.concat(before, getBytesPart(childArray, (chp * 8), childArray.length - (chp * 8)));

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
                                app.trie.seek(pos + 4 + oldKeySize + 20);
                                app.trie.read(childsMap, 0, 32);
                                selfChildsCount = getChildsCountInMap(childsMap);
                                selfChildArraySize = selfChildsCount * (typeAndKeySize[0] == LEAF ? 2 : 8);
                                byte[] newChildArray = new byte[selfChildArraySize];
                                app.trie.read(newChildArray, 0, selfChildArraySize);
                                hash = calcHash(typeAndKeySize[0],
                                        (keyNode.length > 0 && oldKeyNode.length > 0 ? Bytes.concat(keyNode, oldKeyNode) : (keyNode.length > 0 ? keyNode : oldKeyNode)),
                                        typeAndKeySize[0]==LEAF ? Bytes.concat(childsMap, newChildArray) : newChildArray);
                                app.addPosInFreeSpaceMap(pos, oldKeySize, selfChildArraySize);

                                byte[] oldestNodeAge = getOldestNodeAge(nodeAge, typeAndKeySize[0], newChildArray);
                                return Longs.toByteArray(addRecordInFile(oldestNodeAge, typeAndKeySize, (keyNode.length > 0 && oldKeyNode.length > 0 ? Bytes.concat(keyNode, oldKeyNode) : (keyNode.length > 0 ? keyNode : oldKeyNode)), hash, childsMap, newChildArray));
                            }else {//jast copy node in new place
                                // пересчитываем хеш и рекурсивно вносим позицию в вышестоящие узлы
                                hash = calcHash(type, keyNode, childArray);
                                byte[] oldestNodeAge = getOldestNodeAge(nodeAge, BRANCH, childArray);
                                return Longs.toByteArray(addRecordInFile(oldestNodeAge, typeAndKeySize, keyNode, hash, childsMap, childArray));
                            }
                        }else{//changed child node
                            //todo изолировать запись
                            app.trie.seek(posToDelete);
                            app.trie.write(chPos);
                            app.trie.seek(pos + 4 + keyNodeSize + 20 + 32);
                            childArray = new byte[selfChildArraySize];
                            app.trie.read(childArray, 0, selfChildArraySize);
                            hash=calcHash(type, keyNode, childArray);

                            byte[] oldestNodeAge = getOldestNodeAge(nodeAge, BRANCH, childArray);
                            app.trie.seek(pos);
                            app.trie.write(oldestNodeAge);
                            app.trie.seek(pos + 4 + keyNodeSize);
                            app.trie.write(hash);
                            return Longs.toByteArray(pos);
                        }
                    }else{ // for leaf jast delete age and copy node to new place
                        int chp= getChildPosInMap(childsMap, delByte);
                        //delete in map
                        childsMap = changeChildInMap(childsMap, delByte, false);

                        byte[] before=(chp == 0 ? new byte[0] : getBytesPart(childArray,0,  (chp-1)*2));
                        childArray = Bytes.concat(before, getBytesPart(childArray, (chp * 2), childArray.length-(chp * 2)));

                        hash=calcHash(type, keyNode, Bytes.concat(childsMap, childArray));
                        byte[] oldestNodeAge = getOldestNodeAge(nodeAge, LEAF, childArray);
                        app.addPosInFreeSpaceMap(pos, keyNodeSize, selfChildArraySize);

                        return Longs.toByteArray(addRecordInFile(oldestNodeAge, typeAndKeySize, keyNode, hash, childsMap, childArray));
                    }
                }
            }
        }
        return null;
    }

    private byte[] insert(byte[] key, byte[] age, long pos, byte[] fullKey) throws IOException {
        byte[] hash;
        byte[] childsMap = new byte[32];
        byte[] typeAndKeySize = new byte[2];

        if (app.trie.length() == 0) {
            //todo изолировать запись
            app.trie.setLength(0);
            byte[] trieTmp = new byte[2070];
            app.trie.write(trieTmp);
            app.trie.seek(0);
            app.trie.write(age);
            return insert(key, age, pos, fullKey);
        }

        byte[] sResult=search(key, pos);

        if (pos==0L) {
            byte[] lKey;
            if (sResult == null){//Create new LEAF witch age
                lKey = getBytesPart(key, 1, key.length - 2);
                typeAndKeySize[0] = LEAF;
                typeAndKeySize[1] = (byte)lKey.length;
                childsMap = changeChildInMap(new byte[32], (key[key.length-1]&0xFF), true);//add age
                hash=calcHash(typeAndKeySize[0], lKey, Bytes.concat(childsMap, age));
                pos = addRecordInFile(age, typeAndKeySize, lKey, hash, childsMap, age);

            }else{//insert in child & save in root
                lKey = getBytesPart(key, 1, key.length - 1);
                pos = Longs.fromByteArray(insert(lKey, age, Longs.fromByteArray(sResult), new byte[]{key[0]}));
            }
            //save in root
            //if node-age is younger inserted age then change nodeAge to age
            //todo изолировать запись
            app.trie.seek(0);
            byte[] nodeAge = new byte[2];
            app.trie.read(nodeAge, 0, 2);
            if(Utils.compareDate(Utils.reconstructAgeFromBytes(nodeAge), Utils.reconstructAgeFromBytes(age)) > 0L) {
                app.trie.seek(0);
                app.trie.write(age);
            }
            app.trie.seek(22+ getChildPosInROOT((key[0]&0xFF)));
            app.trie.write(Longs.toByteArray(pos));
            app.trie.seek(22);
            byte[] childArray= new byte[2048];
            app.trie.read(childArray,0,2048);
            hash=calcHash(ROOT, new byte[0], childArray);
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

                long posToWrite = pos + 4 + keyNodeSize + 20 + 32 + (type==LEAF ? ((childPosInMap * 2) - 2) : ((childPosInMap * 8) - 8));
                app.trie.seek(posToWrite);
                //todo изолировать запись
                if(type==LEAF){
                    app.trie.write(age);
                }else{
                    byte[] chPos=new byte[8];
                    app.trie.read(chPos, 0, 8);
                    //insert to child
                    chPos = insert(getBytesPart(suffixKey, 1, suffixKey.length-1), age, Longs.fromByteArray(chPos),
                            Bytes.concat(fullKey, keyNode, new byte[]{suffixKey[0]})
                    );
                    app.trie.seek(posToWrite);
                    app.trie.write(chPos);
                }

                //if node-age is younger inserted age then change nodeAge to age
                if(Utils.compareDate(Utils.reconstructAgeFromBytes(nodeAge), Utils.reconstructAgeFromBytes(age)) > 0L) {
                    app.trie.seek(pos);
                    app.trie.write(age);
                }
                app.trie.seek(pos + 4 + keyNodeSize + 20 + 32);
                byte[] childArray = new byte[selfChildArraySize];
                app.trie.read(childArray, 0, selfChildArraySize);
                hash = calcHash(type, keyNode, type==LEAF ? Bytes.concat(childsMap, childArray) : childArray);
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
                    byte[] leafKey = getBytesPart(key, commonKey.length , key.length - commonKey.length - 2);
                    byte[] leafKey_ = getBytesPart(leafKey, 1 , leafKey.length - 1);
                    typeAndKeySize[0] = LEAF;
                    typeAndKeySize[1] = (byte)leafKey_.length;
                    childsMapNew = changeChildInMap(new byte[32], (key[key.length-1]&0xFF),true);
                    hash=calcHash(typeAndKeySize[0], leafKey_, Bytes.concat(childsMapNew, age));
                    long posLeaf = addRecordInFile(age, typeAndKeySize, leafKey_, hash, childsMapNew, age);

                    //COPY OLD NODE WITCH CORP(keyNode - common) KEY
                    byte[] oldLeafKey = getBytesPart(keyNode, commonKey.length , keyNodeSize - commonKey.length);
                    byte[] oldLeafKey_ = getBytesPart(oldLeafKey, 1 , oldLeafKey.length - 1);
                    typeAndKeySize[0] = type;
                    typeAndKeySize[1] = (byte)oldLeafKey_.length;
                    hash=calcHash(type, oldLeafKey_, type==LEAF ? Bytes.concat(childsMap, selfChildArray) : selfChildArray);

                    long posOldLeaf = addRecordInFile(nodeAge, typeAndKeySize, oldLeafKey_, hash, childsMap, selfChildArray);

                    //CREATE NEW BRANCH WITCH COMMON KEY AND CONTENT = NEW LEAF POSITION + OLD NODE POSITION
                    typeAndKeySize[0] = BRANCH;
                    typeAndKeySize[1] = (byte)commonKey.length;
                    if (leafKey[0]>oldLeafKey[0]){
                        childArray = Bytes.concat(Longs.toByteArray(posOldLeaf), Longs.toByteArray(posLeaf));
                    }else{
                        childArray = Bytes.concat(Longs.toByteArray(posLeaf), Longs.toByteArray(posOldLeaf));
                    }
                    hash=calcHash(BRANCH, commonKey, childArray);

                    retPos = Longs.toByteArray(addRecordInFile(
                            (Utils.compareDate(Utils.reconstructAgeFromBytes(nodeAge), Utils.reconstructAgeFromBytes(age)) > 0L ? age : nodeAge),
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
                        leafKey = getBytesPart(suffixKey, 1 , suffixKey.length - 2);
                        insByte = (suffixKey[0]&0xFF);
                        typeAndKeySize[1] = (byte)leafKey.length;
                        childsMapNew = changeChildInMap(new byte[32], (key[key.length-1]&0xFF),true);
                        hash=calcHash(LEAF, leafKey, Bytes.concat(childsMapNew, age));
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
                    hash=calcHash(type, keyNode, type==LEAF ? Bytes.concat(childsMap, childArray) : childArray);

                    retPos = Longs.toByteArray(addRecordInFile(
                            (Utils.compareDate(Utils.reconstructAgeFromBytes(nodeAge), Utils.reconstructAgeFromBytes(age)) > 0L ? age : nodeAge),
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

    private byte[] calcHash(byte type, byte[] digest, byte[] childArray) throws IOException {
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
        return sha256hash160(Bytes.concat(digest,childArray));
    }

    private long addRecordInFile(byte[] age, byte[] typeAndKeySize, byte[] key, byte[] hash, byte[] childsMap, byte[] childArray) throws IOException {
        byte[] record;
        if (age.length<2){
            Log.d("app.trie", "ERROR! age to small");
        }
        if (typeAndKeySize.length<2){
            Log.d("app.trie", "ERROR! typeAndKeySize to small");
        }
        if (hash.length<20){
            Log.d("app.trie", "ERROR! hash to small");
        }
        if (childsMap.length<32){
            Log.d("app.trie", "ERROR! childsMap to small");
        }
        if (getChildsCountInMap(childsMap) != childArray.length/(typeAndKeySize[0]==LEAF? 2 : 8)){
            Log.d("app.trie", "ERROR! childArray <> CountInMap");
        }

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

    private void deleteOldest(long pos, byte[] key) throws IOException {
        //todo не удаляет старые узлы проверить
        Context context = app.getApplicationContext();
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        long maxAge = Long.parseLong(sharedPref.getString("maxAge", "30"));
        byte[] nodeAge = new byte[2];
        app.trie.seek(pos);
        app.trie.read(nodeAge, 0, 2);
        if (Utils.compareDate(new Date(), Utils.reconstructAgeFromBytes(nodeAge)) > maxAge) {
            if (pos == 0L) {
                byte[] childPos = new byte[8];
                for(int i=0; i < 256; i++) {
                    app.trie.seek(22 + i * 8);
                    app.trie.read(childPos, 0, 8);
                    if (Longs.fromByteArray(childPos) != 0) {
                        key[0] = (byte) i;
                        deleteOldest(Longs.fromByteArray(childPos), key);
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
                int childPosInMap = 0;
                for(int i=0; i < 256; i++) {
                    if(checkExistChildInMap(childsMap, i)){
                        childPosInMap = getChildPosInMap(childsMap, i);
                        if(childPosInMap>0) {
                            byte[] sKey=new byte[1];
                            sKey[0] = (byte) i;
                            app.trie.seek(pos + 4 + keyNodeSize + 20 + 32 + ((childPosInMap * (type == LEAF ? 2 : 8)) - (type == LEAF ? 2 : 8)));
                            byte[] childPos = new byte[(type == LEAF ? 2 : 8)];
                            app.trie.read(childPos, 0, (type == LEAF ? 2 : 8));
                            if(type==LEAF){
                                if (Utils.compareDate(new Date(), Utils.reconstructAgeFromBytes(childPos)) > maxAge) {
                                    // insert in delete  bd
                                    app.addForDelete(Bytes.concat(fullKey, sKey));
                                }
                            }else{
                                deleteOldest(Longs.fromByteArray(childPos), Bytes.concat(fullKey, sKey));
                            }
                        }
                    }
                }
            }
        }
    }

    @NonNull
    @TargetApi(26)
    private synchronized String createChannel() {
        NotificationManager mNotificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);

        String name = "Service: Trie";
        int importance = NotificationManager.IMPORTANCE_LOW;

        NotificationChannel mChannel = new NotificationChannel("Service: Trie", name, importance);

        mChannel.enableLights(true);
        mChannel.setLightColor(Color.BLUE);
        if (mNotificationManager != null) {
            mNotificationManager.createNotificationChannel(mChannel);
        } else {
            stopSelf();
        }
        return "Service: Trie";
    }
}
