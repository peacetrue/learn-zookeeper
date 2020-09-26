package com.github.peacetrue.learn.zookeeper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author : xiayx
 * @since : 2020-09-26 07:54
 **/
public class LocalCountService implements CountService {

    private Path path = Paths.get("/Users/xiayx/Documents/Projects/learn-zookeeper/src/test/resources/count.txt");

    public void reset() {
        try {
            Files.write(path, "0".getBytes());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public Long get() {
        try {
            return Long.parseLong(new String(Files.readAllBytes(path)).replace("\n", ""));
        } catch (Exception e) {
            System.err.println(e);
            throw new IllegalStateException(e);
        }
    }

    public Long getAndIncr() {
        long count = get() + 1;
        try {
            String s = String.valueOf(count);
            System.out.printf("%s to %s", count - 1, count).println();
            Files.write(path, s.getBytes());
        } catch (Exception e) {
            System.err.println(e);
            throw new IllegalStateException(e);
        }
        return count;
    }
}
