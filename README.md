## 동시성 제어 방식에 대한 분석 및 보고서

### 개요

포인트 충전 및 사용하는 기능이 동시에 발생할 수 있는 점에 대해,
1. 특정 유저가 동시에 포인트 충전 5회 요청에 성공한다.
2. 특정 유저가 동시에 포인트 충전 및 사용 5회 요청에 성공한다.
3. 유저 100명이 동시에 포인트 충전 요청에 성공한다.

3가지 케이스로 **ConcurrentHashMap**과 **ReentrantLock**을 이용하여 동시성에 대해 테스트하였습니다.

---

### 동시성 제어 방법

#### **1. synchronized**
synchronized는 특정 블록이나 메서드를 동기화하여 한 번에 하나의 스레드만 접근할 수 있도록 할 수 있습니다.  
이때, 선택된 하나의 스레드를 제외한 나머지 스레드는 대기해야 합니다.
'내가 내 포인트 충전하는데 왜 다른 사람 충전하는 걸 기다려야 하지?' 이런 생각이 드는 순간, 유저들은 이 서비스가 비효율적이라고 느낄 수 있습니다.

#### **2. ReentrantLock**
ReentrantLock은 스레드가 동시에 특정 코드에 접근하지 못하도록 문을 잠그는 도구입니다.
lock()으로 문을 잠그고, unlock()으로 문을 엽니다.
문 앞에 먼저 온 순서대로 들어갈 수 있게 만드는 공정 모드 옵션이 있지만, 이 또한 한 사람이 작업 중이면 다른 사람은 무조건 기다려야 합니다.

#### **3. ConcurrentHashMap**
ConcurrentHashMap은 여러 사람이 동시에 데이터를 사용할 수 있는 공유 저장소입니다.
데이터를 여러 줄로 나눠서 관리하기 때문에, 다른 줄에 있는 데이터는 동시에 접근 가능합니다.
읽는 작업은 기다릴 필요가 없고, 쓰는 작업도 최소한의 기다림으로 처리되기에 빠르고 안전합니다.
단, 하나의 키에 대해 동시에 접근하려는 충돌은 제어할 수 없기에 같은 유저가 충전함과 동시에 조회하려 하면 문제가 생길 수 있습니다.

---

### 동시성 제어 구현

ReentrantLock과 ConcurrentHashMap을 조합하여 유저 별로 문을 잠그는 방식으로 풀어나갔습니다.
1. ConcurrentHashMap으로 유저의 데이터를 저장한다.
2. 유저 별로 문을 따로 준비 (ReentrantLock)해서, 각자 자신의 문만 잠그고 작업한다.

**ReentrantLock**은 **하나의 문을 잠그고 여는 열쇠** 🔑, **ConcurrentHashMap**은 **각자 데이터를 저장하는 공간** 📦

```java
@RequiredArgsConstructor
@Service
class PointService {

	private final ConcurrentHashMap<Long, ReentrantLock> lockMap = new ConcurrentHashMap<>();

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
}
```

1. ConcurrentHashMap을 사용한 잠금 관리
   - 유저 별로 ReentrantLock을 저장하는 lockMap을 생성합니다.
   - 특정 유저에 대해 동기화를 제공하므로 다른 유저의 작업에는 영향을 주지 않습니다.
2. ReentrantLock 생성 및 공정 모드 설정
   - computeIfAbsent를 사용하여 유저에 대한 잠금을 생성합니다.
   - **공정 모드(true)** 를 설정하여, 잠금을 요청한 순서대로 스레드가 처리됩니다.
3. 잠금 해제 및 정리
   - 작업이 끝나면 lock.unlock()로 잠금을 해제합니다.
   - computeIfPresent를 사용하여 대기 중인 스레드가 없으면 lockMap에서 해당 잠금을 제거해 메모리 누수를 방지합니다.
4. 포인트 충전 로직
   - 기존 포인트를 조회하고, 충전 후 최대 보유량(100만 포인트)을 초과하지 않는지 확인합니다.
   - 충전 내역을 기록합니다.

---

### 동시성 테스트

**ExecutorService**와 **CountDownLatch**를 사용하여 동시에 여러 작업을 수행할 수 있도록 설정하였습니다.

1. ExecutorService는 스레드 풀로 병렬 작업을 효율적으로 실행한다.
2. CountDownLatch는 특정 작업들이 끝날 때까지 대기한다.

```java
@Test
void 특정_유저가_동시에_포인트_충전_및_사용_5회_요청에_성공한다() throws InterruptedException {
    // given
    Long id = 1L;
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
```

1. 초기 설정
    - 동시 요청을 처리할 수 있는 ExecutorService을 생성합니다.
    - 모든 작업이 완료될 때까지 대기하는 CountDownLatch를 생성합니다.
2. 작업 정의
    - tasks 리스트에 포인트 충전 및 사용을 위한 작업을 정의합니다.
3. 작업 실행
    - tasks 리스트의 각 작업을 스레드 풀에 제출하여 실행합니다.
    - 각 작업이 끝나면 latch.countDown()을 호출하여 CountDownLatch의 카운트를 줄입니다.
    - 모든 작업이 완료될 때까지 latch.await()로 대기합니다.
    - executorService.shutdown()을 호출하여 스레드 풀을 종료합니다.
4. 결과 검증
    - 특정 유저의 현재 포인트를 조회합니다.
    - 기대하는 포인트 금액(10,000)이 실제 포인트와 일치하는지 검증합니다.

---

### 결론

동시성 테스트 코드를 통해 동시에 요청이 들어오더라도 각 유저별로 잠금을 걸어 서로의 작업에 방해가 되지 않음을 확인했습니다.

이를 통해 데이터 일관성이 보장되며, 동시성 제어가 효과적으로 이루어지고 있음을 검증할 수 있었습니다.

이러한 방식으로 스레드 안전성을 확보함으로써, 여러 유저가 동시에 포인트를 충전하고 사용할 때 발생할 수 있는 문제를 예방할 수 있습니다.