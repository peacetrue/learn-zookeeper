package com.github.peacetrue.learn.zookeeper;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.zookeeper.*;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * @author : xiayx
 * @since : 2020-09-20 22:59
 **/
@Slf4j
public class ZooKeeperUtils {

    public static ZooKeeper getZooKeeper() throws IOException {
        Thread thread = Thread.currentThread();
        Watcher watcher = event -> {
            log.info("session watch: [{}]", event);
            if (event.getState() == Watcher.Event.KeeperState.SyncConnected) {
                LockSupport.unpark(thread);
            }
        };
        ZooKeeper zooKeeper = getZooKeeper(watcher);
        LockSupport.park();
        return zooKeeper;
    }

    public static CompletableFuture<ZooKeeper> getZooKeeperFuture() throws IOException {
        boolean[] booleans = {false};
        ZooKeeper zooKeeper = getZooKeeper(event -> {
            log.info("session watch: [{}]", event);
            if (event.getState() == Watcher.Event.KeeperState.SyncConnected) {
                booleans[0] = true;
            }
        });
        return CompletableFuture.supplyAsync(() -> {
            while (true) if (booleans[0]) return zooKeeper;
        });
    }

    /** @see CompletableFuture#supplyAsync(Supplier, Executor) */
    public static <T> CompletableFuture<List<T>> supplyAsync(List<Supplier<T>> suppliers, Executor executor) {
        List<CompletableFuture<T>> futures = suppliers.stream()
                .map(supplier -> CompletableFuture.supplyAsync(supplier, executor))
                .collect(Collectors.toList());
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApplyAsync((voids) -> futures.stream().map(CompletableFuture::join).collect(Collectors.toList()), executor);
    }

    /** @see CompletableFuture#runAsync(Runnable, Executor) */
    public static CompletableFuture<Void> runAsync(List<Runnable> runnables, Executor executor) {
        return CompletableFuture.allOf(runnables.stream().map(
                runnable -> CompletableFuture.runAsync(runnable, executor)
        ).toArray(CompletableFuture[]::new));
    }

    @Data
    @AllArgsConstructor
    public static class R<T> {
        private boolean over;
        private T value;

        public T value() {
            while (true) if (over) return value;
        }

        public T value(long timeout, TimeUnit timeUnit) throws TimeoutException {
            long start = System.nanoTime();
            long timeoutNanos = timeUnit.toNanos(timeout);
            while (true) {
                if (over) return value;
                long pass = System.nanoTime() - start;
                if (pass > timeoutNanos) throw new TimeoutException();
            }
        }
    }

    public static <T> CompletableFuture<T> completableFuture(Consumer<Consumer<T>> consumer, ExecutorService executorService) {
        R<T> r = new R<>(false, null);
        consumer.accept(value -> {
            r.over = true;
            r.value = value;
        });
        return CompletableFuture.supplyAsync(r::value, executorService);
    }

    public static <T> CompletableFuture<T> completableFuture2(Function<Runnable, T> function) {
        R<T> r = new R<>(false, null);
        r.setValue(function.apply(() -> r.over = true));
        return CompletableFuture.supplyAsync(r::value);
    }

    public static CompletableFuture<ZooKeeper> getZooKeeperFuture2() {
        return completableFuture2(valve -> {
            try {
                return getZooKeeper(event -> {
                    if (event.getState() == Watcher.Event.KeeperState.SyncConnected) {
                        valve.run();
                    }
                });
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private static ZooKeeper getZooKeeper(Watcher watcher) throws IOException {
        String connectString = "10.0.0.41:2181,10.0.0.42:2181,10.0.0.43:2181";
        return new ZooKeeper(
                connectString,
                5_000,
                watcher
        );
    }

    private static final String prefix = "/id/";

    public static Long generateId(ZooKeeper zooKeeper) {
        try {
            String path = zooKeeper.create(prefix, null, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
            return Long.parseLong(path.substring(prefix.length()));
        } catch (KeeperException | InterruptedException | NumberFormatException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void generateId4callback(ZooKeeper zooKeeper, Consumer<Long> consumer) {
        zooKeeper.create(prefix, null, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL,
                (rc, path, ctx, name) -> consumer.accept(Long.parseLong(name.substring(prefix.length()))), null);
    }

    public static long generateId4version(ZooKeeper zooKeeper) {
        try {
            return zooKeeper.setData("/lock", null, -1).getVersion();
        } catch (KeeperException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void generateId4versionC(ZooKeeper zooKeeper, Consumer<Long> consumer) {
        zooKeeper.setData("/lock", null, -1,
                (rc, path, ctx, stat) -> consumer.accept((long) stat.getVersion()), null);
    }
}
