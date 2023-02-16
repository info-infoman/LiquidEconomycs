package com.infoman.liquideconomycs.trie;

import java.io.IOException;
import java.util.Arrays;

import static com.infoman.liquideconomycs.trie.Node.ROOT;
import static com.infoman.liquideconomycs.Utils.getBytesPart;

public class PubKey {
    public byte[] nodePubKey, prefixFromNewKey, commonKey, newKeySuffix, nodeKeySuffix;
    byte type;

    public PubKey(byte nodeType, byte[] nodeKey) {
        type = nodeType;
        nodePubKey = nodeKey;
    }


    public void initNodeKyeFieldsByNewKey(byte[] pubKey) throws IOException {
        prefixFromNewKey = getBytesPart(pubKey, 0, nodePubKey.length);
        getCommonKey();
        newKeySuffix = getBytesPart(pubKey, commonKey.length, pubKey.length - commonKey.length);
        if(type != ROOT) {
            nodeKeySuffix = getBytesPart(nodePubKey, commonKey.length, nodePubKey.length - commonKey.length);
        }
    }

    public int getPlaceIntForChildFromNewKeySuffix(byte[] pubKey) throws IOException {
        int pubKeyInt = 256;
        if(type == ROOT){
            pubKeyInt = pubKey[0] & 0xFF;
        }else{
            pubKeyInt = newKeySuffix[0] & 0xFF;
        }
        return pubKeyInt;
    }

    public int getPlaceIntForChildFromNodeKeySuffix(byte[] pubKey) throws IOException {
        int pubKeyInt = 256;
        if(type == ROOT){
            pubKeyInt = pubKey[0] & 0xFF;
        }else{
            pubKeyInt = nodeKeySuffix[0] & 0xFF;
        }
        return pubKeyInt;
    }

    public byte[] getKeyForAddInChildFromNewKeySuffix() throws IOException {
        return getBytesPart(newKeySuffix, 1, newKeySuffix.length - 1);
    }

    public byte[] getKeyForAddInChildFromNodeKeySuffix() throws IOException {
        return getBytesPart(nodeKeySuffix, 1, nodeKeySuffix.length - 1);
    }

    public byte[] getKeyForNewChildFromNewPubKey() throws IOException {
        return getBytesPart(newKeySuffix, 1, newKeySuffix.length-2);
    }

    public byte[] getKeyForNewChildFromNodeKey() throws IOException {
        return getBytesPart(nodeKeySuffix, 1, nodeKeySuffix.length-1);
    }

    public void getCommonKey() throws IOException {
        commonKey = new byte[0];
        for(int i = 1; i < nodePubKey.length+1; i++){
            byte[] suffixKey = getBytesPart(prefixFromNewKey, 0, nodePubKey.length-i);
            if(Arrays.equals(suffixKey, getBytesPart(nodePubKey, 0, nodePubKey.length-i))){
                commonKey = suffixKey;
            }
        }
    }

    public boolean getEqualsPrefixFromNewKeyAndNodePubKey() throws IOException {
        return Arrays.equals(prefixFromNewKey, nodePubKey);
    }

}
