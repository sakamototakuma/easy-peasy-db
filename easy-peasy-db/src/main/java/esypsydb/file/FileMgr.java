package esypsydb.file;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Map;

// 
public class FileMgr {
    private File dbDirectory; // 保存先ディレクトリ
    private int blocksize;
    private boolean isNew;
    private Map<String, RandomAccessFile> openFiles = new java.util.LinkedHashMap<String, RandomAccessFile>(16, 0.75f,
            true) {
        protected boolean removeEldestEntry(Map.Entry<String, RandomAccessFile> eldest) {
            if (size() > 100) {
                try {
                    eldest.getValue().close();
                } catch (IOException e) {
                    /* ignore */ }
                return true;
            }
            return false;
        }
    };

    public FileMgr(File dbDirectory, int blocksize) {
        this.dbDirectory = dbDirectory;
        this.blocksize = blocksize;
        isNew = !dbDirectory.exists();

        // databaseがないなら作成
        if (isNew)
            dbDirectory.mkdirs();

        // 一時テーブルを取り除く
        for (String filename : dbDirectory.list())
            if (filename.startsWith("temp"))
                new File(dbDirectory, filename).delete();
    }

    public synchronized void read(BlockId blk, Page p) {
        try {
            RandomAccessFile f = getFile(blk.fileName());
            f.seek(blk.number() * blocksize);
            f.getChannel().read(p.contents());
        } catch (IOException e) {
            throw new RuntimeException("cannnot read blk" + blk);
        }
    }

    public synchronized void write(BlockId blk, Page p) {
        try {
            RandomAccessFile f = getFile(blk.fileName());
            f.seek(blk.number() * blocksize);
            f.getChannel().write(p.contents());
        } catch (IOException e) {
            throw new RuntimeException("cannot write block" + blk);
        }
    }

    /**
     * ファイルの拡張：blocksizeの空配列をファイル末尾に付け足す
     * 
     * @return BlockId
     */
    public synchronized BlockId append(String filename) {
        int newblknum = length(filename);
        BlockId blk = new BlockId(filename, newblknum);
        byte[] b = new byte[blocksize];
        try {
            RandomAccessFile f = getFile(filename);
            f.seek(blk.number() * blocksize);
            f.write(b);
        } catch (IOException e) {
            throw new RuntimeException("cannot append block" + blk);
        }
        return blk;
    }

    // ファイルのブロック数を返す
    public synchronized int length(String filename) {
        try {
            RandomAccessFile f = getFile(filename);
            return (int) (f.length() / blocksize);
        } catch (IOException e) {
            throw new RuntimeException("cannot access" + filename);
        }
    }

    public synchronized void closeFile(String filename) {
        RandomAccessFile f = openFiles.remove(filename);
        if (f != null) {
            try {
                f.close();
            } catch (IOException e) {
                /* ignore */ }
        }
        new File(dbDirectory, filename).delete();
    }

    public boolean isNew() {
        return isNew;
    }

    public int blockSize() {
        return blocksize;
    }

    public synchronized void fsync(String filename) {
        try {
            RandomAccessFile f = getFile(filename);
            f.getChannel().force(true);
        } catch (IOException e) {
            throw new RuntimeException("cannot fsync" + filename);
        }
    }

    public synchronized void fsyncAll() {
        for (RandomAccessFile f : openFiles.values()) {
            try {
                f.getChannel().force(true);
            } catch (IOException e) {
                throw new RuntimeException("cannot fsync data files");
            }
        }
    }


    private RandomAccessFile getFile(String filename) throws IOException {
        RandomAccessFile f = openFiles.get(filename);
        if (f == null) {
            File dbTable = new File(dbDirectory, filename);
            f = new RandomAccessFile(dbTable, "rw");
            openFiles.put(filename, f);
        }
        return f;
    }
}
