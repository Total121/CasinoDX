package com.dravorax.casinodx.animation;

import java.util.UUID;

public interface Animation {

    UUID playerId();

    int maxDurationTicks();

    void start();

    void tick(int elapsedTicks);

    void pause();

    void resume();

    void finish();

    void cancel();

    void cleanup();

    boolean isFinished();

    boolean isCancelled();

    boolean isPaused();
}
