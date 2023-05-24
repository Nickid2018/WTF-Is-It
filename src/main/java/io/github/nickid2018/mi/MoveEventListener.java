package io.github.nickid2018.mi;

@FunctionalInterface
public interface MoveEventListener {

    void move(int line, int fromSlot, int endSlot, MoveDirection direction, long sourceData, long endData);
}
