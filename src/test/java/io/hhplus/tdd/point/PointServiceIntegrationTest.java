package io.hhplus.tdd.point;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PointServiceIntegrationTest {

    @Autowired
    private PointService pointService;

    @Test
    void 특정_유저가_동시에_포인트_충전_5회_요청에_성공한다() throws InterruptedException {
        // given
        Long id = 1L;
        Long amount = 10_000L;

        int concurrentRequest = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(concurrentRequest);
        CountDownLatch latch = new CountDownLatch(concurrentRequest);
        List<Runnable> tasks = List.of(
                () -> pointService.charge(id, amount),
                () -> pointService.charge(id, amount),
                () -> pointService.charge(id, amount),
                () -> pointService.charge(id, amount),
                () -> pointService.charge(id, amount)
        );

        // when
        tasks.forEach(task -> executorService.submit(() -> {
            try {
                task.run();
            } finally {
                latch.countDown();
            }
        }));
        latch.await();
        executorService.shutdown();

        // then
        UserPoint userPoint = pointService.point(id);
        assertEquals(50_000L, userPoint.point());
    }

    @Test
    void 특정_유저가_동시에_포인트_충전_및_사용_5회_요청에_성공한다() throws InterruptedException {
        // given
        Long id = 2L;
        Long amount = 10_000L;

        int concurrentRequest = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(concurrentRequest);
        CountDownLatch latch = new CountDownLatch(concurrentRequest);
        List<Runnable> tasks = List.of(
                () -> pointService.charge(id, amount),
                () -> pointService.use(id, amount),
                () -> pointService.charge(id, amount),
                () -> pointService.use(id, amount),
                () -> pointService.charge(id, amount)
        );

        // when
        tasks.forEach(task -> executorService.submit(() -> {
            try {
                task.run();
            } finally {
                latch.countDown();
            }
        }));
        latch.await();
        executorService.shutdown();

        // then
        UserPoint userPoint = pointService.point(id);
        assertEquals(10_000L, userPoint.point());
    }

    @Test
    void 유저_100명이_동시에_포인트_충전_및_사용_요청에_성공한다() throws InterruptedException {
        // given
        Long amount = 10_000L;
        int concurrentRequest = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(concurrentRequest);
        CountDownLatch latch = new CountDownLatch(concurrentRequest);
        List<Runnable> tasks = new ArrayList<>();
        for (Long userId = 1L; userId <= concurrentRequest; userId++) {
            Long finalUserId = userId;
            tasks.add(() -> pointService.charge(finalUserId, amount));
            tasks.add(() -> pointService.use(finalUserId, amount));
        }

        // when
        tasks.forEach(task -> executorService.submit(() -> {
            try {
                task.run();
            } finally {
                latch.countDown();
            }
        }));
        latch.await();
        executorService.shutdown();

        // then
        for (Long userId = 1L; userId <= concurrentRequest; userId++) {
            UserPoint userPoint = pointService.point(userId);
            assertEquals(0L, userPoint.point());
        }
    }

}