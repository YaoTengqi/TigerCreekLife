package com.hmdp.utils;

public interface ILock {
    /**
     * 尝试获取锁
     *
     * @param timeoutSec
     * @return 是否成功获取锁
     */
    boolean tryLock(long timeoutSec);

    /**
     * 释放锁
     */
    void unlock();
}
