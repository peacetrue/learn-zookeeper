package com.github.peacetrue.learn.zookeeper;

import org.apache.zookeeper.ZooKeeper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * @author : xiayx
 * @since : 2020-09-26 06:08
 **/
class ZooKeeperDistributedLockTest {

    @Test
    void noLock() {
        MemCountService memLocalService = new MemCountService();
        Runnable runnable = memLocalService::getAndIncr;
        concurrent(memLocalService, runnable);
    }

    private void concurrent(CountService memLocalService, Runnable runnable) {
        ExecutorService executorService = Executors.newFixedThreadPool(100);
        int count = 100;
        List<Runnable> runnables = IntStream.range(0, count).mapToObj(i -> runnable).collect(Collectors.toList());
        Thread thread = Thread.currentThread();
        ZooKeeperUtils.runAsync(runnables, executorService)
                .whenComplete((a, b) -> {
                    Long aLong = memLocalService.get();
                    System.out.println(aLong);
                    LockSupport.unpark(thread);
                });
        LockSupport.park(this);
    }

    @Test
    void noLockTxt() {
        LocalCountService memLocalService = new LocalCountService();
        Runnable runnable = memLocalService::getAndIncr;
        concurrent(memLocalService, runnable);
    }

    @Test
    public CompletableFuture<Void> lock() throws Exception {
        ZooKeeper zooKeeper = ZooKeeperUtils.getZooKeeper();
        CountService memLocalService = new LocalCountService();
        ZooKeeperDistributedLock lock = new ZooKeeperDistributedLock(zooKeeper, "/lock");
        Runnable runnable = () -> {
            try {
                lock.lock();
                memLocalService.getAndIncr();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                lock.unlock();
            }
        };
        ExecutorService executorService = Executors.newFixedThreadPool(100);
        int count = 10;
        List<Runnable> runnables = IntStream.range(0, count).mapToObj(i -> runnable).collect(Collectors.toList());
        return ZooKeeperUtils.runAsync(runnables, executorService);
    }


    @Test
    void distributedLock() throws Exception {
        CompletableFuture.allOf(lock(), lock())
                .whenComplete((aVoid, throwable) -> {
                    LocalCountService localCountService = new LocalCountService();
                    System.out.printf("over ---%s", localCountService.get());
                    localCountService.reset();
                    System.out.printf("over ---%s", localCountService.get());
                });
        Thread.sleep(1_000_000L);
    }

    @Test
    void name() throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(10);
//        CompletableFuture.allOf(
//                CompletableFuture.runAsync(() -> System.out.println("1"), executorService),
//                CompletableFuture.runAsync(() -> System.out.println("1"), executorService)
//        )
//                .whenComplete((aVoid, throwable) -> System.out.println("2"));

        ZooKeeperUtils.runAsync(
                Arrays.asList(
                        () -> {
                            try {
                                Thread.sleep(1_000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            System.out.println("a-1");
                        },
                        () -> {
                            System.out.println("b-1");
                            try {
                                Thread.sleep(1_000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                ),
                executorService
        )
                .whenComplete((aVoid, throwable) -> System.out.println("c-2"));
        Thread.sleep(1_0000L);
    }
}
