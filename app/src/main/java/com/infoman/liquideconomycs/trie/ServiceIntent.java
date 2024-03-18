package com.infoman.liquideconomycs.trie;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;
import com.infoman.liquideconomycs.Core;
import com.infoman.liquideconomycs.Utils;

import org.bitcoinj.core.SignatureDecodeException;

import java.io.IOException;
import java.util.Arrays;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;

import static androidx.core.app.NotificationCompat.PRIORITY_LOW;
import static com.infoman.liquideconomycs.Utils.ACTION_FIND;
import static com.infoman.liquideconomycs.Utils.ACTION_GENERATE_ANSWER;
import static com.infoman.liquideconomycs.Utils.ACTION_INSERT;
import static com.infoman.liquideconomycs.Utils.EXTRA_AGE;
import static com.infoman.liquideconomycs.Utils.EXTRA_MASTER;
import static com.infoman.liquideconomycs.Utils.EXTRA_MSG_TYPE;
import static com.infoman.liquideconomycs.Utils.EXTRA_PAYLOAD;
import static com.infoman.liquideconomycs.Utils.EXTRA_PUBKEY;
import static com.infoman.liquideconomycs.trie.Node.BRANCH;
import static com.infoman.liquideconomycs.trie.Node.LEAF;
import static com.infoman.liquideconomycs.trie.Node.ROOT;
import static java.lang.Long.parseLong;
//TODO add max age field in leaf and branch node = max age in childs, for automate delete to old pubKey

public class ServiceIntent extends IntentService {

    protected Core app;
    protected Node[] nodes;

    public ServiceIntent() {
        super("TrieServiceIntent");
    }


    @SuppressLint("ForegroundServiceType")
    @Override
    public void onCreate() {
        super.onCreate();
        //android.os.Debug.waitForDebugger();
        app = (Core) getApplicationContext();
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(app);
        long maxAge = parseLong(sharedPref.getString("maxAge", "30"));
//        for( int i : app.waitingIntentCounts) {
//            if(i!=0){
//                return;
//            }
//        }
        nodes = new Node[(int) maxAge];

        try {
            for(int i = 0; i < nodes.length; i++){
                NodeParams nodeParams = new NodeParams();
                nodeParams.index = i;
                nodeParams.type = ROOT;
                nodeParams.pubKey = new byte[0];
                nodeParams.pos = 0L;
                nodeParams.hash = new byte[20];
                nodeParams.newble = false;
                nodes[i] = new Node(app, nodeParams);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        //android.os.Debug.waitForDebugger();
        //TODO cut down childMap 4 bytes sector mask and 2-32 bytes map? total 5-36 bytes
        //ROOT(content BRANCHs & LEAFs)
        //hash sha256hash160(20) /child point array(1-256*8)
        //00*20                  /0000000000000000 max 2068 byte
        //BRANCH(content child BRANCHs & LEAFs)
        //type(1)/key size(1)/key(0-18)/hash sha256hash160(20)/childsMap(32)  /nodePointArray(1-256*8)
        //00     /00         /00*18    /00*20                 /00*32         /00*8    max 2102 byte (ideal 153 185(branchs) =~307Mb)
        //LEAF(content accounts key suffix & age)
        //type(1)/key size(1)/key(0-18)/hash sha256hash160(20)/childsMap(32)
        //00     /00         /00*18    /00*20                 /00*32                  max 72 byte (ideal 39 062 500(leafs)=2GB)
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
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
        // cancel any running threads here
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            Log.d("trie!", String.format("trie! %s", action));
            if (ACTION_INSERT.equals(action)) {
                final byte[] pubKey = intent.getByteArrayExtra(EXTRA_PUBKEY);
                final int index = intent.getIntExtra(EXTRA_AGE, 0);
                if(index > -1 && index < nodes.length) {
                    //while (app.waitingIntentCounts[index] != 0) {}
                    //app.waitingIntentCounts[index]++;

                    ////////////////////////////////////////////////////////////////
                    try {
                        nodes[index].insert(pubKey);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    //app.waitingIntentCounts[index]--;
                }
                ////////////////////////////////////////////////////////////////
            }

            if (ACTION_FIND.equals(action)) {
                final String master = intent.getStringExtra(EXTRA_MASTER), cmd = "Find";
                final byte[] key = intent.getByteArrayExtra(EXTRA_PUBKEY);
                ////////////////////////////////////////////////////////////////
                try {
                    for(int i = nodes.length - 1; i == 0; i--){
                        if(nodes[i].find(key)){
                            app.broadcastActionMsg(master, cmd, new byte[1]);
                            break;
                        }
                    }
                    app.broadcastActionMsg(master, cmd, new byte[0]);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                ////////////////////////////////////////////////////////////////
            }

            if (ACTION_GENERATE_ANSWER.equals(action)) {
                final byte msgType = intent.getByteExtra(EXTRA_MSG_TYPE, Utils.getHashs);
                final byte[] payload = intent.getByteArrayExtra(EXTRA_PAYLOAD);
                ////////////////////////////////////////////////////////////////
                Log.d("trie!", String.format("trie! %s", Arrays.toString(payload)));
                try {
                    app.broadcastActionMsg("Trie", "Answer", generateAnswer(msgType, payload));
                } catch (IOException | SignatureDecodeException e) {
                    e.printStackTrace();
                }
                ////////////////////////////////////////////////////////////////
            }
        }
    }

    @SuppressLint("Range")
    private byte[] generateAnswer(byte msgType, byte[] payload) throws IOException, SignatureDecodeException {
        int index = 0;
        byte[] answer = new byte[0];
        boolean exist, existSelfChild;
        if (null != payload && payload.length > 8){
            answer = Utils.getBytesPart(payload,0, 1);
            index = answer[0] & 0xFF;
            if(index < 0 || index > nodes.length - 1){
                return new byte[0];
            }
            payload = Utils.getBytesPart(payload,1, payload.length-1);
        }else{
            return answer;
        }

        if(msgType == Utils.getHashs){
            //payload = array[pos...]
            for(int i=0;i < payload.length/8;i++){
                byte[] posInByte = Utils.getBytesPart(payload,i*8, 8);
                long pos = Longs.fromByteArray(posInByte);
                NodeParams nodeParams = new NodeParams();
                nodeParams.index = index;
                nodeParams.type = (pos == 0L ? ROOT: BRANCH);
                nodeParams.pubKey = new byte[0];
                nodeParams.pos = pos;
                nodeParams.hash = new byte[20];
                nodeParams.newble = false;
                Node node = new Node(app, nodeParams);
                if(node.type != LEAF){
                    node.loadChilds();
                }
                answer = Bytes.concat(answer, posInByte, node.getBlob(true));
            }
            byte[] type = new byte[1];
            type[0] = Utils.hashs;
            answer = Bytes.concat(type, answer);
        }else{
            //payload = pos+typeAndKeySize+keyNode+childMap+childArray[pos+hash(BRANCH\ROOT)... or age(LEAF)...]
            //если ранее запрашивался корень то сравним с хешем нашего корня и если совпадает то ничего не делаем
            //если нет то построим карту своего корня и пройдемся по узлам как обычно
            //нам прислали рание запрошенные узлы, необходимо их расшифровать
            NodeParams nodeParams;
            for(int i = 0; i < payload.length;) {
                exist                       = false;
                //node from payload

                byte[] nodeTypeAndKeySize   = Utils.getBytesPart(payload, i+8, 2);
                nodeParams = new NodeParams();
                nodeParams.index = index;
                nodeParams.pos = Longs.fromByteArray(Utils.getBytesPart(payload, i, 8));
                nodeParams.type = nodeParams.pos == 0L ? ROOT : nodeTypeAndKeySize[0];
                nodeParams.pubKey = nodeParams.type != ROOT ? Utils.getBytesPart(payload, i + 10, nodeTypeAndKeySize[1]) : new byte[0];
                nodeParams.hash = nodeParams.type != ROOT ? new byte[20] : Utils.getBytesPart(payload, i + 8, 20);
                nodeParams.newble = true;
                Node node = new Node(app, nodeParams);
                if (nodeParams.type != ROOT) {
                    node.mapBytes = Utils.getBytesPart(payload, i + 10 + nodeTypeAndKeySize[1], 32);
                }else{
                    node.loadRootMapForExtNode(Utils.getBytesPart(payload, i + 8, payload.length - 8));
                }
                int offLen                  = (node.type!=LEAF?20:0);
                int len                     = node.getCountInMap() * (node.type==LEAF ? 0 : 28);
                int off = nodeParams.type != ROOT ? i + 10 + nodeTypeAndKeySize[1] + 32 : i + 8;
                byte[] childsArray          = Utils.getBytesPart(payload, off, len);

                //next index of data-part in payload
                i = off + len;

                //load self node
                Node selfNode;
                byte[] selfPrefix = new byte[0];
                nodeParams = new NodeParams();
                nodeParams.index = index;
                nodeParams.type = ROOT;
                nodeParams.pubKey = new byte[0];
                nodeParams.pos = 0L;
                nodeParams.hash = new byte[20];
                nodeParams.newble = false;
                Node rootNode = new Node(app, nodeParams);
                rootNode.loadChilds();
                rootNode.calcSpace();
                selfNode = rootNode;
                if (node.type != ROOT){
                    //Получим полный префикс ключа в предидущем цикле перед запросом
                    //Префикс нарастает по мере движения в глюбь дерева
                    Cursor query = app.getPrefixByPos(index, node.position);
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
                        selfNode = rootNode.findPath(selfPrefix);
                        selfNode.loadChilds();
                        selfNode.calcSpace();
                    }
                }else{
                    if (!Arrays.equals(node.hash, selfNode.hash)){
                        return new byte[0];
                    }
                }
                //todo В цикле  от 0 - 255 мы должны обойти все дочерние узлы
                // 1) Если узел\возраст есть в полученой карте:
                // 1.1) Если это тип BRANCH\ROOT и (узел в карте имеет хеш не равный нашему или дочерний узел не найден в нашей карте) то
                // внести в базу (prefix + индекс цикла - позиция в карте) и позицию, добавить позицию в следующий запрос
                // 1.2) Если это тип LEAF и ((узел в карте имеет возраст не равный нашему  и возраст моложе нашего ) или возраст не найден в нашей карте)
                // то добавить/изменить по(prefix + индекс цикла - позиция в карте) новый возраст
                byte[] ask = new byte[1];
                ask[0] = (byte) index;

                for(int c = 0; c < 256; c++){
                    byte[] c_ = new byte[1];
                    c_[0] = (byte)c;
                    if (!node.getInMap(c)) {
                        continue;
                    }
                    int posInMap = node.getPos(c);
                    int selfPosInMap = selfNode.getPos(c);
                    byte[] newPrefix;
                    //Если такой узел есть или это корень то
                    if(exist || selfNode.type == ROOT) {
                        newPrefix = selfNode.type != ROOT ? Bytes.concat(selfPrefix, node.nodeKey.nodePubKey, c_) : c_;
                        existSelfChild = selfNode.getInMap(c);
                        if (node.type == LEAF) {
                            if (!existSelfChild){
                                //Если лист и на листе не найден конец ключа то добавим новый ключ
                                app.broadcastActionMsg("Trie", "AddPubKeyForInsert", index, newPrefix);

                                //app.startActionInsert(newPrefix, index);
                            }
                        } else {
                            if (!existSelfChild || !Arrays.equals(
                                    Utils.getBytesPart(childsArray, (posInMap * 28) - offLen, offLen),
                                    selfNode.hash
                            )){
                                //если это корень или ветвь  и если ненайден дочерний узел
                                // или хеш дочернего узла не равен хешу узла//todo?????????????
                                long pos_ = Longs.fromByteArray(Utils.getBytesPart(childsArray, (posInMap * 28) - 28, 8));
                                app.addPrefixByPos(pos_, newPrefix, index, existSelfChild);
                                ask = Bytes.concat(ask, Longs.toByteArray(pos_));
                            }
                        }
                    }else{
                        //если это новый узел  и не корень то если это ветвь то продолжаем запросы
                        if(node.type != LEAF) {
                            long pos_ = Longs.fromByteArray(Utils.getBytesPart(childsArray, (selfPosInMap * 28) - 28, 8));
                            app.addPrefixByPos(pos_, Bytes.concat(selfPrefix, node.nodeKey.nodePubKey, c_), index, false);
                            ask = Bytes.concat(ask, Longs.toByteArray(pos_));
                        }else{
                            // иначе добавляем новые ключи с возрастами
                            app.broadcastActionMsg("Trie", "AddPubKeyForInsert", index, Bytes.concat(selfPrefix, node.nodeKey.nodePubKey, c_));
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
