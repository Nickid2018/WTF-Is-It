package io.github.nickid2018.mi;

@FunctionalInterface
public interface SpawnEventListener {

    void spawn(int row, int column, long number);
}