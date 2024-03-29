package com.infoman.liquideconomycs.trie;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;

import com.infoman.liquideconomycs.Core;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

import static com.infoman.liquideconomycs.Utils.getDayMilliByIndex;
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
                byte[] trieTmp = new byte[2068];
                write(trieTmp);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        virtualFilePointer = 0L;
    }

    //Сохраняет новый узел
    public void saveNodeNewStateBlobInDB(Node node, boolean newPlace) throws IOException {
        byte[] blob;
        long pos = 0L;
        if(virtualFilePointer == 0L){
            virtualFilePointer = this.length();
        }
        if(node.type != ROOT){
            if(newPlace) {
                Cursor query = app.getFreeSpace(getDayMilliByIndex(node.index), node.space);
                seek(length());
                pos = this.virtualFilePointer;

                if (query.getCount() > 0 && query.moveToFirst()) {
                    @SuppressLint("Range") int id = query.getInt(query.getColumnIndex("id"));
                    int posColIndex = query.getColumnIndex("pos");
                    int spaceColIndex = query.getColumnIndex("space");
                    long p = query.getLong(posColIndex);
                    int s = query.getInt(spaceColIndex);
                    if (p > 0) {
                        app.updateFreeSpace(getDayMilliByIndex(node.index), id, p, node.space, s);
                        pos = p;
                    }
                } else {
                    this.virtualFilePointer = this.virtualFilePointer + node.space;
                }
                query.close();
            }else{
                pos = node.position;
            }
        }
        blob = node.getBlob(false);
        set(pos, blob);
        node.position = pos;
    }

    //Зачитывает кусок байтов по позиции
    public void get(byte[] b, long pos, int off, int len) throws IOException {
        seek(pos);
        read(b, off, len);
    }

    //todo изолировать запись
    public void set(long pos, byte[] b) throws IOException {
        seek(pos);
        write(b);
    }

    public void transaction() throws IOException {
        this.virtualFilePointer = this.length();
    }
}
