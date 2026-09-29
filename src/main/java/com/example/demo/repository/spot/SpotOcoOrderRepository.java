package com.example.demo.repository.spot;

import com.example.demo.entity.spot.SpotOcoOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;

public interface SpotOcoOrderRepository extends JpaRepository<SpotOcoOrder, Long> {
    List<SpotOcoOrder> findByUserId(Long userId);
    List<SpotOcoOrder> findByUserIdAndStatus(Long userId, String status);
    List<SpotOcoOrder> findBySymbolAndStatus(String symbol, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from SpotOcoOrder o where o.symbol = :symbol and o.status = :status")
    List<SpotOcoOrder> findPendingForUpdate(@Param("symbol") String symbol, @Param("status") String status);
}
