package esypsydb.multibuffer;

import esypsydb.server.EasyPeasyDB;

public class BufferNeeds {
    public static int bessRoot(int size) {
        int avali = EasyPeasyDB.bufferMgr().available();
        if (avali <= 1)
            return 1;
        int k = Integer.MAX_VALUE;
        double i = 1.0;
        while (k > avali) {
            i++;
            k = (int)Math.ceil(Math.pow(size, 1/i));
        }
        return k;
    }

    /**
     * chunk数 = Math.ceil(B2/k)     天井関数
     * 
     * @param size
     * @return k
     */
    public static int bestFactor(int size) {
        int avali = EasyPeasyDB.bufferMgr().available();
        if (avali <= 1)
            return 1;
        int k = size;
        double i = 1.0;
        while (k > avali) {
            i++;
            k = (int)Math.ceil(size / i);
        }
        return k;
    }
}
