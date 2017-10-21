package com.acmetensortoys.ctfwstimer.lib;

public interface TimerProvider {
    long wallMS();
    void postDelay(Runnable r, long delayMS);
    void cancelPost(Runnable r);
}
