package com.infoman.liquideconomycs.trie;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;
import com.infoman.liquideconomycs.Core;
import com.infoman.liquideconomycs.Utils;

import java.io.IOException;
import java.util.Arrays;
import java.util.Date;

import static com.infoman.liquideconomycs.Utils.ageToBytes;
import static org.bitcoinj.core.Utils.sha256hash160;


public class Node extends ChildMap {

    public static byte
            ROOT        = 1,
            BRANCH      = 2,
            LEAF        = 3;

    public Core app;
    public long position;
    public byte[] age, hash;
    public PubKey nodeKey;
    public byte type;
    public boolean change;
    public int space;


    public Node(Core context, NodeParams nodeParams) throws IOException {
        super(32);
        app = context;
        age = nodeParams.age;
        type = nodeParams.type;
        nodeKey = new PubKey(type, nodeParams.pubKey);
        position = nodeParams.pos;
        hash = nodeParams.hash;
        age = ageToBytes(new Date());//for new node set new date, for old loaded in loadNode()
        if(!nodeParams.newble){
            loadNode();
        }else{
            change = true;
        }
    }

    private void loadNode() throws IOException {
        byte[] pubKey;
        if ((type == ROOT && position != 0L)) throw new AssertionError();

        if(type == ROOT) {
            loadRootMap();
            loadChilds();
        }else {
            app.file.get(age, position, 0, 2);
            type = app.file.readByte();
            pubKey = new byte[app.file.readByte()];
            app.file.get(pubKey, position, 4, pubKey.length);
            app.file.get(mapBytes, position + 4 + pubKey.length + 20, 0, mapSize);
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
                byte[] b = new byte[type == LEAF ? 2 : 8];
                app.file.get(b, positionInFile + (i * (type == LEAF ? 2 : 8)), 0, (type == LEAF ? 2 : 8));
                if(type == LEAF){
                    mapAges[i] = b;
                }else {
                    long pos = Longs.fromByteArray(b);
                    NodeParams nodeParams = new NodeParams();
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

    //if position is clear/new, (replace old node/put new node/delete node),  on new place
    //and insert free space in database
    //if change rewrite node
    protected void insert(byte[] pubKey, byte[] newAge) throws IOException {
        if (type == ROOT) {
            constructTrieByKey(pubKey);
        }else{
            nodeKey.initNodeKyeByNewKey(pubKey);
        }

        if(Arrays.equals(nodeKey.prefix, nodeKey.nodePubKey) || type == ROOT) {
            int intForChild = nodeKey.getKeyIntForChild(pubKey);
            if (type == LEAF) {
                if (getInMap(intForChild)) {
                    if (Utils.compareDate(newAge, mapAges[intForChild], 0L)) {
                        mapAges[intForChild] = age;
                        calcHash();
                        change = true;
                    }
                } else {
                    setInMap(intForChild, true);
                    mapAges[intForChild] = age;
                    calcHash();
                    change = true;
                    app.insertFreeSpaceWitchCompressTrieFile(position, calcSpace());
                    position = 0L;
                }
            }else{//get/set child ref
                Node ref;
                if (getInMap(intForChild)) {
                    ref = mapChilds[intForChild];
                }else{
                    setInMap(intForChild, true);

                    NodeParams nodeParams = new NodeParams();
                    nodeParams.type = LEAF;
                    nodeParams.pubKey = nodeKey.getKeyNewChild();
                    nodeParams.pos = 0L;
                    nodeParams.hash = new byte[20];
                    nodeParams.newble = true;

                    ref = new Node(app, nodeParams);

                    mapChilds[intForChild] = ref;
                    if (type != ROOT) {
                        app.insertFreeSpaceWitchCompressTrieFile(position, calcSpace());
                    }
                    position = 0L;
                }
                ref.insert(nodeKey.getKeyChild(), newAge);
                change = ref.change;
                calcHash();
            }
        }else{
            //reconstruct this node(leaf or branch)
            //get int
            int intForNewChild = nodeKey.getKeyIntForChild(pubKey);
            int intForOldChild = nodeKey.getNodeIntForChild(pubKey);

            //create new node and copy this node to new node
            NodeParams nodeParams = new NodeParams();
            nodeParams.type = type;
            nodeParams.pubKey = nodeKey.getNodeNewChild();
            nodeParams.pos = 0L;
            nodeParams.hash = new byte[20];
            nodeParams.newble = true;//todo

            Node ref = new Node(app, nodeParams);
            ref.age = age;
            ref.mapBytes = mapBytes;
            ref.mapChilds = mapChilds;
            ref.mapAges = mapAges;
            ref.calcHash();

            //create new node and insert suffix key and age
            nodeParams = new NodeParams();
            nodeParams.type = LEAF;
            nodeParams.pubKey = nodeKey.getKeyNewChild();
            nodeParams.pos = 0L;
            nodeParams.hash = new byte[20];
            nodeParams.newble = true;

            Node newRef = new Node(app, nodeParams);
            newRef.insert(nodeKey.getKeyChild(), newAge);

            //change this node to common node
            nodeKey = new PubKey(BRANCH, nodeKey.common);;
            type = BRANCH;
            //clear map
            mapBytes = new byte[mapSize];
            mapChilds = new Node[mapSize * 8];
            mapAges = new byte[mapSize * 8][2];
            //link new and old nodes to this
            setInMap(intForNewChild, true);
            setInMap(intForOldChild, true);
            mapChilds[intForNewChild] = newRef;
            mapChilds[intForOldChild] = ref;
            change = true;
            app.insertFreeSpaceWitchCompressTrieFile(position, calcSpace());
            calcHash();
            position = 0L;
        }
        //for all node type compare age and change if node is younger
        if (Utils.compareDate(age, newAge, 0L) && change) {
            age = newAge;
        }
    }

    private void constructTrieByKey(byte[] pubKey) throws IOException {
        nodeKey.initNodeKyeByNewKey(pubKey);
        if(Arrays.equals(nodeKey.prefix, nodeKey.nodePubKey)) {
            int pubKeyInt = nodeKey.getKeyIntForChild(pubKey);
            byte[] keyChild = nodeKey.getKeyChild();
            if (getInMap(pubKeyInt)) {
                if (type != ROOT) {//root loaded in constructor
                    loadChilds();
                }
                if (type != LEAF) {//leaf have no childs
                    mapChilds[pubKeyInt].constructTrieByKey(keyChild);
                }
            }
        }
    }

    private int calcSpace() {
        //todo
        space = 0;
        return space;
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
