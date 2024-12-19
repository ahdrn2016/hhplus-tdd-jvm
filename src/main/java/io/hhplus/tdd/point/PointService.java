package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import static io.hhplus.tdd.point.TransactionType.CHARGE;
import static io.hhplus.tdd.point.TransactionType.USE;

@RequiredArgsConstructor
@Service
public class PointService {

    private final UserPointTable userPointTable;
    private final PointHistoryTable pointHistoryTable;
    private final ConcurrentHashMap<Long, ReentrantLock> lockMap = new ConcurrentHashMap<>();

    public UserPoint point(long id) {
        return userPointTable.selectById(id);
    }

    public List<PointHistory> history(long id) {
        return pointHistoryTable.selectAllByUserId(id);
    }

    public UserPoint charge(long id, long amount) {
        if (amount < 10_000) {
            throw new RuntimeException("1만 포인트부터 충전 가능합니다.");
        }

        ReentrantLock lock = lockMap.computeIfAbsent(id, key -> new ReentrantLock(true));
        lock.lock();

        try {
            UserPoint origin = userPointTable.selectById(id);
            if (origin.point() + amount > 1_000_000) {
                throw new RuntimeException("보유 포인트 잔액은 100만 포인트를 초과할 수 없습니다.");
            }

            UserPoint userPoint = userPointTable.insertOrUpdate(id, origin.point() + amount);
            pointHistoryTable.insert(id, amount, CHARGE, System.currentTimeMillis());

            return userPoint;

        } finally {
            lock.unlock();
            lockMap.computeIfPresent(id, (key, existingLock) -> existingLock.hasQueuedThreads() ? existingLock : null);
        }
    }

    public UserPoint use(long id, long amount) {
        if (amount <= 0) {
            throw new RuntimeException("사용할 포인트를 입력해주세요.");
        }
        if (amount > 1_000_000) {
            throw new RuntimeException("최대 사용 가능한 포인트는 100만 포인트입니다.");
        }

        ReentrantLock lock = lockMap.computeIfAbsent(id, key -> new ReentrantLock(true));
        lock.lock();

        try {
            UserPoint origin = userPointTable.selectById(id);
            if (amount > origin.point()) {
                throw new RuntimeException("보유 포인트보다 초과 사용할 수 없습니다.");
            }

            UserPoint userPoint = userPointTable.insertOrUpdate(id, origin.point() - amount);
            pointHistoryTable.insert(id, amount, USE, System.currentTimeMillis());

            return userPoint;

        } finally {
            lock.unlock();
            lockMap.computeIfPresent(id, (key, existingLock) -> existingLock.hasQueuedThreads() ? existingLock : null);
        }
    }
}
