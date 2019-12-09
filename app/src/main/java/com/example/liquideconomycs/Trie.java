package com.example.liquideconomycs;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.BitSet;

import static org.bitcoinj.core.Utils.sha256hash160;

public class Trie {
    private RandomAccessFile trie, freeSpace;
    private byte[] hashSize = new byte[20];
    private static byte[] rootPos = Longs.toByteArray(0L);
    private static byte ROOT = 1; //
    private static byte BRANCH = 2;
    private static byte LEAF = 3;
    private static byte FOUND = 0;
    private static byte NOTFOUND = 1;
    Trie(String nodeDir) throws FileNotFoundException {
        trie = new RandomAccessFile(nodeDir+ "/trie.dat", "rw");
        freeSpace = new RandomAccessFile(nodeDir+ "/freeSpace.dat", "rw");
    }

    //we need save 10 000 000 000+ account for all people
    // and we need have sync func like "all for all" between two people(personal accounts trie), who met by chance
    //Compress by key & adaptive points size & save age in leaf (Lp - leaf point, Bp - branch point, Ad - account data):
    //     Bp     Lp  Ad      Bp  Bp    Lp  Ad       Bp  Bp  Bp  lp  Ad
    //1)R(FF)FFFF(FF)(FF) 2)R(FF)(FF)FF(FF)(FF) 3)R (FF)(FF)(FF)(FF)(FF) 1) insert FFFFFFFFFF
    //                          \     \  \(0F)         \  \    \  \(0F) 2) insert FFF0FFFFFF, FFFFFFFF0F, FFFFFFAFFF
    //                           |     |                |  |    |       3) insert FFFFRQAFFF
    //                           |     Lp  Ad           |  |    |Lp  Ad
    //                           |    (AF)(FF)          |  \    (AF)(FF)
    //                           |                      |   Lp    Ad
    //                           |LP     Ad           Lp|  (RQ)AF(FF)
    //                          (F0)FFFF(FF)          (F0)FFFF(FF)
    //if key size 20 and age 2:
    // Lp-76b            Bp-143b Lp-191b        Bp-222b Lp-249b
    // total insert 22*5 = 110 byte, trie size 471 byte (but this is a lazy expansion, when the tree is compacted, compression will occur)
    //ROOT(content BRANCH)
    //type(1)/hash sha256hash160(20)/child point array(1-256*8)
    //00     /00*20                 /0000000000000000 max 2069 byte
    //BRANCH(content child BRANCH & LEAF)
    //type(1)/key size(1)/key(1-17)/hash sha256hash160(20)/childsMap(32)  /leafPointArray(1-256*8)
    //00     /00         /00*19    /00*20                 /00*32          /00*8              max 2121 byte (ideal ‭39 062 500‬(leaves) =~301Mb)
    //LEAF(content account age)
    //type(1)/key size(1)/key(1-18)/hash sha256hash160(20)/childsMap(32)  /ageArray(1-256*2)/
    //00     /00         /00*20    /00*20                 /00*32          /00*2               max 585 (ideal 10000000000(account)=21GB)
    //total 22GB(ideal trie for 10 000 000 000 accounts)
    //
    //we are save all change: 1st in a free space pointers at , 2st in apped bytes in files trie.dat
    //free space pointers register it is special buffer  for story free pointers in trie.dat
    //deserialize = /size(2 bytes)/point(8 bytes)/ for trie.dat. For 10 000 000 pointers *10 bytes = ~ 95Mb

    public byte[] insert(byte[] key, byte[] age) throws IOException {

        byte[] s = search(key, rootPos);
        if(s==null){
            trie.setLength(0);
            trie.write(ROOT);
            byte[] trieTmp =new byte[2068];
            trie.write(trieTmp);
            s = search(key, rootPos);
        }
        ByteArrayInputStream fined = new ByteArrayInputStream(s);
        long pos = recursiveInsert(key, age, fined, 0);
        fined.close();
        return getHash(pos);
    }

    private long recursiveInsert(byte[] key, byte[] age, ByteArrayInputStream fined, long pos) throws IOException {
        byte[] hash;
        //BitSet childsMap = BitSet.valueOf(new byte[256]);
        byte[] childsMap = new byte[32];
        if(fined.available()>0){
            byte[] type=new byte[1];
            fined.read(type,0,1);
            byte[] found=new byte[1];
            fined.read(found,0,1);
            //если корень то если позиция нулевая(новая ветка в дереве) то добавляем новый Leaf иначе запишем позицию в карту ветвей
            if(type[0]==ROOT){
                if(pos==0){
                    //todo find free space
                    trie.seek(trie.length());
                    pos=trie.getFilePointer();
                    //
                    trie.write(LEAF);
                    ByteBuffer buffer = ByteBuffer.allocate(19);
                    byte[] lKey= buffer.put(key, 0, buffer.limit()).array();
                    trie.writeByte((byte)19);
                    trie.write(lKey);
                    hash=calcHash(LEAF, lKey, age);
                    trie.write(hash);
                    childsMap = addChildInMap(childsMap, (key[key.length-1]&0xFF));
                    trie.write(childsMap);
                    trie.write(age);
                }
                //set pos
                trie.seek(21+(((key[0]&0xFF)*8)-8));
                trie.write(Longs.toByteArray(pos));
                trie.seek(21);
                byte[] childArray= new byte[2048];
                trie.read(childArray,0,2048);
                hash=calcHash(ROOT, new byte[1], childArray);
                trie.seek(1);
                trie.write(hash);
                return 0L;
            }else{
                // Иначе получаем позицию, ключ, карту дочерних узлов
                byte[] selfPos= new byte[8];
                fined.read(selfPos,0,8);
                byte[] selfKeySize= new byte[1];
                fined.read(selfKeySize,0,1);
                byte[] selfKey= new byte[selfKeySize[0]];
                fined.read(selfKey,0,selfKeySize[0]);
                byte[] suffixKey = getBytesPart(key, selfKeySize[0], key.length - selfKeySize[0]);
                byte[] preffixKey = getBytesPart(key, 0, selfKeySize[0]);
                //todo найдем общий ключ между ключем узла и префиксом вносимого ключа
                byte[] commonKey;
                if(preffixKey.length==1){
                    commonKey = preffixKey;
                }else{
                    commonKey = getCommonKey(selfKey, preffixKey);
                }
                byte[] selfChildMap= new byte[32];
                fined.read(selfChildMap,0,32);
                int selfChildsCount = getChildsCount(selfChildMap);

                boolean sizeChange = false;

                if(type[0]==BRANCH){
                    //todo если это ветка то создаем и зачитываем туда дочерний массив с размером = число дочерей *8
                    // переходим в позицию
                    int selfChildArraySize = selfChildsCount*8;
                    byte[] selfChildArray= new byte[selfChildArraySize];
                    trie.seek(Longs.fromByteArray(selfPos)+2+(selfKeySize[0]&0xFF)+20+32);//go to childArray
                    trie.read(selfChildArray,0,selfChildArraySize);
                    //todo если префикс вносимого ключа не = ключу листа или ненайден дочерний элемент то
                    if(found[0]==NOTFOUND){
                        //todo найдем свободное пространство и внесем новый лист
                        trie.seek(trie.length());
                        long posLeaf=trie.getFilePointer();
                        trie.write(LEAF);
                        //выделим буфер размером длинна вносимого ключа - длинна общего ключа - 1 байт(для дочери)
                        ByteBuffer bL = ByteBuffer.allocate(key.length - commonKey.length-1);
                        byte[] leafKey= bL.put(key, commonKey.length, bL.limit()).array();
                        trie.writeByte(leafKey.length);
                        trie.write(leafKey);
                        hash=calcHash(LEAF, leafKey, age);
                        trie.write(hash);
                        childsMap = addChildInMap(childsMap, (key[key.length-1]&0xFF));
                        trie.write(childsMap);
                        trie.write(age);
                        //todo если префикс вносимого ключа не = ключу ветки
                        if(!Arrays.equals(preffixKey, selfKey)){
                            //todo найдем свободное пространство
                            // создадим новую ветку из текущей
                            // она будет содержать массив дочерей текущей ветви и иметь ключ = суффиксу от текущего ключа за минусом общего
                            trie.seek(trie.length());
                            long posBranch=trie.getFilePointer();
                            trie.write(BRANCH);
                            ByteBuffer bB = ByteBuffer.allocate((selfKey.length - commonKey.length));
                            byte[] branchKey= bB.put(selfKey, commonKey.length, bB.limit()).array();
                            trie.writeByte(branchKey.length);
                            trie.write(branchKey);
                            hash=calcHash(BRANCH, branchKey, selfChildArray);
                            trie.write(hash);
                            trie.write(selfChildMap);
                            trie.write(selfChildArray);

                            //todo найдем свободное пространство
                            // создадим новую ветку из текущей
                            // она будет содержать массив дочерей состоящий из созданных ранее ветви и листа и иметь ключ равный общему префиксу для обоих дочерей
                            trie.seek(trie.length());
                            pos=trie.getFilePointer();

                            trie.write(BRANCH);
                            trie.writeByte(commonKey.length);
                            trie.write(commonKey);
                            childsMap = addChildInMap(new byte[32], (leafKey[0]&0xFF));
                            childsMap = addChildInMap(childsMap, (branchKey[0]&0xFF));
                            byte [] childArray;
                            if ((leafKey[0]&0xFF)>(branchKey[0]&0xFF)){
                                childArray = Bytes.concat(Longs.toByteArray(posBranch), Longs.toByteArray(posLeaf));
                            }else{
                                childArray = Bytes.concat(Longs.toByteArray(posLeaf), Longs.toByteArray(posBranch));
                            }
                            hash=calcHash(BRANCH, commonKey, childArray);
                            trie.write(hash);
                            trie.write(childsMap);
                            trie.write(childArray);
                            return recursiveInsert(key, age, fined, pos);
                        }
                        //todo найдем свободное пространство
                        // создадим новую ветку из текущей
                        // она будет содержать массив дочерей с добавлением нового дочернего элемента в виде ссылки на позицию созданого листа
                        trie.seek(trie.length());
                        pos=trie.getFilePointer();

                        trie.write(BRANCH);
                        trie.writeByte(selfKey.length);
                        trie.write(selfKey);
                        childsMap = addChildInMap(selfChildMap, (leafKey[0]&0xFF));
                        byte [] childArray;
                        if ((leafKey[0]&0xFF)>0 && (leafKey[0]&0xFF)!=255){
                            childArray = Bytes.concat(getBytesPart(selfChildArray,0, ((leafKey[0]&0xFF)-1)*8),
                                    Longs.toByteArray(posLeaf),
                                    getBytesPart(selfChildArray,(leafKey[0]&0xFF)*8, 255-((leafKey[0]&0xFF)*8)));
                        }else{
                           if((leafKey[0]&0xFF)==0){
                               childArray = Bytes.concat(Longs.toByteArray(posLeaf),
                                       getBytesPart(selfChildArray,8, 247));
                           }else{
                               childArray = Bytes.concat(getBytesPart(selfChildArray,0, 247),
                                       Longs.toByteArray(posLeaf));
                           }
                        }
                        hash=calcHash(BRANCH, commonKey, childArray);
                        trie.write(hash);
                        trie.write(childsMap);
                        trie.write(childArray);
                        return recursiveInsert(key, age, fined, pos);
                    }else{
                        //todo Если найден то ищем позицию в массиве дочерей, записываем туда позицию найденного(вдруг изменилась)
                        // пересчитываем хеш и рекурсивно вносим позицию в вышестоящие узлы
                        int childPosInMap = getChildPos(selfChildMap, (suffixKey[0]&0xFF));
                        trie.seek(Longs.fromByteArray(selfPos)+2+(selfKeySize[0]&0xFF)+20+32+(childPosInMap*8)-8);
                        trie.write(Longs.toByteArray(pos));
                        trie.seek(Longs.fromByteArray(selfPos)+2+(selfKeySize[0]&0xFF)+20+32);
                        trie.read(selfChildArray,0,selfChildArraySize);
                        hash=calcHash(BRANCH, selfKey, selfChildArray);
                        trie.seek(Longs.fromByteArray(selfPos)+2+(selfKeySize[0]&0xFF));
                        trie.write(hash);
                        return recursiveInsert(key, age, fined, Longs.fromByteArray(selfPos));
                    }
                }else{
                    //todo если это лист то зачитываем его дочерний массив с размером = число дочерей *2
                    int selfChildArraySize = selfChildsCount*2;
                    byte[] selfChildArray= new byte[selfChildArraySize];
                    trie.seek(Longs.fromByteArray(selfPos)+2+(selfKeySize[0]&0xFF)+20+32);//go to childArray
                    trie.read(selfChildArray,0,selfChildArraySize);
                    //todo если префикс вносимого ключа не = ключу листа или ненайден дочерний элемент то
                    if(found[0]==NOTFOUND){
                        //todo если префикс вносимого ключа не = ключу листа
                        if(!Arrays.equals(preffixKey, selfKey)){
                            //todo найдем свободное пространство и создадим новый лист
                            trie.seek(trie.length());
                            long posLeaf=trie.getFilePointer();
                            trie.write(LEAF);
                            //выделим буфер размером длинна вносимого ключа - длинна общего ключа - 1 байт(для дочери)
                            ByteBuffer bL = ByteBuffer.allocate(key.length - commonKey.length-1);
                            byte[] leafKey= bL.put(key, commonKey.length, bL.limit()).array();
                            trie.writeByte(leafKey.length);
                            trie.write(leafKey);
                            hash=calcHash(LEAF, leafKey, age);
                            trie.write(hash);
                            childsMap = addChildInMap(childsMap, (key[key.length-1]&0xFF));
                            trie.write(childsMap);
                            trie.write(age);

                            //todo найдем свободное пространство
                            // создадим новый лист из текущего
                            // он будет содержать массив дочерей текущего листа и иметь суффикс от ключа  текущего листа за минусом общего
                            trie.seek(trie.length());
                            long posOldLeaf=trie.getFilePointer();
                            trie.write(LEAF);
                            ByteBuffer boL = ByteBuffer.allocate((selfKey.length - commonKey.length));
                            byte[] oldLeafKey= boL.put(selfKey, commonKey.length, boL.limit()).array();
                            trie.writeByte(oldLeafKey.length);
                            trie.write(oldLeafKey);
                            hash=calcHash(LEAF, oldLeafKey, selfChildArray);
                            trie.write(hash);
                            trie.write(selfChildMap);
                            trie.write(selfChildArray);

                            //todo найдем свободное пространство
                            // создадим новую ветку для обоих листов
                            // она будет содержать массив дочерей состоящий из созданных ранее листов и иметь ключ равный общему префиксу для обоих дочерей
                            trie.seek(trie.length());
                            pos=trie.getFilePointer();

                            trie.write(BRANCH);
                            trie.writeByte(commonKey.length);
                            trie.write(commonKey);
                            //childsMap = BitSet.valueOf(new byte[256]);//get clear child map
                            childsMap = addChildInMap(new byte[32], (leafKey[0]&0xFF));
                            childsMap = addChildInMap(childsMap, (oldLeafKey[0]&0xFF));
                            byte [] childArray;
                            if (leafKey[0]>oldLeafKey[0]){
                                childArray = Bytes.concat(Longs.toByteArray(posOldLeaf), Longs.toByteArray(posLeaf));
                            }else{
                                childArray = Bytes.concat(Longs.toByteArray(posLeaf), Longs.toByteArray(posOldLeaf));
                            }
                            hash=calcHash(BRANCH, commonKey, childArray);
                            trie.write(hash);
                            trie.write(childsMap);
                            trie.write(childArray);
                            return recursiveInsert(key, age, fined, pos);
                        }
                        //todo найдем свободное пространство
                        // создадим новый лист на базе старого
                        // в него будет добавлен возраст дочернего элемента
                        trie.seek(trie.length());
                        pos=trie.getFilePointer();

                        trie.write(LEAF);
                        trie.writeByte(selfKey.length);
                        trie.write(selfKey);
                        //childsMap = BitSet.valueOf(new byte[256]);//get clear child map
                        childsMap = addChildInMap(selfChildMap, (key[key.length-1]&0xFF));
                        byte [] childArray;
                        if ((key[key.length-1]&0xFF)>0 && (key[key.length-1]&0xFF)!=255){
                            childArray = Bytes.concat(getBytesPart(selfChildArray,0, ((key[key.length-1]&0xFF)-1)*2),
                                    age,
                                    getBytesPart(selfChildArray,(key[key.length-1]&0xFF)*2, 255-((key[key.length-1]&0xFF)*2)));
                        }else{
                            if((key[key.length-1]&0xFF)==0){
                                childArray = Bytes.concat(age,
                                        getBytesPart(selfChildArray,2, 253));
                            }else{
                                childArray = Bytes.concat(getBytesPart(selfChildArray,0, 253),
                                        age);
                            }
                        }
                        hash=calcHash(LEAF, commonKey, childArray);
                        trie.write(hash);
                        trie.write(childsMap);
                        trie.write(childArray);
                        return recursiveInsert(key, age, fined, pos);
                    }else {
                        //todo Если найден то ищем позицию в массиве дочерей, записываем туда вносимый возраст (вдруг изменился)
                        // пересчитываем хеш и рекурсивно вносим позицию в вышестоящие узлы
                        int childPosInMap = getChildPos(selfChildMap, (suffixKey[0]&0xFF));
                        trie.seek(Longs.fromByteArray(selfPos)+2+(selfKeySize[0]&0xFF)+20+32+(childPosInMap*2)-2);
                        trie.write(age);
                        trie.seek(Longs.fromByteArray(selfPos)+2+(selfKeySize[0]&0xFF)+20+32);
                        trie.read(selfChildArray,0,selfChildArraySize);
                        hash=calcHash(LEAF, selfKey, selfChildArray);
                        trie.seek(Longs.fromByteArray(selfPos)+2+(selfKeySize[0]&0xFF));
                        trie.write(hash);
                        return recursiveInsert(key, age, fined, Longs.fromByteArray(selfPos));
                    }
                }
            }
        }else{
            return 0L;
        }

    }
    //Извлекает общий для selfKey и key префикс
    private byte[] getCommonKey(byte[] selfKey, byte[] key) {
        for(int i = 1; i < selfKey.length+1; i++){
            byte[] sK = getBytesPart(key, 0, selfKey.length-i);
            if(Arrays.equals(sK, getBytesPart(selfKey, 0, selfKey.length-i))){
                return sK;
            }
        }
        return null;
    }

    //
    public byte[] search(byte[] key, byte[] pos) throws IOException {
        //Если размер файла == 0  то вернем null
        if(trie.length()>0) {
            //иначе переместимся на позицию pos
            trie.seek(Longs.fromByteArray(pos));
            //прочитаем тип узла
            byte type = trie.readByte();
            //если это корень то переместим курсор на позицию в массиве детей = первый байт ключа * 8 - 8
            //если содержимое != 0 то  начинаем искать там и вернем то что нашли + корень, иначе вернем корень
            if (type == ROOT) {
                trie.seek(trie.getFilePointer() + 20 + ((key[0]&0xFF) * 8)-8);
                byte[] childPos = new byte[8];
                trie.read(childPos, 0, 8);
                if (Longs.fromByteArray(childPos) != 0) {
                    byte[] child = search(key, childPos);
                    ByteBuffer rtBuffer = ByteBuffer.allocate(child.length + 1);
                    return rtBuffer.put(child).put(ROOT).array();
                }
                ByteBuffer rtBuffer = ByteBuffer.allocate(1);
                return rtBuffer.put(ROOT).array();
            } else {
                //прочитаем ключ, карту дочерей, число дочерей, суфикс вносимого ключа
                byte keyNodeSize = trie.readByte();
                byte[] keyNode = new byte[keyNodeSize];
                trie.read(keyNode, 0, keyNodeSize);
                //todo compare keyNode vs key
                trie.seek(trie.getFilePointer() + 20); //skip hash
                byte[] childsMap = new byte[32];
                trie.read(childsMap, 0, 32);
                //Получим суффикс вносимого ключа(остаток от вносимого ключа)
                byte[] preffixKey = getBytesPart(key, 0, keyNodeSize);
                byte[] suffixKey = getBytesPart(key, keyNodeSize, key.length - keyNodeSize);
                //found Если вносимый ключ(его часть =  длинне ключа узла) = ключу узла и первый байт суффикса имеется в массиве дочерей то
                if(Arrays.equals(preffixKey, keyNode) && checkChild(childsMap, (suffixKey[0] & 0xFF))) {
                    if (type == BRANCH) {
                        int childPosInMap = getChildPos(childsMap, (suffixKey[0] & 0xFF));
                        trie.seek(trie.getFilePointer() + (childPosInMap * 8) - 8);
                        byte[] childPos = new byte[8];
                        trie.read(childPos, 0, 8);
                        byte[] child = search(suffixKey, childPos);
                        ByteBuffer rtBuffer = ByteBuffer.allocate(child.length + 10 + 1 + (keyNodeSize & 0xFF) + 32);
                        //Вернем детей, Тип, Признак что найден, Позицию узла, размер ключа узла, ключ узла, карту дочерей
                        //ret ...00/00/00/0000000000000000/00/00n/0000000000000000000000000000000000000000000000000000000000000000
                        return rtBuffer.put(child).put(BRANCH).put(FOUND).put(pos).put(keyNodeSize).put(keyNode).put(childsMap).array();
                    }else{
                        ByteBuffer rtBuffer = ByteBuffer.allocate(10 + 1 + (keyNodeSize&0xFF) + 32);
                        //Вернем Тип, Признак что найден, Позицию узла, размер ключа узла, ключ узла, карту дочерей, возраст найден, текущий возраст/
                        //ret /00/00/0000000000000000/00/00n/0000000000000000000000000000000000000000000000000000000000000000/0/0000/
                        return rtBuffer.put(LEAF).put(FOUND).put(pos).put(keyNodeSize).put(keyNode).put(childsMap).array();
                    }
                }else{//иначе вернем (лист)
                    ByteBuffer rtBuffer = ByteBuffer.allocate(10 + 1+ (keyNodeSize&0xFF) + 32);
                    //Вернем Тип, Признак что не найден, Позицию узла, размер ключа узла, ключ узла, карту дочерей
                    //ret /00/00/0000000000000000/00/00n/0000000000000000000000000000000000000000000000000000000000000000/
                    return rtBuffer.put(type).put(NOTFOUND).put(pos).put(keyNodeSize).put(keyNode).put(childsMap).array();
                }
            }
        }else{
            return null;
        }
    }

    private int getChildPos(byte[]childsMap, int key){
        int result=1;
        BitSet prepare = BitSet.valueOf(childsMap);
        for(int i = 0; i < key;i++){
            if(prepare.get(i)) result=result+1;
        }
        return result;
    }

    private int getChildsCount(byte[]childsMap){
        return BitSet.valueOf(childsMap).cardinality();
    }

    private boolean checkChild(byte[]childsMap, int key){
        BitSet prepare = BitSet.valueOf(childsMap);
        return prepare.get(key);
    }

    private byte[] addChildInMap(byte[]childsMap, int key){
        //byte[] result;
        ByteBuffer result = ByteBuffer.allocate(32);
        //byte[] oldLeafKey= boL.put(selfKey, commonKey.length, selfKey.length).array();
        boolean end = false;
        for(int i=0; i < 32; i++){
            if(key>(i*8)-1 && key<(i*8)+9){
                result.put(childsMap,0,i);//start
                //
                byte[] p = new byte[1];
                p[0]=childsMap[i];
                BitSet prepare1 = BitSet.valueOf(p);
                for(int b=0;b<8;b++) {
                    if ((i * 8) + b == key) {
                        prepare1.set(b);
                        break;
                    }
                }
                for(int c=0;c<256;c++){
                    p[0]=(byte)c;
                    BitSet prepare2 = BitSet.valueOf(p);
                    if(prepare1.equals(prepare2)){
                        result.put(p);
                        result.put(childsMap,result.position(),32-(i+1));//end
                        end=true;
                        break;
                    }
                }
                //
            }
            if(end){
                break;
            }
        }
        return result.array();
    }

    private byte[] getBytesPart(byte[]src, int off, int len){
        byte[] result= new byte[len];
        for(int i=0; i < result.length; i++){
            result[i]=src[off+i];
        }
        return result;
    }

    public byte[] getHash(long pos) throws IOException {
        byte[] hash = new byte[20];
        trie.seek(pos);
        byte type = trie.readByte();
        if(type==ROOT){
            trie.read(hash, 0, 20);
        }else {
            byte keySize = trie.readByte();
            trie.seek(trie.getFilePointer() + 1 + keySize);
            trie.read(hash, 0, 20);
        }
        return hash;
    }

    private byte[] calcHash(byte type, byte[] key, byte[] childArray) throws IOException {
        byte[] hash;
        if(type==BRANCH || type==ROOT) {
            byte[] digest=key;
            for(int i = 0; i < childArray.length;) {
                long pos = Longs.fromByteArray(getBytesPart(childArray, i, 8));
                if(pos!=0){
                    digest = Bytes.concat(digest, getHash(pos));
                }
                i = i + 8;;
            }
            hash = sha256hash160(digest);
        }else if(type==LEAF){
            byte[] digest=Bytes.concat(key, childArray);
            hash = sha256hash160(digest);
        }else{
            hash = null;
        }
        return hash;
    }

}