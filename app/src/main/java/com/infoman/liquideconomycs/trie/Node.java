package com.infoman.liquideconomycs.trie;

import android.content.SharedPreferences;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;
import com.infoman.liquideconomycs.Core;

import java.io.IOException;

import androidx.preference.PreferenceManager;

import static com.infoman.liquideconomycs.Utils.getDayMilliByIndex;
import static org.bitcoinj.core.Utils.sha256hash160;


public class Node extends ChildMap {

    public static byte
            ROOT        = 1,
            BRANCH      = 2,
            LEAF        = 3;
    public Core app;
    public long position, maxAge;
    public byte[] hash;
    public PubKey nodeKey;
    public byte type;
    public boolean change;
    public int space, index;

    public Node(Core context, NodeParams nodeParams) throws IOException {
        super(32);
        index =  nodeParams.index;
        app = context;
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(app);
        maxAge = Long.parseLong(sharedPref.getString("maxAge", "30"));
        type = nodeParams.type;
        nodeKey = new PubKey(type, nodeParams.pubKey);
        position = nodeParams.pos;
        hash = nodeParams.hash;
        if(!nodeParams.newble){
            loadNode();
        }else{
            change = true;
        }
    }

    private void loadNode() throws IOException {

        if ((type == ROOT && position != 0L)) throw new AssertionError();

        app.files[index].seek(position);

        if(type == ROOT) {
            app.files[index].get(hash, position, 0, 20);
            loadRootMap();
            //loadChilds();
        }else {
            type = app.files[index].readByte();
            int pubKeySize = app.files[index].readByte();
            byte[] pubKey = new byte[pubKeySize];
            app.files[index].get(pubKey, position + 2, 0, pubKeySize);
            nodeKey = new PubKey(type, pubKey);
            app.files[index].get(mapBytes, position + 2 + pubKeySize + 20, 0, mapSize);
            calcSpace();
            //child no need auto load childs because they loaded in constructTrieByKey()
        }

    }

    private void loadRootMap() throws IOException {
        long p = position + 20;
        for(int i = 0; i < (mapSize * 8); i++) {
            byte[] b = new byte[8];
            app.files[index].get(b, p + (i * 8), 0, 8);
            long pos = Longs.fromByteArray(b);
            if(pos > 0L){
                setInMap(i, true);
            }
        }
    }

    public void loadChilds() throws IOException {
        long positionInFile = type == ROOT ? position + 20 : position + (2 + nodeKey.nodePubKey.length + 20 + mapSize);
        for(int i = 0; i < (mapSize * 8); i++) {
            if (getInMap(i)) {
                int posInArray = type == ROOT ? i : getPos(i) - 1;
                byte[] b = new byte[8];
                app.files[index].get(b, positionInFile + (posInArray * 8), 0, 8);

                long pos = Longs.fromByteArray(b);
                NodeParams nodeParams = new NodeParams();
                nodeParams.index = index;
                nodeParams.type = BRANCH;
                nodeParams.pos = pos;
                nodeParams.hash = getHash(pos);
                nodeParams.newble = false;
                mapChilds[i] = new Node(app, nodeParams);

            }else{
                mapChilds[i] = null;

            }
        }
        calcHash();
    }

    private void loadChild(int pubKeyInt) throws IOException {
        long positionInFile = type == ROOT ? position + 20 : position + (2 + nodeKey.nodePubKey.length + 20 + mapSize);
        if (getInMap(pubKeyInt)) {
            int posInArray = type == ROOT ? pubKeyInt : getPos(pubKeyInt) - 1;
            byte[] b = new byte[8];
            app.files[index].get(b, positionInFile + (posInArray * 8), 0, 8);
            long pos = Longs.fromByteArray(b);
            NodeParams nodeParams = new NodeParams();
            nodeParams.index = index;
            nodeParams.type = BRANCH;
            nodeParams.pos = pos;
            nodeParams.hash = getHash(pos);
            nodeParams.newble = false;
            mapChilds[pubKeyInt] = new Node(app, nodeParams);
        }
    }

    //if position is clear/new, (replace old node/put new node/delete node),  on new place
    //and insert free space in database
    //if change rewrite node
    protected void insert(byte[] pubKey) throws IOException {
        boolean newSpace = false;
        if (type == ROOT) {
            if(find(pubKey)){
                return;
            }else{
                constructTrieByKey(pubKey);
            }
        }else{
            nodeKey.initNodeKyeFieldsByNewKey(pubKey);
        }
        if(nodeKey.getEqualsPrefixFromNewKeyAndNodePubKey() || type == ROOT) {
            int intForChild = nodeKey.getPlaceIntForChildFromNewKeySuffix(pubKey);
            if (type == LEAF) {
                if (getInMap(intForChild)) {
                    change = false;
                } else {
                    setInMap(intForChild, true);
                    calcHash();
                    calcSpace();
                    app.files[index].saveNodeNewStateBlobInDB(this, change = true);
                    change = true;
                }
            }else{//get/set child ref
                Node ref;
                if (getInMap(intForChild)) {
                    ref = mapChilds[intForChild];
                }else{
                    setInMap(intForChild, true);

                    NodeParams nodeParams = new NodeParams();
                    nodeParams.index = index;
                    nodeParams.type = LEAF;
                    nodeParams.pubKey = nodeKey.getKeyForNewChildFromNewPubKey();
                    nodeParams.pos = 0L;
                    nodeParams.hash = new byte[20];
                    nodeParams.newble = true;

                    ref = new Node(app, nodeParams);

                    mapChilds[intForChild] = ref;
                    if (type != ROOT) {
                        calcSpace();
                        newSpace = true;
                    }

                }
                ref.insert(nodeKey.getKeyForAddInChildFromNewKeySuffix());
                change = ref.change;
                if(change){
                    calcHash();
                    app.files[index].saveNodeNewStateBlobInDB(this, newSpace);
                }
            }
        }else{
            //reconstruct this node(leaf or branch)
            //get int
            int intForNewChild = nodeKey.getPlaceIntForChildFromNewKeySuffix(pubKey);
            int intForOldChild = nodeKey.getPlaceIntForChildFromNodeKeySuffix(pubKey);

            //create new node and copy this node to new node
            NodeParams nodeParams = new NodeParams();
            nodeParams.index = index;
            nodeParams.type = type;
            nodeParams.pubKey = nodeKey.getKeyForNewChildFromNodeKey();
            nodeParams.pos = 0L;
            nodeParams.hash = new byte[20];
            nodeParams.newble = true;

            Node ref = new Node(app, nodeParams);
            if (type != LEAF) {
                loadChilds();
            }
            ref.mapBytes = mapBytes;
            ref.mapChilds = mapChilds;
            ref.calcHash();
            ref.calcSpace();
            app.files[index].saveNodeNewStateBlobInDB(ref, true);

            //create new node and insert suffix key and age
            nodeParams = new NodeParams();
            nodeParams.index = index;
            nodeParams.type = LEAF;
            nodeParams.pubKey = nodeKey.getKeyForNewChildFromNewPubKey();
            nodeParams.pos = 0L;
            nodeParams.hash = new byte[20];
            nodeParams.newble = true;

            Node newRef = new Node(app, nodeParams);
            newRef.insert(nodeKey.getKeyForAddInChildFromNewKeySuffix());

            //change this node to common node
            app.insertFreeSpaceWitchCompressTrieFile(getDayMilliByIndex(index), position, space);
            nodeKey = new PubKey(BRANCH, nodeKey.commonKey);
            type = BRANCH;
            //clear map ch ag
            mapBytes = new byte[mapSize];
            mapChilds = new Node[mapSize * 8];
            //link new and old nodes to this
            setInMap(intForNewChild, true);
            setInMap(intForOldChild, true);
            mapChilds[intForNewChild] = newRef;
            mapChilds[intForOldChild] = ref;
            change = true;
            calcHash();
            calcSpace();

            app.files[index].saveNodeNewStateBlobInDB(this, true);
        }
        if(type==ROOT && change){
            app.files[index].transaction();
        }
    }

    private void constructTrieByKey(byte[] pubKey) throws IOException {
        nodeKey.initNodeKyeFieldsByNewKey(pubKey);
        if(nodeKey.getEqualsPrefixFromNewKeyAndNodePubKey()) {
            change = false;
            if (type != LEAF) {
                loadChilds();
            }
            int pubKeyInt = nodeKey.getPlaceIntForChildFromNewKeySuffix(pubKey);
            byte[] keyChild = nodeKey.getKeyForAddInChildFromNewKeySuffix();
            if (getInMap(pubKeyInt)) {
                //if (type != ROOT) {//root loaded in constructor
                app.files[index].saveNodeOldStateBlobInDB(getDayMilliByIndex(index), position, type == ROOT ? 2068 : space);
                //}
                if (type != LEAF) {//leaf have no childs
                    mapChilds[pubKeyInt].constructTrieByKey(keyChild);
                }
            }
        }
    }

    public boolean find(byte[] pubKey) throws IOException {
        nodeKey.initNodeKyeFieldsByNewKey(pubKey);
        if(nodeKey.getEqualsPrefixFromNewKeyAndNodePubKey()) {
            int pubKeyInt = nodeKey.getPlaceIntForChildFromNewKeySuffix(pubKey);
            byte[] keyChild = nodeKey.getKeyForAddInChildFromNewKeySuffix();
            if (getInMap(pubKeyInt)) {
                if (type != LEAF) {//leaf have no childs
                    loadChild(pubKeyInt);
                    return mapChilds[pubKeyInt].find(keyChild);
                }else{
                    return true;
                }
            }
        }
        return false;
    }

    public void calcSpace() {
        space = 4 + nodeKey.nodePubKey.length + 20 + mapSize + (getCountInMap() * (type == LEAF ? 0 : 8));
    }

    private void calcHash() {
        byte[] digest = Bytes.concat(nodeKey.nodePubKey, mapBytes);
        if(type!=LEAF) {
            byte[] emptyHash = new byte[20];
            for (int i = 0; i < mapChilds.length; i++) {
                if(getInMap(i) && mapChilds[i] != null && mapChilds[i].hash != emptyHash){
                  digest = Bytes.concat(digest, mapChilds[i].hash);
                }
            }
        }
        hash = sha256hash160(digest);
    }

    protected byte[] getHash(long pos) throws IOException {
        byte[] hash = new byte[20];
        if (pos == 0L) {
            app.files[index].seek(pos);
        } else {
            app.files[index].seek(pos+1);
            byte keySize = app.files[index].readByte();
            app.files[index].seek(pos+ 2 + keySize);
        }
        app.files[index].read(hash, 0, 20);
        return hash;
    }

}


