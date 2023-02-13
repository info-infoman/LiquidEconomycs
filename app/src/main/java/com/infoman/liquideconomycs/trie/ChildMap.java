package com.infoman.liquideconomycs.trie;

import com.google.common.primitives.Bytes;

import java.io.IOException;
import java.util.BitSet;

public class ChildMap{
    public int         mapSize;
    public byte[]      mapBytes;
    public TrieNode[]  mapChilds;
    public byte[][]    mapAges;
    public BitSet prepare = new BitSet();

    public ChildMap(int maxSize) throws IOException {
        mapSize = maxSize;
        mapBytes = new byte[mapSize];
    }

    //todo проверить применение если нет в мапе
    public int getPos(int index){
        int result = 0;
        prepare = BitSet.valueOf(mapBytes);
        int count = getCountInMap();
        for(int i = 0; i < index + 1; i++){
            if(i == count) break;
            if(prepare.get(i)) result = result + 1;
        }
        return result;
    }

    public int getOne(boolean all){
        prepare = BitSet.valueOf(mapBytes);
        int count = getCountInMap();
        for(int i=0; i < (count*8); i++) {
            if (prepare.get(i)) {
                return i;
            }
        }
        return 0;
    }

    public int getCountInMap(){
        return BitSet.valueOf(mapBytes).cardinality();
    }

    public boolean getInMap(int index){
        return BitSet.valueOf(mapBytes).get(index);
    }

    public void setInMap(int index, boolean operation){
        prepare = BitSet.valueOf(mapBytes);
        prepare.set(index, operation);
        mapBytes = Bytes.concat(prepare.toByteArray(), new byte[mapSize - prepare.toByteArray().length]);
    }

}
