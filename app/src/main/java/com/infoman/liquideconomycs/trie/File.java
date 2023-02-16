package com.infoman.liquideconomycs.trie;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;
import com.infoman.liquideconomycs.Core;
import com.infoman.liquideconomycs.Utils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Date;

import static com.infoman.liquideconomycs.trie.Node.LEAF;
import static com.infoman.liquideconomycs.trie.Node.ROOT;

public class File extends RandomAccessFile {
    Core app;
    long virtualFilePointer;
    //todo изолировать запись
    public File(Context context, String name, String mode) throws FileNotFoundException {
        super(name, mode);
        app = (Core) context;
        try {
            if (length() == 0L) {
                setLength(0L);
                byte[] trieTmp = new byte[2070];
                write(trieTmp);
                seek(0L);
                write(Utils.ageToBytes(new Date()));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        virtualFilePointer = 0L;
    }

    public void saveNodeNewStateBlobInDB(Node node, boolean newPlace) throws IOException {
        byte[] blob;
        long pos = 0L;
        if(virtualFilePointer == 0L){
            virtualFilePointer = app.file.length();
        }
        if(node.type != ROOT){
            Cursor query = app.getFreeSpace(node.space);
            seek(length());
            pos = app.file.virtualFilePointer;

            if (query.getCount() > 0 && query.moveToFirst()) {
                int id  = query.getInt(query.getColumnIndex("id"));
                int posColIndex = query.getColumnIndex("pos");
                int spaceColIndex = query.getColumnIndex("space");
                long p = query.getLong(posColIndex);
                int s = query.getInt(spaceColIndex);
                if( p > 0 ) {
                    app.deleteFreeSpace(id, p, node.space, s);
                    pos=p;
                }
            }else{
                app.file.virtualFilePointer = app.file.virtualFilePointer + node.space;
            }
            query.close();

            byte[] typeAndKeySze = new byte[2];
            typeAndKeySze[0] = node.type;
            typeAndKeySze[1] = (byte) node.nodeKey.nodePubKey.length;
            blob = Bytes.concat(node.age, typeAndKeySze, node.nodeKey.nodePubKey, node.hash, node.mapBytes);

            for(int i = 0; i < (node.mapSize * 8); i++) {
                if (node.getInMap(i)) {
                    if(node.type != LEAF) {
                        blob = Bytes.concat(blob, Longs.toByteArray(node.mapChilds[i].position));
                    }else{
                        blob = Bytes.concat(blob, node.mapAges[i]);
                    }
                }
            }
        }else{
            blob = Bytes.concat(node.age, node.hash);
            for(int i = 0; i < (node.mapSize * 8); i++) {
                if (node.getInMap(i)) {
                    blob = Bytes.concat(blob, Longs.toByteArray(node.mapChilds[i].position));
                }else{
                    blob = Bytes.concat(blob, new byte[8]);
                }
            }
        }
        Log.d("Trie insert PubKey", String.valueOf(pos));
        app.insertNodeBlob(pos, blob, "cacheNewNodeBlobs");
        node.position = pos;
    }

    public void saveNodeOldStateBlobInDB(long pos, int len) throws IOException {
        byte[] blob = new byte[len];
        seek(pos);
        read(blob, 0, len);
        app.insertNodeBlob(pos, blob, "cacheOldNodeBlobs");
    }

    public void get(byte[] b, long pos, int off, int len) throws IOException {
        seek(pos);
        read(b, off, len);
    }

    //todo изолировать запись
    public void set(long pos, byte[] b) throws IOException {
        // app.insertFreeSpaceWitchCompressTrieFile(position, calcSpace());
        seek(pos);
        write(b);
    }

    public void transaction() throws IOException {
        Cursor query = app.getNodeBlobs("cacheNewNodeBlobs");
        while(query.moveToNext()) {
            int posColIndex = query.getColumnIndex("pos");
            int nodeColIndex = query.getColumnIndex("node");
            long pos = query.getLong(posColIndex);
            byte[] blob = query.getBlob(nodeColIndex);
            seek(pos);
            write(blob);
        }
        query.close();
        app.file.virtualFilePointer = app.file.length();
        app.clearTable("cacheOldNodeBlobs");
        app.clearTable("cacheNewNodeBlobs");
    }

    public void recovery() throws IOException {
        app.clearTable("cacheNewNodeBlobs");
        Cursor query = app.getNodeBlobs("cacheOldNodeBlobs");
        if(query.getCount() > 0) {
            while (query.moveToNext()) {
                int posColIndex = query.getColumnIndex("pos");
                int nodeColIndex = query.getColumnIndex("node");
                long pos = query.getLong(posColIndex);
                byte[] blob = query.getBlob(nodeColIndex);
                seek(pos);
                write(blob);
            }
            query.close();
            app.file.virtualFilePointer = app.file.length();
            app.clearTable("cacheOldNodeBlobs");
        }
    }
}
