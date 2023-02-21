package com.infoman.liquideconomycs.trie;

import android.content.SharedPreferences;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;
import com.infoman.liquideconomycs.Core;
import com.infoman.liquideconomycs.Utils;

import java.io.IOException;
import java.util.Date;

import androidx.preference.PreferenceManager;

import static org.bitcoinj.core.Utils.sha256hash160;


public class Node extends ChildMap {

    public static byte
            ROOT        = 1,
            BRANCH      = 2,
            LEAF        = 3;
    public Core app;
    public long position, maxAge;
    public byte[] age, hash;
    public PubKey nodeKey;
    public byte type;
    public boolean change;
    public int space;

    public Node(Core context, NodeParams nodeParams) throws IOException {
        super(32);
        app = context;
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(app);
        maxAge = Long.parseLong(sharedPref.getString("maxAge", "30"));
        age = nodeParams.age;
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

        app.file.get(age, position, 0, 2);

        if(type == ROOT) {
            app.file.get(hash, position + 2, 0, 20);
            loadRootMap();
            //loadChilds();
        }else {
            type = app.file.readByte();
            int pubKeySize = app.file.readByte();
            byte[] pubKey = new byte[pubKeySize];
            app.file.get(pubKey, position + 4, 0, pubKeySize);
            nodeKey = new PubKey(type, pubKey);
            app.file.get(mapBytes, position + 4 + pubKeySize + 20, 0, mapSize);
            calcSpace();
            //child no need auto load childs because they loaded in constructTrieByKey()
        }

    }

    private void loadRootMap() throws IOException {
        long p = position + 22;
        for(int i = 0; i < (mapSize * 8); i++) {
            byte[] b = new byte[8];
            app.file.get(b, p + (i * 8), 0, 8);
            long pos = Longs.fromByteArray(b);
            if(pos > 0L){
                setInMap(i, true);
            }
        }
    }

    public void loadChilds() throws IOException {
        long positionInFile = type == ROOT ? position + 22 : position + (4 + nodeKey.nodePubKey.length + 20 + mapSize);
        for(int i = 0; i < (mapSize * 8); i++) {
            if (getInMap(i)) {
                int posInArray = type == ROOT ? i : getPos(i) - 1;
                byte[] b = new byte[type == LEAF ? 2 : 8];
                app.file.get(b, positionInFile + (posInArray * (type == LEAF ? 2 : 8)), 0, (type == LEAF ? 2 : 8));
                if(type == LEAF){
                    mapAges[i] = b;
                }else {
                    long pos = Longs.fromByteArray(b);
                    NodeParams nodeParams = new NodeParams();
                    nodeParams.age = new byte[2];
                    nodeParams.type = BRANCH;
                    nodeParams.pos = pos;
                    nodeParams.hash = getHash(pos);
                    nodeParams.newble = false;
                    mapChilds[i] = new Node(app, nodeParams);
                }
            }else{
                if(type == LEAF){
                    mapAges[i] = null;
                }else {
                    mapChilds[i] = null;
                }
            }
        }
        calcHash();
    }

    private void loadChild(int pubKeyInt) throws IOException {
        long positionInFile = type == ROOT ? position + 22 : position + (4 + nodeKey.nodePubKey.length + 20 + mapSize);
        if (getInMap(pubKeyInt)) {
            int posInArray = type == ROOT ? pubKeyInt : getPos(pubKeyInt) - 1;
            byte[] b = new byte[type == LEAF ? 2 : 8];
            app.file.get(b, positionInFile + (posInArray * (type == LEAF ? 2 : 8)), 0, (type == LEAF ? 2 : 8));
            if(type == LEAF){
                mapAges[pubKeyInt] = b;
            }else {
                long pos = Longs.fromByteArray(b);
                NodeParams nodeParams = new NodeParams();
                nodeParams.age = new byte[2];
                nodeParams.type = BRANCH;
                nodeParams.pos = pos;
                nodeParams.hash = getHash(pos);
                nodeParams.newble = false;
                mapChilds[pubKeyInt] = new Node(app, nodeParams);
            }
        }
    }

    //if position is clear/new, (replace old node/put new node/delete node),  on new place
    //and insert free space in database
    //if change rewrite node
    protected void insert(byte[] pubKey, byte[] newAge) throws IOException {
        boolean newSpace = false;
        if (type == ROOT) {
            constructTrieByKey(pubKey);
        }else{
            nodeKey.initNodeKyeFieldsByNewKey(pubKey);
        }
        if(nodeKey.getEqualsPrefixFromNewKeyAndNodePubKey() || type == ROOT) {
            int intForChild = nodeKey.getPlaceIntForChildFromNewKeySuffix(pubKey);
            if (type == LEAF) {
                if (getInMap(intForChild)) {
                    if (Utils.compareDate(newAge, mapAges[intForChild], 0L)) {
                        mapAges[intForChild] = age;
                        if (Utils.compareDate(age, newAge, 0L)) {
                            age = newAge;
                        }
                        calcHash();
                        change = true;
                        app.file.saveNodeNewStateBlobInDB(this, false);
                    }
                } else {
                    setInMap(intForChild, true);
                    mapAges[intForChild] = newAge;
                    if (Utils.compareDate(age, newAge, 0L)) {
                        age = newAge;
                    }
                    calcHash();
                    calcSpace();
                    change = true;
                    app.insertFreeSpaceWitchCompressTrieFile(position, space);
                    app.file.saveNodeNewStateBlobInDB(this, true);
                }
            }else{//get/set child ref
                Node ref;
                if (getInMap(intForChild)) {
                    ref = mapChilds[intForChild];
                }else{
                    setInMap(intForChild, true);

                    NodeParams nodeParams = new NodeParams();
                    nodeParams.age = newAge;
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
                ref.insert(nodeKey.getKeyForAddInChildFromNewKeySuffix(), newAge);
                change = ref.change;
                if(change){
                    if (Utils.compareDate(age, newAge, 0L)) {
                        age = newAge;
                    }
                    calcHash();
                    app.file.saveNodeNewStateBlobInDB(this, newSpace);
                }
            }
        }else{
            //reconstruct this node(leaf or branch)
            //get int
            int intForNewChild = nodeKey.getPlaceIntForChildFromNewKeySuffix(pubKey);
            int intForOldChild = nodeKey.getPlaceIntForChildFromNodeKeySuffix(pubKey);

            //create new node and copy this node to new node
            NodeParams nodeParams = new NodeParams();
            nodeParams.age = age;
            nodeParams.type = type;
            nodeParams.pubKey = nodeKey.getKeyForNewChildFromNodeKey();
            nodeParams.pos = 0L;
            nodeParams.hash = new byte[20];
            nodeParams.newble = true;

            Node ref = new Node(app, nodeParams);
            loadChilds();
            ref.mapBytes = mapBytes;
            ref.mapChilds = mapChilds;
            ref.mapAges = mapAges;
            ref.calcHash();
            ref.calcSpace();
            app.file.saveNodeNewStateBlobInDB(ref, true);

            //create new node and insert suffix key and age
            nodeParams = new NodeParams();
            nodeParams.age = newAge;
            nodeParams.type = LEAF;
            nodeParams.pubKey = nodeKey.getKeyForNewChildFromNewPubKey();
            nodeParams.pos = 0L;
            nodeParams.hash = new byte[20];
            nodeParams.newble = true;

            Node newRef = new Node(app, nodeParams);
            newRef.insert(nodeKey.getKeyForAddInChildFromNewKeySuffix(), newAge);

            //change this node to common node
            app.insertFreeSpaceWitchCompressTrieFile(position, space);
            nodeKey = new PubKey(BRANCH, nodeKey.commonKey);
            type = BRANCH;
            //clear map ch ag
            mapBytes = new byte[mapSize];
            mapChilds = new Node[mapSize * 8];
            mapAges = new byte[mapSize * 8][2];
            //link new and old nodes to this
            setInMap(intForNewChild, true);
            setInMap(intForOldChild, true);
            mapChilds[intForNewChild] = newRef;
            mapChilds[intForOldChild] = ref;
            change = true;
            if (Utils.compareDate(age, newAge, 0L)) {
                age = newAge;
            }
            calcHash();
            calcSpace();

            app.file.saveNodeNewStateBlobInDB(this, true);
        }
        if(type==ROOT && change){
            app.file.transaction();
        }
    }

    private void constructTrieByKey(byte[] pubKey) throws IOException {
        nodeKey.initNodeKyeFieldsByNewKey(pubKey);
        if(nodeKey.getEqualsPrefixFromNewKeyAndNodePubKey()) {
            change = false;
            loadChilds();
            int pubKeyInt = nodeKey.getPlaceIntForChildFromNewKeySuffix(pubKey);
            byte[] keyChild = nodeKey.getKeyForAddInChildFromNewKeySuffix();
            if (getInMap(pubKeyInt)) {
                //if (type != ROOT) {//root loaded in constructor
                app.file.saveNodeOldStateBlobInDB(position, type == ROOT ? 2070 : space);
                //}
                if (type != LEAF) {//leaf have no childs
                    mapChilds[pubKeyInt].constructTrieByKey(keyChild);
                }
            }
        }
    }

    public byte[] find(byte[] pubKey) throws IOException {
        nodeKey.initNodeKyeFieldsByNewKey(pubKey);
        if(nodeKey.getEqualsPrefixFromNewKeyAndNodePubKey()) {
            int pubKeyInt = nodeKey.getPlaceIntForChildFromNewKeySuffix(pubKey);
            loadChild(pubKeyInt);
            byte[] keyChild = nodeKey.getKeyForAddInChildFromNewKeySuffix();
            if (getInMap(pubKeyInt)) {
                if (type != LEAF) {//leaf have no childs
                    mapChilds[pubKeyInt].find(keyChild);
                }else{
                    return mapAges[pubKeyInt];
                }
            }
        }
        return new byte[0];
    }

    public void findOldestNode(byte[] key) throws IOException {
        if (Utils.compareDate(Utils.ageToBytes(new Date()), age, maxAge)) {
            loadChilds();
            byte[] fullKey = Bytes.concat(key, nodeKey.nodePubKey);
            for(int i=0; i < mapSize * 8; i++) {
                if (getInMap(i)) {
                    byte[] sKey=new byte[1];
                    sKey[0] = (byte) i;
                    if (type != LEAF) {//leaf have no childs
                        mapChilds[i].findOldestNode(Bytes.concat(fullKey, sKey));
                    }else{
                        if (Utils.compareDate(Utils.ageToBytes(new Date()), mapAges[i], maxAge)) {
                            app.addForDelete(Bytes.concat(fullKey, sKey));
                        }
                    }
                }
            }
        }
    }

    public void calcSpace() {
        space = 4 + nodeKey.nodePubKey.length + 20 + mapSize + (getCountInMap() * (type == LEAF ? 2 : 8));
    }

    private void calcHash() {
        byte[] digest = Bytes.concat(nodeKey.nodePubKey, mapBytes);
        byte[] emptyHash = new byte[20];
        for (int i = 0; i < mapChilds.length; i++) {
            if(getInMap(i) && mapChilds[i] != null && mapChilds[i].hash != emptyHash){
                if(type==LEAF) {
                    digest = Bytes.concat(digest, mapAges[i]);
                }else{
                    digest = Bytes.concat(digest, mapChilds[i].hash);
                }
            }
        }
        hash = sha256hash160(digest);
    }

    protected byte[] getHash(long pos) throws IOException {
        byte[] hash = new byte[20];
        if (pos == 0L) {
            app.file.seek(pos+2);
        } else {
            app.file.seek(pos+3);
            byte keySize = app.file.readByte();
            app.file.seek(pos+ 4 + keySize);
        }
        app.file.read(hash, 0, 20);
        return hash;
    }

}


