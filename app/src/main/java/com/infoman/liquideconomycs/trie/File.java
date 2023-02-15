package com.infoman.liquideconomycs.trie;

import com.infoman.liquideconomycs.Utils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Date;

public class File extends RandomAccessFile {
    //todo изолировать запись
    public File(String name, String mode) throws FileNotFoundException {
        super(name, mode);
        try {
            if (length() == 0) {
                setLength(0);
                byte[] trieTmp = new byte[2070];
                write(trieTmp);
                seek(0);
                write(Utils.ageToBytes(new Date()));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void get(byte[] b, long pos, int off, int len) throws IOException {
        seek(pos);
        read(b, off, len);
    }

    //todo изолировать запись
    public void set(long pos, byte[] b) throws IOException {
        seek(pos);
        write(b);
    }
}
