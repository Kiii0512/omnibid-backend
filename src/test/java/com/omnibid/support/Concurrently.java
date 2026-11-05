package com.omnibid.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class Concurrently {

    private Concurrently() {}

    @FunctionalInterface
    public interface IndexedTask<T> {
        T call(int index) throws Exception;
    }

    public static <T> List<T> run(int n, Callable<T> task) throws Exception {
        return runIndexed(n, index -> task.call());
    }

    public static <T> List<T> runIndexed(int n, IndexedTask<T> task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(n);
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(n);

        List<Future<T>> futures = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < n; i++) {
            final int index = i;
            futures.add(executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    return task.call(index);
                } finally {
                    done.countDown();
                }
            }));
        }

        ready.await(10, TimeUnit.SECONDS);
        start.countDown();
        done.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        List<T> results = new ArrayList<>();
        for (Future<T> f : futures) {
            results.add(f.get());
        }
        return results;
    }
}
