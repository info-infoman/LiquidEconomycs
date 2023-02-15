package com.infoman.liquideconomycs.trie;

import java.io.IOException;
import java.util.Arrays;

import static com.infoman.liquideconomycs.trie.Node.ROOT;
import static com.infoman.liquideconomycs.Utils.getBytesPart;

public class PubKey {
    public byte[] nodePubKey, prefix, common, keySuffix, nodeSuffix;
    byte type;

    public PubKey(byte nodeType, byte[] nodeKey){
        type = nodeType;
        nodePubKey = nodeKey;
        //pubKey = pk;
    }

    public void initNodeKyeByNewKey(byte[] pubKey){
        prefix = getBytesPart(pubKey, 0, nodePubKey.length);
        getCommonKey();
        keySuffix = getBytesPart(pubKey, common.length, pubKey.length - common.length);
        if(type != ROOT) {
            nodeSuffix = getBytesPart(nodePubKey, common.length, nodePubKey.length - common.length);
        }
    }

    public int getKeyIntForChild(byte[] pubKey) throws IOException {
        int pubKeyInt = 256;
        if(type == ROOT){
            pubKeyInt = pubKey[0] & 0xFF;
        }else{
            pubKeyInt = keySuffix[0] & 0xFF;
        }
        return pubKeyInt;
    }

    public int getNodeIntForChild(byte[] pubKey) throws IOException {
        int pubKeyInt = 256;
        if(type == ROOT){
            pubKeyInt = pubKey[0] & 0xFF;
        }else{
            pubKeyInt = nodeSuffix[0] & 0xFF;
        }
        return pubKeyInt;
    }

    public byte[] getKeyChild() throws IOException {
        return getBytesPart(keySuffix, 1, keySuffix.length - 1);
    }

    public byte[] getNodeChild() throws IOException {
        return getBytesPart(nodeSuffix, 1, nodeSuffix.length - 1);
    }

    public byte[] getKeyNewChild() throws IOException {
        return getBytesPart(keySuffix, 1, keySuffix.length-2);
    }

    public byte[] getNodeNewChild() throws IOException {
        return getBytesPart(nodeSuffix, 1, nodeSuffix.length-1);
    }

    public void getCommonKey() {
        common = new byte[0];
        for(int i = 1; i < nodePubKey.length+1; i++){
            byte[] suffixKey = getBytesPart(prefix, 0, nodePubKey.length-i);
            if(Arrays.equals(suffixKey, getBytesPart(nodePubKey, 0, nodePubKey.length-i))){
                common = suffixKey;
            }
        }
    }

}
