package com.infoman.liquideconomycs.trie;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;
import com.infoman.liquideconomycs.Core;
import com.infoman.liquideconomycs.Utils;

import java.io.IOException;
import java.util.Arrays;

import static com.infoman.liquideconomycs.Utils.LEAF;
import static com.infoman.liquideconomycs.Utils.ROOT;
import static org.bitcoinj.core.Utils.sha256hash160;


public class TrieNode extends ChildMap {

    public Core app;
    public long position;
    public byte[] age, nodeKey, hash;
    public byte type, keySize;
    public boolean change;
    public int space;

    public TrieNode(Core context, long pos, byte[] h, boolean newble) throws IOException {
        super(32);
        app = context;
        position = pos;
        hash = h;
        age = new byte[2];
        if(!newble){
            loadNode();
        }else{
            change = true;
        }

    }

    private void loadNode() throws IOException {
        app.trie.seek(position);
        app.trie.read(age, 0, 2);
        if(position == 0L){
            type = ROOT;
            nodeKey = new byte[0];
        }else {
            type = app.trie.readByte();
            keySize = app.trie.readByte();
            nodeKey = new byte[keySize];
            app.trie.read(nodeKey, 0, keySize);
            app.trie.seek(position + 4 + keySize + 20); //skip hash
            app.trie.read(mapBytes, 0, 32);
        }
        if(type == ROOT) {
            loadChilds();
        }
    }

    public void loadChilds() throws IOException {
        for(int i = 0; i < (mapSize * 8); i++) {
            if (getInMap(i)) {
                long p = type == ROOT ? position + 22 : (4 + keySize + 20 + mapSize);
                app.trie.seek(p + (i * (type == LEAF ? 2 : 8)));
                byte[] b = new byte[type == LEAF ? 2 : 8];
                app.trie.read(b, 0, (type == LEAF ? 2 : 8));
                if(type == LEAF){
                    mapAges[i] = b;
                }else {
                    long pos = Longs.fromByteArray(b);
                    mapChilds[i] = new TrieNode(app, pos, getHash(pos), false);
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
    //insertFreeSpaceWitchCompressTrieFile(long pos, int space)
    protected boolean insert(byte[] pubKey, byte[] newAge) throws IOException {
        TrieNode ref = null;
        TriePubKey pk = constructTrieByKey(pubKey);
        if(Arrays.equals(pk.prefix, nodeKey) || type == ROOT) {
            int intForChild = pk.getKeyIntForChild();
            if (type == LEAF) {
                if (getInMap(intForChild)) {
                    if (Utils.compareDate(newAge, mapAges[intForChild], 0L)) {
                        mapAges[intForChild] = age;
                        if (Utils.compareDate(age, newAge, 0L)) {
                            age = newAge;
                        }
                        calcHash();
                        change = true;
                        return true;
                    }
                } else {
                    setInMap(intForChild, true);
                    mapAges[intForChild] = age;
                    calcHash();
                    change = true;
                    position = 0L;
                    app.insertFreeSpaceWitchCompressTrieFile(position, calcSpace());
                    return true;
                }
            }else{//get/set child ref
                if (getInMap(intForChild)) {
                    ref = mapChilds[intForChild];
                }else{
                    setInMap(intForChild, true);
                    ref = new TrieNode(app, 0L, new byte[20], true);
                    ref.type = LEAF;
                    mapChilds[intForChild] = ref;
                    position = 0L;
                    if (type != ROOT) {
                        app.insertFreeSpaceWitchCompressTrieFile(position, calcSpace());
                    }
                }
                if(ref!=null && ref.insert(pk.getKeyChild(), newAge)){
                    if (Utils.compareDate(age, newAge, 0L)) {
                        age = newAge;
                    }
                    change = true;
                    calcHash();
                    return true;
                }
                return false;
            }
        }else{
            //reconstruct this node(leaf or branch)
            //todo

        }
        return false;
    }

    private TriePubKey constructTrieByKey(byte[] pubKey) throws IOException {
        TriePubKey pk = new TriePubKey(pubKey, type, nodeKey);
        if(Arrays.equals(pk.prefix, nodeKey)) {
            int pubKeyInt = pk.getKeyIntForChild();
            byte[] keyChild = pk.getKeyChild();
            if ((type == LEAF ? mapAges.length == 0 : mapChilds.length == 0) && getInMap(pubKeyInt)) {
                loadChilds();
                if (type != LEAF) {
                    TriePubKey res = mapChilds[pubKeyInt].constructTrieByKey(keyChild);
                }
            }
        }
        return pk;
    }

    private int calcSpace() {
        space = 0;
        return space;
    }

    private void calcHash() {
        byte[] digest = Bytes.concat(nodeKey, mapBytes);
        for (int i = 0; i < mapChilds.length; i++) {
            if(getInMap(i)){
                if(type==LEAF) {
                    digest = Bytes.concat(digest, mapAges[i]);
                }else{
                    digest = Bytes.concat(digest, mapChilds[i].hash);
                }
            }else{
                continue;
            }
        }
        hash = sha256hash160(digest);
    }

    protected byte[] getHash(long pos) throws IOException {
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



 }
