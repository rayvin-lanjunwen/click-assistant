package com.clickassistant.mobile;

public final class PrototypeTask {
    private final String name;
    private final int x;
    private final int y;
    private final int repeatCount;
    private final int startDelayMs;
    private final int intervalMs;

    public PrototypeTask(String name, int x, int y, int repeatCount, int startDelayMs, int intervalMs) {
        this.name = name;
        this.x = x;
        this.y = y;
        this.repeatCount = repeatCount;
        this.startDelayMs = startDelayMs;
        this.intervalMs = intervalMs;
    }

    public String getName() {
        return name;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getRepeatCount() {
        return repeatCount;
    }

    public int getStartDelayMs() {
        return startDelayMs;
    }

    public int getIntervalMs() {
        return intervalMs;
    }

    public boolean isValid() {
        return name != null
            && !name.trim().isEmpty()
            && x >= 0
            && y >= 0
            && repeatCount > 0
            && startDelayMs >= 0
            && intervalMs >= 0;
    }
}
