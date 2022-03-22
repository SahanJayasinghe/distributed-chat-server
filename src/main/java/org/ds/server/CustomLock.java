package org.ds.server;

public class CustomLock {
    static private final Object obj = new Object();

    public static void customWait() {
        synchronized (obj) {
            try {
                obj.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public static void customNotifyAll() {
        synchronized (obj) {
            obj.notifyAll();
        }
    }

}
