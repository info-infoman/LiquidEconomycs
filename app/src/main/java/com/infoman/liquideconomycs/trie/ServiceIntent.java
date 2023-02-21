package com.infoman.liquideconomycs.trie;

import android.annotation.TargetApi;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;

import com.infoman.liquideconomycs.Core;
import com.infoman.liquideconomycs.Utils;

import java.io.FileNotFoundException;
import java.io.IOException;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import static androidx.core.app.NotificationCompat.PRIORITY_LOW;
import static com.infoman.liquideconomycs.Utils.ACTION_DELETE;
import static com.infoman.liquideconomycs.Utils.ACTION_FIND;
import static com.infoman.liquideconomycs.Utils.ACTION_GENERATE_ANSWER;
import static com.infoman.liquideconomycs.Utils.ACTION_GET_HASH;
import static com.infoman.liquideconomycs.Utils.ACTION_INSERT;
import static com.infoman.liquideconomycs.Utils.ACTION_INSERT_FREE_SPACE_IN_MAP;
import static com.infoman.liquideconomycs.Utils.ACTION_STOP_TRIE;
import static com.infoman.liquideconomycs.Utils.EXTRA_AGE;
import static com.infoman.liquideconomycs.Utils.EXTRA_MASTER;
import static com.infoman.liquideconomycs.Utils.EXTRA_MSG_TYPE;
import static com.infoman.liquideconomycs.Utils.EXTRA_PAYLOAD;
import static com.infoman.liquideconomycs.Utils.EXTRA_POS;
import static com.infoman.liquideconomycs.Utils.EXTRA_PUBKEY;
import static com.infoman.liquideconomycs.Utils.EXTRA_SPACE;
import static com.infoman.liquideconomycs.trie.Node.ROOT;
//TODO add max age field in leaf and branch node = max age in childs, for automate delete to old pubKey

public class ServiceIntent extends IntentService {

    protected Core app;
    protected Node node;

    public ServiceIntent() {
        super("TrieServiceIntent");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        //android.os.Debug.waitForDebugger();
        app = (Core) getApplicationContext();

        if(app.waitingIntentCount !=0) return;
        Log.d("TrieServiceIntent", "Create!"+app.waitingIntentCount);

        //init trie file class
        try {
            app.file = new File(app,app.getFilesDir().getAbsolutePath() + "/trie" + "/trie.dat", "rw");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        try {
            app.file.recovery();
        } catch (IOException e) {
            e.printStackTrace();
        }

        //load root
        NodeParams nodeParams = new NodeParams();
        nodeParams.age = new byte[2];
        nodeParams.type = ROOT;
        nodeParams.pubKey = new byte[0];
        nodeParams.pos = 0L;
        nodeParams.hash = new byte[20];
        nodeParams.newble = false;

        try {
            node = new Node(app, nodeParams);
        } catch (IOException e) {
            e.printStackTrace();
        }
        //android.os.Debug.waitForDebugger();
        //TODO cut down childMap 4 bytes sector mask and 2-32 bytes map? total 5-36 bytes
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            channel = createChannel();
        }else {
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
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {

            //Log.d("app.trie", "onHandleIntent!"+app.waitingIntentCount+" "+intent.getAction());
            final String action = intent.getAction();

            if (ACTION_GET_HASH.equals(action)) {
                final String master = intent.getStringExtra(EXTRA_MASTER), cmd = "GetHash";
                final long pos = intent.getLongExtra(EXTRA_POS,0L);
                ////////////////////////////////////////////////////////////////
                try {
                    app.broadcastActionMsg(master, cmd, node.getHash(pos));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                ////////////////////////////////////////////////////////////////
            }

            if (ACTION_INSERT.equals(action)) {
                while(app.waitingIntentCount!=0){}
                app.waitingIntentCount++;
                final byte[] pubKey = intent.getByteArrayExtra(EXTRA_PUBKEY), age = intent.getByteArrayExtra(EXTRA_AGE);
                ////////////////////////////////////////////////////////////////
                try {
                    node.insert(pubKey, age);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                app.waitingIntentCount--;
                ////////////////////////////////////////////////////////////////
            }

            if (ACTION_FIND.equals(action)) {
                final String master = intent.getStringExtra(EXTRA_MASTER), cmd = "Find";
                final byte[] key = intent.getByteArrayExtra(EXTRA_PUBKEY);
                ////////////////////////////////////////////////////////////////
                try {
                    app.broadcastActionMsg(master, cmd, node.find(key));
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
                /*try {
                    //app.broadcastActionMsg(master, cmd, delete(key, pos));
                } catch (IOException e) {
                    e.printStackTrace();
                }*/
                ////////////////////////////////////////////////////////////////
            }

            if (ACTION_GENERATE_ANSWER.equals(action)) {
                final byte msgType = intent.getByteExtra(EXTRA_MSG_TYPE, Utils.getHashs);
                final byte[] payload = intent.getByteArrayExtra(EXTRA_PAYLOAD);
                ////////////////////////////////////////////////////////////////
                /*try {
                    //app.broadcastActionMsg("Trie", "Answer", generateAnswer(msgType, payload));
                } catch (IOException | SignatureDecodeException e) {
                    e.printStackTrace();
                }*/
                ////////////////////////////////////////////////////////////////
            }

            if (ACTION_INSERT_FREE_SPACE_IN_MAP.equals(action)) {
                final long pos = intent.getLongExtra(EXTRA_POS, 0L);
                final int space = intent.getIntExtra(EXTRA_SPACE,0);
                ////////////////////////////////////////////////////////////////
                app.insertFreeSpaceWitchCompressTrieFile(pos, space);
                ////////////////////////////////////////////////////////////////
            }

            if (ACTION_STOP_TRIE.equals(action)) {
                try {
                    while(app.waitingIntentCount!=0){}
                    app.waitingIntentCount++;
                    //get Oldest Key
                    app.clearTableForDelete();

                    node.findOldestNode(new byte[0]);
/*
                    Cursor query = app.getPubKeysForDelete();
                    while (query.moveToNext()) {
                        int pubKeyColIndex = query.getColumnIndex("pubKey");
                        delete(query.getBlob(pubKeyColIndex), 0L);
                    }
                    query.close();
*/
                    //app.clearTableForDelete();
                    app.clearPrefixTable();
                    app.waitingIntentCount--;
                } catch (IOException e) {
                    e.printStackTrace();
                }
                ////////////////////////////////////////////////////////////////
            }


        }
    }



    /*
    //todo add age sort for sync priority
    //todo изолировать запись
    private byte[] generateAnswer(byte msgType, byte[] payload) throws IOException, SignatureDecodeException {
        Context context = app.getApplicationContext();
        ChildsMap childsMap = new ChildsMap(32);
        ChildsMap selfNodeMap = new ChildsMap(32);
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
                childsMap.value             = Utils.getBytesPart(payload, i + 10 + nodeTypeAndKeySize[1], 32);
                int childsCountInMap        = childsMap.getCount();
                int len                     = childsCountInMap * (nodeTypeAndKeySize[0]==Utils.LEAF ? 2 : 28);
                byte[] childsArray          = Utils.getBytesPart(payload, i + 10 + nodeTypeAndKeySize[1] + 32, len);
                //next index of data-part in payload
                i                           = i + 10 + nodeTypeAndKeySize[1] + 32 + len;
                //self node
                byte[] selfNodePos= new byte[8],
                        selfPrefix = new byte[0],
                        selfNodeMapAndHashOrAge,
                        selfTypeAndKeySize = new byte[2],
                        selfNodeHashsOrAges = new byte[0];
                selfNodeMap.value = null;
                if (nodeTypeAndKeySize[0]== ROOT){
                    selfNodeMapAndHashOrAge = getNodeMapAndHashesOrAges(selfNodePos);
                    selfTypeAndKeySize = Utils.getBytesPart(selfNodeMapAndHashOrAge, 0, 2);
                    selfNodeMap.value = Utils.getBytesPart(selfNodeMapAndHashOrAge, 2, 32);
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
                                selfNodeMap.value = Utils.getBytesPart(selfNodeMapAndHashOrAge, 2 + selfTypeAndKeySize[1], 32);
                                selfNodeHashsOrAges = Utils.getBytesPart(selfNodeMapAndHashOrAge, 2 + selfTypeAndKeySize[1] + 32, selfNodeMapAndHashOrAge.length - (2 + selfTypeAndKeySize[1] + 32));
                            } else {
                                selfNodeMap.value = Utils.getBytesPart(selfNodeMapAndHashOrAge, 2, 32);
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
                    if (!childsMap.get(c)) {
                        continue;
                    }
                    int posInMap = childsMap.getPos(c);
                    int selfPosInMap = selfNodeMap.getPos(c);
                    byte[] newPrefix;
                    if(exist || selfTypeAndKeySize[0] == ROOT) {
                        newPrefix = selfTypeAndKeySize[0] != ROOT ? Bytes.concat(selfPrefix, key, c_) : c_;
                        existSelfChild = selfNodeMap.get(c);

                        if (nodeTypeAndKeySize[0] == LEAF) {
                            byte[] childAge = Utils.getBytesPart(childsArray, (posInMap * offLen) - offLen, offLen);
                            if (!existSelfChild ||
                                    (!Arrays.equals(childAge, Utils.getBytesPart(selfNodeHashsOrAges, (selfPosInMap * offLen) - offLen, offLen))
                                            && Utils.compareDate(Utils.reconstructAgeFromBytes(childAge), Utils.reconstructAgeFromBytes(Utils.getBytesPart(selfNodeHashsOrAges, (selfPosInMap * offLen) - offLen, offLen))) > 0L)
                            ) {
                                //todo add check newPrefix length
                                SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
                                long maxAge = Long.parseLong(sharedPref.getString("maxAge", "30"));
                                if (Utils.compareDate(new Date(), Utils.reconstructAgeFromBytes(childAge)) < maxAge && Utils.compareDate(new Date(), Utils.reconstructAgeFromBytes(childAge)) >= 0L)
                                    app.startActionInsert(newPrefix, childAge);

                            }
                        } else {
                            if (!existSelfChild || !Arrays.equals(
                                    Utils.getBytesPart(childsArray, (posInMap * 28) - offLen, offLen),
                                    Utils.getBytesPart(selfNodeHashsOrAges, (posInMap * 28) - offLen, offLen)
                            )) {
                                //todo add to table sync add to list new ask
                                long pos_ = Longs.fromByteArray(Utils.getBytesPart(childsArray, (posInMap * 28) - 28, 8));
                                app.addPrefixByPos(pos_, newPrefix, null, existSelfChild);
                                ask = Bytes.concat(ask, Longs.toByteArray(pos_));
                            }
                        }
                    }else{
                        //если это новый узел то если это ветвь то продолжаем запросы
                        if(nodeTypeAndKeySize[0] != LEAF) {
                            long pos_ = Longs.fromByteArray(Utils.getBytesPart(childsArray, (selfPosInMap * 28) - 28, 8));
                            app.addPrefixByPos(pos_, Bytes.concat(selfPrefix, key, c_), null, false);
                            ask = Bytes.concat(ask, Longs.toByteArray(pos_));
                        }else{
                            // иначе добавляем новые ключи с возрастами
                            byte[] childAge = Utils.getBytesPart(childsArray, (posInMap* offLen) - offLen, offLen);
                            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
                            long maxAge = Long.parseLong(sharedPref.getString("maxAge", "30"));
                            //todo add check newPrefix length
                            if (Utils.compareDate(new Date(), Utils.reconstructAgeFromBytes(childAge)) < maxAge && Utils.compareDate(new Date(), Utils.reconstructAgeFromBytes(childAge)) >= 0L){
                                app.startActionInsert(Bytes.concat(selfPrefix, key, c_), childAge);
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



    //return byte[8] if deleted or not found, or pos in file if change or hash if root
    private byte[] delete(byte[] key, long pos) throws IOException {
        byte[] hash;
        ChildsMap childsMap = new ChildsMap(32);
        byte[] typeAndKeySize = new byte[2];
        byte[] s=search(key, pos);
        if (s.length > 0) {
            if(pos==0L) {
                if(key.length<20){
                    Log.d("app.trie", "ERROR! delete key to small");
                    return new byte[8];
                }
                byte[] dKey = getBytesPart(key, 1, key.length - 1);
                byte[] posBytes = delete(dKey, Longs.fromByteArray(s));
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
                app.trie.read(childsMap.value, 0, 32);
                byte[] suffixKey = getBytesPart(key, keyNodeSize, key.length - keyNodeSize);

                int selfChildsCount = childsMap.getCount();
                int selfChildArraySize = selfChildsCount * (type == LEAF ? 2 : 8);

                if(selfChildsCount==1 && type == LEAF){ //jast delete empty LEAF node and return zero
                    app.startActionInsertFreeSpaceInMap(pos, 56 + keyNodeSize + selfChildArraySize);
                    return new byte[8];
                }else{//selfChildsCount <=> 1 && any type
                    app.trie.seek(pos + 4 + keyNodeSize + 20 + 32);
                    byte[] childArray = new byte[selfChildArraySize];
                    app.trie.read(childArray, 0, selfChildArraySize);
                    int delByte = (type==LEAF ? (key[key.length-1]&0xFF) :(suffixKey[0]&0xFF));

                    if (type != LEAF) {//for BRANCH
                        int childPosInMap = childsMap.getPos(delByte);
                        long posToDelete = pos + 4 + keyNodeSize + 20 + 32 + ((childPosInMap * 8) - 8);
                        app.trie.seek(posToDelete);
                        byte[] chPos = new byte[8];
                        app.trie.read(chPos, 0, 8);
                        //delete in child
                        byte[] newChPos = delete(getBytesPart(suffixKey, 1, suffixKey.length - 1), Longs.fromByteArray(chPos));
                        if(Longs.fromByteArray(newChPos) == 0L){//deleted all child node
                            app.startActionInsertFreeSpaceInMap(pos, 56 + keyNodeSize + selfChildArraySize);
                            if(selfChildsCount==1){// deleted node
                                return new byte[8];
                            }else{ //jast copy node in new place
                                int chp = childsMap.getPos(delByte);
                                //delete in map
                                childsMap.set(delByte, false);
                                //delete in array
                                byte[] before = (chp == 0 ? new byte[0] : getBytesPart(childArray, 0, (chp - 1) * 8));
                                childArray = Bytes.concat(before, getBytesPart(childArray, (chp * 8), childArray.length - (chp * 8)));

                                if(childArray.length == 8) {//if stay one child then
                                    byte[] k = new byte[1];
                                    k[0] = (byte) childsMap.getOne();
                                    byte[] reconstNode = reconctructNode(childArray, keyNodeSize, keyNode, k);
                                    if (reconstNode.length > 0) {//if not leaf then reconstruct this branch witch
                                        return reconstNode;
                                    }
                                }
                                hash = calcHash(type, keyNode, childArray);
                                byte[] oldestNodeAge = getOldestNodeAge(nodeAge, BRANCH, childArray);
                                return Longs.toByteArray(
                                        addRecordInFile(oldestNodeAge,
                                                typeAndKeySize,
                                                keyNode,
                                                hash,
                                                childsMap,
                                                childArray)
                                );
                            }
                        }else{//changed child node todo изолировать запись
                                app.trie.seek(posToDelete);
                                app.trie.write(newChPos);
                                app.trie.seek(pos + 4 + keyNodeSize + 20 + 32);
                                childArray = new byte[selfChildArraySize];
                                app.trie.read(childArray, 0, selfChildArraySize);
                                hash = calcHash(type, keyNode, childArray);

                                byte[] oldestNodeAge = getOldestNodeAge(nodeAge, BRANCH, childArray);
                                app.trie.seek(pos);
                                app.trie.write(oldestNodeAge);
                                app.trie.seek(pos + 4 + keyNodeSize);
                                app.trie.write(hash);
                                return Longs.toByteArray(pos);
                        }
                    }else{ // for leaf jast delete age and copy node to new place
                        app.startActionInsertFreeSpaceInMap(pos, 56 +  keyNodeSize + selfChildArraySize);
                        int chp= childsMap.getPos(delByte);
                        //delete in map
                        childsMap.set(delByte, false);

                        byte[] before=(chp == 0 ? new byte[0] : getBytesPart(childArray,0,  (chp-1)*2));
                        childArray = Bytes.concat(before, getBytesPart(childArray, (chp * 2), childArray.length-(chp * 2)));

                        hash=calcHash(type, keyNode, Bytes.concat(childsMap.value, childArray));
                        byte[] oldestNodeAge = getOldestNodeAge(nodeAge, LEAF, childArray);

                        return Longs.toByteArray(
                                addRecordInFile(oldestNodeAge,
                                        typeAndKeySize,
                                        keyNode,
                                        hash,
                                        childsMap,
                                        childArray)
                        );
                    }
                }
            }
        }
        //not found
        return new byte[8];
    }

    private byte[] reconctructNode(byte[] childArray, int keyNodeSize, byte[] keyNode, byte[] childKey) throws IOException {
        long pos = Longs.fromByteArray(childArray);
        byte[] nodeAge = new byte[2], typeAndKeySize = new byte[2];

        app.trie.seek(pos);
        app.trie.read(nodeAge, 0, 2);
        typeAndKeySize[0] = app.trie.readByte();
        if(typeAndKeySize[0] == LEAF){
           return new byte[0];//do nothing
        }else{
            ChildsMap childsMap = new ChildsMap(32);
            int selfChildsCount, selfChildArraySize, oldKeySize;
            byte[] oldKeyChild, newChildArray, newKeyNode, hash;

            oldKeySize = app.trie.readByte();
            typeAndKeySize[1] = (byte)(oldKeySize + 1 + keyNodeSize);
            oldKeyChild = new byte[oldKeySize];
            app.trie.read(oldKeyChild, 0, oldKeySize);
            app.trie.seek(pos + 4 + oldKeySize + 20);
            app.trie.read(childsMap.value, 0, 32);
            selfChildsCount = childsMap.getCount();
            selfChildArraySize = selfChildsCount * 8;
            newChildArray = new byte[selfChildArraySize];
            app.trie.read(newChildArray, 0, selfChildArraySize);
            newKeyNode = Bytes.concat(keyNode,childKey, oldKeyChild);
            hash = calcHash(typeAndKeySize[0], newKeyNode, newChildArray);

            app.startActionInsertFreeSpaceInMap(pos, 56 +  oldKeySize + selfChildArraySize);

            return Longs.toByteArray(
                    addRecordInFile(nodeAge,
                            typeAndKeySize,
                            newKeyNode,
                            hash,
                            childsMap,
                            newChildArray)
            );
        }

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

    //Получает typeAndKeySize+keyNode+childMap+childArray[pos+hash(BRANCH\ROOT)... or age(LEAF)...]
    // узла по позиции в файле дерева
    private byte[] getNodeMapAndHashesOrAges(byte[] selfNodePos) throws IOException {
        long pos = Longs.fromByteArray(selfNodePos);
        ChildsMap childsMap = new ChildsMap(32);
        byte[]  typeAndKeySize  = new byte[2],
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
                    childsMap.set(i & 0xFF,true);
                    selfChildArray = Bytes.concat(selfChildArray, childPos);
                }
            }
        }else {
            app.trie.seek(pos+2);
            app.trie.read(typeAndKeySize, 0, 2);
            keyNode = new byte[typeAndKeySize[1]];
            app.trie.read(keyNode, 0, typeAndKeySize[1]);
            app.trie.seek(pos + 4 + typeAndKeySize[1] + 20); //skip hash
            app.trie.read(childsMap.value, 0, 32);
            int selfChildrenCount = childsMap.getCount();
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
            return Bytes.concat(typeAndKeySize, keyNode, childsMap.value, result);
        else
            return Bytes.concat(typeAndKeySize, childsMap.value, result);
    }

    //return pos or age or byte[0] - if not found
    private byte[] search(byte[] key, long pos) throws IOException {
        //Если размер файла == 0  то вернем null
        ChildsMap childsMap = new ChildsMap(32);
        if(app.trie.length()>0) {
            //если это корень то переместим курсор на позицию в массиве детей = первый байт ключа * 8 - 8
            //и вернем если не равно 0
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
                app.trie.read(childsMap.value, 0, 32);
                //Получим префикс и суффикс искомого ключа

                byte[] preffixKey = getBytesPart(key, 0, keyNodeSize);
                byte[] suffixKey = getBytesPart(key, keyNodeSize, key.length - keyNodeSize);

                //found Если искомый ключ(его часть =  длинне ключа узла) = ключу узла и первый байт суффикса имеется в массиве дочерей то
                if(Arrays.equals(preffixKey, keyNode) && childsMap.get( suffixKey[0] & 0xFF)) {
                    int childPosInMap;
                    byte[] result;
                    childPosInMap = childsMap.getPos(suffixKey[0] & 0xFF);
                    if(type==BRANCH) {//ret pos
                        app.trie.seek(pos + 4 + keyNodeSize + 20 + 32 + ((childPosInMap * 8) - 8));
                        result = new byte[8];
                        app.trie.read(result, 0, 8);

                    }else {                  //ret age
                        app.trie.seek(pos + 4 + keyNodeSize + 20 + 32 + ((childPosInMap * 2) - 2));
                        result = new byte[2];
                        app.trie.read(result, 0, 2);
                    }
                    return result;
                }
            }
        }
        return new byte[0];
    }


*/
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