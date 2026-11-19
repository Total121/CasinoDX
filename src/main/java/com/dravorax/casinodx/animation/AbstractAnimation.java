package com.dravorax.casinodx.animation;

import java.util.UUID;

public abstract class AbstractAnimation implements Animation {

    private final UUID playerId;
    private final int maxDurationTicks;
    private boolean finished;
    private boolean cancelled;
    private boolean paused;
    private boolean started;

    protected AbstractAnimation(UUID playerId, int maxDurationTicks) {
        this.playerId = playerId;
        this.maxDurationTicks = Math.max(1, maxDurationTicks);
    }

    @Override
    public UUID playerId() {
        return playerId;
    }

    @Override
    public int maxDurationTicks() {
        return maxDurationTicks;
    }

    @Override
    public void start() {
        if (started) return;
        started = true;
        onStart();
    }

    @Override
    public void pause() {
        paused = true;
    }

    @Override
    public void resume() {
        paused = false;
    }

    @Override
    public void finish() {
        if (finished || cancelled) return;
        finished = true;
        onFinish();
        cleanup();
    }

    @Override
    public void cancel() {
        if (cancelled || finished) return;
        cancelled = true;
        onCancel();
        cleanup();
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public boolean isPaused() {
        return paused;
    }

    @Override
    public void cleanup() {}

    protected void onStart() {}

    protected void onFinish() {}

    protected void onCancel() {}

    protected void markFinished() {
        finish();
    }
}
