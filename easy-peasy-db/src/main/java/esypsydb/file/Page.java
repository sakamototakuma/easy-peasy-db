package esypsydb.file;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class Page {
    private ByteBuffer bb;
    public static final Charset CHARASET = StandardCharsets.US_ASCII;

    // ダイレクトバッファ作成用コンストラクタ
    public Page(int blocksize) {
        bb = ByteBuffer.allocateDirect(blocksize);
    }

    // ログページ作成用コンストラクタ 普通のJava配列
    public Page(byte[] b) {
        bb = ByteBuffer.wrap(b);
    }

    // 固定位置にint nを読み書き
    // 指定位置の値をget
    public int getInt(int offset) {
        return bb.getInt(offset);
    }

    //　指定位置に値nをset
    public void setInt(int offset, int n) {
        bb.putInt(offset, n);
    }

    // 可変長 bytes[]の読み書き
    public byte[] getBytes(int offset) {
        // 開始位置にポインタを移す
        bb.position(offset);
        // lengthの4bytesを取得
        int length = bb.getInt();
        byte b [] = new byte[length];
        // ByteBufferの該当箇所から配列bのサイズ=length分のビット列を取得
        // getメソッドは引数の配列に該当データを埋める
        bb.get(b);
        return b;
    }

    public void setBytes(int offset, byte[] b) {
        bb.position(offset);
        bb.putInt(b.length);
        bb.put(b);
    }

    // 文字列の読み書き
    public String geString(int offset) {
        byte[] b = getBytes(offset);
        // 受け取った配列bを文字セットで文字列で復元
        return new String(b, CHARASET);
    }

    public void setString(int offset, String s) {
        // 文字列=>バイト列
        byte[] b = s.getBytes(CHARASET);
        setBytes(offset, b);
    }

    // 最大で何バイトの領域を確保すべきかを計算し、領域の予約をする
    // strlenは保存したい文字数
    public static int maxLength(int strlen) {
        float bytesPerChar = CHARASET.newEncoder().maxBytesPerChar();
        // Integer.BYTES は “長さ(int)” の4バイト分
        return Integer.BYTES + (strlen * (int)bytesPerChar);
    }

    // FilrMgrで必要な静的メソッドのpackage
    ByteBuffer contents() {
        // バッファのポインタリセット
        bb.position(0);
        return bb;
    }
}
