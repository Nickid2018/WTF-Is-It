package io.github.nickid2018.mi;

import java.util.Arrays;
import java.util.function.LongConsumer;

public class LongArrayStream {

    private final long[] data;

    public LongArrayStream(int size) {
        data = new long[size];
    }

    public static LongArrayStream of(long... data) {
        LongArrayStream stream = new LongArrayStream(data.length);
        System.arraycopy(data, 0, stream.data, 0, data.length);
        return stream;
    }

    public LongArrayStream reverse() {
        int length = data.length;
        long[] data = this.data;
        for (int i = 0; i < length / 2; i++) {
            long tmp = data[i];
            data[i] = data[length - i - 1];
            data[length - i - 1] = tmp;
        }
        return this;
    }

    public long peek(int at) {
        return data[at];
    }

    public long set(int at, long value) {
        data[at] = value;
        return value;
    }

    public void accept(LongConsumer consumer) {
        for (long value : data)
            consumer.accept(value);
    }

    public void accept(IntLongBiConsumer consumer) {
        for (int i = 0; i < data.length; i++)
            consumer.accept(i, peek(i));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LongArrayStream that = (LongArrayStream) o;
        return Arrays.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(data);
    }

    @FunctionalInterface
    public interface IntLongBiConsumer {

        void accept(int i, long j);
    }
}

