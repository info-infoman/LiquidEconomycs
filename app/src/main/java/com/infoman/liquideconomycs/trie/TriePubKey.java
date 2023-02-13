package com.infoman.liquideconomycs.trie;

import java.io.IOException;
import java.util.Arrays;

import static com.infoman.liquideconomycs.Utils.ROOT;
import static com.infoman.liquideconomycs.Utils.getBytesPart;

class TriePubKey {
    public byte[] pubKey, prefix, common, suffix;
    int nodeType;

    public TriePubKey(byte[] pk, int nT, byte[] nodeKey){
        pubKey = pk;
        prefix = getBytesPart(pubKey, 0, nodeKey.length);
        common = getCommonKey(nodeKey);
        assert common != null && nT != ROOT;
        suffix = getBytesPart(pubKey, nodeKey.length, pubKey.length-nodeKey.length);
        nodeType = nT;
    }

    public int getKeyIntForChild() throws IOException {
        int pubKeyInt = 256;
        if(nodeType == ROOT){
            pubKeyInt = pubKey[0] & 0xFF;
        }else{
            pubKeyInt = suffix[0] & 0xFF;
        }
        return pubKeyInt;
    }

    public byte[] getKeyChild() throws IOException {
        return getBytesPart(suffix, 1, suffix.length-1);
    }

    public byte[] getCommonKey(byte[] nodeKey) {
        for(int i = 1; i < nodeKey.length+1; i++){
            byte[] sK = getBytesPart(prefix, 0, nodeKey.length-i);
            if(Arrays.equals(sK, getBytesPart(nodeKey, 0, nodeKey.length-i))){
                return sK;
            }
        }
        return null;
    }

}
