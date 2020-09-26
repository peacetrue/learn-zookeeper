package com.github.peacetrue.learn.zookeeper;

import org.apache.zookeeper.*;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author : xiayx
 * @since : 2020-09-26 05:37
 **/
public class ZooKeeperDistributedLock implements Lock {

    private ZooKeeper zooKeeper;
    private String lockPath;
    private String lockPath_;
    private String sequentialPath;
    private ReentrantLock localLock = new ReentrantLock();

    public ZooKeeperDistributedLock(ZooKeeper zooKeeper, String lockPath) {
        this.zooKeeper = zooKeeper;
        this.lockPath = lockPath;
        this.lockPath_ = lockPath + "/";
    }

    @Override
    public void lock() {
        try {
            localLock.lock();
            String sequentialPath = zooKeeper.create(lockPath_, null, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
            System.out.printf("create path:[%s]", sequentialPath).println();
            this.sequentialPath = sequentialPath;
        } catch (KeeperException | InterruptedException e) {
            System.err.println(e);
            localLock.unlock();
            throw new IllegalStateException(e);
        }
        this.lock(sequentialPath.substring(lockPath_.length()), true, new CountDownLatch(1));
    }

    public void lock(String actualPath, boolean first, CountDownLatch countDownLatch) {
        try {
            List<String> children = zooKeeper.getChildren(lockPath, false);
            children.sort(String::compareTo);
            System.out.printf("children:[%s]", children).println();
            if (actualPath.equals(children.get(0))) {
                if (!first) countDownLatch.countDown();
                System.out.printf("get lock %s", actualPath).println();
                return;
            }

            int index = children.indexOf(actualPath);
            String path = lockPath_ + children.get(index - 1);
            System.out.printf("[%s ]pre node path:[%s]", actualPath, path).println();
            zooKeeper.exists(path, event -> {
                if (Watcher.Event.EventType.NodeDeleted == event.getType()) {
                    this.lock(actualPath, false, countDownLatch);
                }
            });
            System.out.printf("%s await", actualPath).println();
            countDownLatch.await();
        } catch (KeeperException | InterruptedException e) {
            System.err.println(e);
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {

    }

    @Override
    public boolean tryLock() {
        return false;
    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        return false;
    }

    @Override
    public void unlock() {
        if (sequentialPath == null) return;
        try {
            System.out.printf("unlock %s b", sequentialPath).println();
            zooKeeper.delete(sequentialPath, -1);
            localLock.unlock();
            System.out.printf("unlock %s a", sequentialPath).println();
        } catch (InterruptedException | KeeperException e) {
            System.err.println(e);
            throw new IllegalStateException(e);
        }
    }

    @Override
    public Condition newCondition() {
        return null;
    }
}
