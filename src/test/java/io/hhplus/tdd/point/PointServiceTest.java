package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

class PointServiceTest {

    private final UserPointTable userPointTable = mock(UserPointTable.class);
    private final PointHistoryTable pointHistoryTable = mock(PointHistoryTable.class);
    private final PointService pointService = new PointService(userPointTable, pointHistoryTable);

    @Test
    void 충전_포인트가_1만원_미만이면_요청은_실패한다() {
        // given
        Long id = 1L;
        Long amount = 9_999L;
        given(userPointTable.selectById(id))
                .willReturn(new UserPoint(id, amount, System.currentTimeMillis()));

        // when // then
        assertThrows(
                Exception.class,
                () -> pointService.charge(id, amount)
        );
        verify(userPointTable, never()).selectById(anyLong());
    }

    @Test
    void 충전_포인트가_100만원_초과하면_요청은_실패한다() {
        // given
        Long id = 1L;
        Long amount = 10_000_000L;
        given(userPointTable.selectById(id))
                .willReturn(new UserPoint(id, amount, System.currentTimeMillis()));

        // when // then
        assertThrows(
                Exception.class,
                () -> pointService.charge(id, amount)
        );
        verify(userPointTable, times(1)).selectById(anyLong());
    }
    
    @Test
    void 충전_포인트와_보유_포인트의_합산이_100만원_초과하면_요청은_실패한다() {
        // given
        Long id = 1L;
        Long amount = 100_000L;
        given(userPointTable.selectById(id))
                .willReturn(new UserPoint(id, 1_000_000L, System.currentTimeMillis()));

        // when // then
        assertThrows(
                Exception.class,
                () -> pointService.charge(id, amount)
        );
        verify(userPointTable, times(1)).selectById(anyLong());
    }

    @Test
    void 포인트_충전_요청에_성공한다() {
        // given
        Long id = 1L;
        Long amount = 50_000L;
        Long originPoint = 100_000L;
        Long chargeAfterPoint = originPoint + amount;

        given(userPointTable.selectById(id))
                .willReturn(new UserPoint(id, originPoint, System.currentTimeMillis()));
        given(userPointTable.insertOrUpdate(id, chargeAfterPoint))
                .willReturn(new UserPoint(id, chargeAfterPoint, System.currentTimeMillis()));

        // when
        UserPoint userPoint = pointService.charge(id, amount);

        // then
        assertEquals(chargeAfterPoint, userPoint.point());
        verify(userPointTable, times(1)).selectById(anyLong());
        verify(userPointTable, times(1)).insertOrUpdate(anyLong(), anyLong());
    }

    @Test
    void 사용_포인트가_0원_이하면_실패한다() {
        // given
        Long id = 1L;
        Long amount = 0L;

        given(userPointTable.selectById(id))
                .willReturn(new UserPoint(id, amount, System.currentTimeMillis()));

        // when // then
        assertThrows(
                Exception.class,
                () -> pointService.use(id, amount)
        );
        verify(userPointTable, never()).selectById(anyLong());
    }

    @Test
    void 사용_포인트가_100만원_초과하면_실패한다() {
        // given
        Long id = 1L;
        Long amount = 10_000_000L;
        given(userPointTable.selectById(id))
                .willReturn(new UserPoint(id, amount, System.currentTimeMillis()));

        // when // then
        assertThrows(
                Exception.class,
                () -> pointService.use(id, amount)
        );
        verify(userPointTable, never()).selectById(anyLong());
    }

    @Test
    void 사용_포인트가_보유_포인트보다_크면_요청은_실패한다() {
        // given
        Long id = 1L;
        Long amount = 500_000L;

        given(userPointTable.selectById(id))
                .willReturn(new UserPoint(id, 100_000L, System.currentTimeMillis()));

        // when // then
        assertThrows(
                Exception.class,
                () -> pointService.use(id, amount)
        );
        verify(userPointTable, times(1)).selectById(anyLong());
    }

    @Test
    void 포인트_사용_요청에_성공한다() {
        // given
        Long id = 1L;
        Long amount = 50_000L;
        Long originPoint = 100_000L;
        Long useAfterPoint = originPoint - amount;

        given(userPointTable.selectById(id))
                .willReturn(new UserPoint(id, originPoint, System.currentTimeMillis()));
        given(userPointTable.insertOrUpdate(id, useAfterPoint))
                .willReturn(new UserPoint(id, useAfterPoint, System.currentTimeMillis()));

        // when
        UserPoint userPoint = pointService.use(id, amount);

        // then
        assertEquals(useAfterPoint, userPoint.point());
        verify(userPointTable, times(1)).selectById(anyLong());
        verify(userPointTable, times(1)).insertOrUpdate(anyLong(), anyLong());
    }
  
}