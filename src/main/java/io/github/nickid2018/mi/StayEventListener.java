package io.github.nickid2018.mi;

@FunctionalInterface
public interface StayEventListener {

    void stay(int line, int index, MoveDirection direction);
}
