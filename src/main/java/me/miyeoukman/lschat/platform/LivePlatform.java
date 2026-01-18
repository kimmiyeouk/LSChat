package me.miyeoukman.lschat.platform;

public interface LivePlatform {
    String getName();
    void start();
    void stop();
    boolean isRunning();
}
