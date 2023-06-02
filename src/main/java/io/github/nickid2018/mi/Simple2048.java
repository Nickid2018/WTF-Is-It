package io.github.nickid2018.mi;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

public class Simple2048 {

    private final int size;
    private final long[][] table;
    private final Random random = new Random();
    private final int[] valueSpared;
    private int spareCount;
    private long maxValue;
    private long score;
    private MoveEventListener moveListener;
    private StayEventListener stayListener;
    private SpawnEventListener spawnListener;

    public Simple2048(int size) {
        if (size > 8 || size < 3)
            throw new IllegalArgumentException();
        this.size = size;
        table = new long[size][size];
        score = 0;
        int[] valueSpared = new int[spareCount = size * size];
        for (int i = 0; i < size * size; i++)
            valueSpared[i] = i;
        this.valueSpared = valueSpared;
        spawnRandomValue();
        spawnRandomValue();
    }

    public MoveEventListener getMoveListener() {
        return moveListener;
    }

    public void setMoveListener(MoveEventListener moveListener) {
        this.moveListener = moveListener;
    }

    public StayEventListener getStayListener() {
        return stayListener;
    }

    public void setStayListener(StayEventListener stayListener) {
        this.stayListener = stayListener;
    }

    public SpawnEventListener getSpawnListener() {
        return spawnListener;
    }

    public void setSpawnListener(SpawnEventListener spawnListener) {
        this.spawnListener = spawnListener;
    }

    public void reset() {
        clear();
        spawnRandomValue();
        spawnRandomValue();
    }

    private void clear() {
        for (int row = 0; row < size; row++)
            for (int column = 0; column < size; column++)
                set(row, column, 0);
        for (int i = 0; i < size * size; i++)
            valueSpared[i] = i;
        spareCount = size * size;
        score = 0;
        maxValue = 0;
    }

    public long getMaxValue() {
        return maxValue;
    }

    public long getScore() {
        return score;
    }

    public void setScore(long score) {
        this.score = score;
    }

    public boolean doMove(MoveDirection direction) {
        boolean success = internalMove(direction);
        if (success)
            // If moved successfully, spawn new value
            spawnRandomValue();
        return success;
    }

    public int fromLineToSlot(int line, int index, MoveDirection direction) {
        return switch (direction) {
            case UP -> index * size + line;
            case DOWN -> (size - 1 - index) * size + line;
            case LEFT -> line * size + index;
            case RIGHT -> (line + 1) * size - 1 - index;
        };
    }

    public boolean internalMove(MoveDirection direction) {
        boolean success = false;
        // Re-calculate spare count
        spareCount = 0;
        for (int line = 0; line < size; line++) {
            int finalLine = line;  // Effective final statement (lambda restricted)
            success |= calculateSingleWithDirection(line, direction, stream -> {
                // Module to handle moving
                // Stream: First->Last = bottom moving->top moving
                LongArrayStream outStream = new LongArrayStream(size);
                AtomicInteger nowPosition = new AtomicInteger(0);
                AtomicInteger lastIndex = new AtomicInteger(0);
                AtomicBoolean moved = new AtomicBoolean(false);
                AtomicLong lastNumber = new AtomicLong(0);
                stream.accept((index, value) -> {
                    long last = lastNumber.get();
                    if (last != 0 && last == value) {
                        // If last value isn't 0 (Condition: Merged or first element)
                        // and the value equals last value, merge them and set last value to 0
                        long result = outStream.set(nowPosition.get() - 1, last + value);
                        score += result;
                        if (result > maxValue)
                            maxValue = result;
                        if (moveListener != null) {
                            moveListener.move(finalLine, lastIndex.get(), nowPosition.get() - 1, direction, value, result);
                            moveListener.move(finalLine, index, nowPosition.get() - 1, direction, value, result);
                        }
                        lastNumber.set(0);
                        moved.set(true);
                    } else if (value != 0) {
                        // If the value is 0, ignore it
                        // Otherwise, add it to the result
                        lastNumber.set(value);
                        outStream.set(nowPosition.get(), value);
                        if (moved.get() && moveListener != null)
                            moveListener.move(finalLine, index, nowPosition.get(), direction, value, value);
                        if (!moved.get() && stayListener != null)
                            stayListener.stay(finalLine, index, direction);
                        lastIndex.set(index);
                        nowPosition.incrementAndGet();
                    } else
                        moved.set(true);
                });
                // Update values
                supplyNewDataWithDirection(finalLine, direction, outStream);
                // If the result equals the input, the operation in this line is invalid
                return !outStream.equals(stream);
            });
        }
        return success;
    }

    public boolean checkContinue() {
        if (spareCount > 0)
            return true;
        for (int row = 0; row < size; row++)
            for (int column = 0; column < size; column++) {
                long value = get(row, column);
                if (row != size - 1 && get(row + 1, column) == value)
                    return true;
                if (column != size - 1 && get(row, column + 1) == value)
                    return true;
            }
        // When checked the table has neither spare area nor merge chance, the game is over
        return false;
    }

    private void spawnRandomValue() {
        if (spareCount == 0)
            // It won't be invoked
            return;
        int index = spareCount == 1 ? --spareCount : random.nextInt(--spareCount);  // Select random position
        int at = valueSpared[index];
        int value = random.nextFloat() < 0.95f ? 2 : 4;
        if (value > maxValue)
            maxValue = value;
        set(at / size, at % size, value);  // 95% spawns 2, 5% spawns 4
        System.arraycopy(valueSpared, index + 1, valueSpared, index, spareCount - index);  // Delete value has been spawned
        valueSpared[spareCount] = 0;
        if (spawnListener != null)
            spawnListener.spawn(at / size, at % size, value);
    }

    private boolean calculateSingleWithDirection(int line, MoveDirection direction, Function<LongArrayStream, Boolean> calculator) {
        // Get the stream of the line in certain direction
        return switch (direction) {
            case UP -> calculator.apply(LongArrayStream.of(getColumn(line)));
            case DOWN -> calculator.apply(LongArrayStream.of(getColumn(line)).reverse());
            case LEFT -> calculator.apply(LongArrayStream.of(getRow(line)));
            case RIGHT -> calculator.apply(LongArrayStream.of(getRow(line)).reverse());
        };
    }

    private void supplyNewDataWithDirection(int line, MoveDirection direction, LongArrayStream stream) {
        // Update the line in certain direction
        // Re-calculate the spare area
        switch (direction) {
            case UP -> {
                fillColumn(line, 0);
                stream.accept((index, value) -> {
                    set(index, line, value);
                    if (value == 0)
                        valueSpared[spareCount++] = index * size + line;
                });
            }
            case DOWN -> {
                fillColumn(line, 0);
                stream.accept((index, value) -> {
                    set(size - index - 1, line, value);
                    if (value == 0)
                        valueSpared[spareCount++] = (size - index - 1) * size + line;
                });
            }
            case LEFT -> {
                fillRow(line, 0);
                stream.accept((index, value) -> {
                    set(line, index, value);
                    if (value == 0)
                        valueSpared[spareCount++] = line * size + index;
                });
            }
            case RIGHT -> {
                fillRow(line, 0);
                stream.accept((index, value) -> {
                    set(line, size - index - 1, value);
                    if (value == 0)
                        valueSpared[spareCount++] = (line + 1) * size - index - 1;
                });
            }
        }
    }

    public void set(int row, int column, long value) {
        table[row][column] = value;
    }

    public void validate() {
        spareCount = 0;
        for (int i = 0; i < size; i++)
            for (int j = 0; j < size; j++) {
                long now;
                if ((now = get(i, j)) == 0)
                    valueSpared[spareCount++] = i * size + j;
                maxValue = Math.max(maxValue, now);
            }
    }

    public long get(int row, int column) {
        return table[row][column];
    }

    public long[] getRow(int row) {
        return table[row];
    }

    public long[] getColumn(int column) {
        long[] columnData = new long[size];
        for (int i = 0; i < size; i++)
            columnData[i] = get(i, column);
        return columnData;
    }

    public void fillRow(int row, int value) {
        Arrays.fill(table[row], value);
    }

    public void fillColumn(int column, int value) {
        for (int i = 0; i < size; i++)
            table[i][column] = value;
    }

    public int size() {
        return size;
    }

    public Simple2048 copy() {
        Simple2048 copy = new Simple2048(size);
        for (int i = 0; i < size; i++)
            System.arraycopy(table[i], 0, copy.table[i], 0, size);
        copy.maxValue = maxValue;
        copy.spareCount = spareCount;
        System.arraycopy(valueSpared, 0, copy.valueSpared, 0, spareCount);
        return copy;
    }
}
