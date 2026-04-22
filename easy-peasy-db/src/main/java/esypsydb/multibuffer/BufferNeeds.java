package esypsydb.multibuffer;

public class BufferNeeds {
    public static int bessRoot(int available, int size) {
        if (available <= 1)
            return 1;
        int k = Integer.MAX_VALUE;
        double i = 1.0;
        while (k > available) {
            i++;
            k = (int)Math.ceil(Math.pow(size, 1/i));
        }
        return k;
    }

    /**
     * chunk数 = Math.ceil(B2/k)     天井関数
     */
    public static int bestFactor(int available, int size) {
        if (available <= 1)
            return 1;
        int k = size;
        double i = 1.0;
        while (k > available) {
            i++;
            k = (int)Math.ceil(size / i);
        }
        return k;
    }
}
