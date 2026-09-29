package com.example.demo.repository.futures;

import com.example.demo.entity.futures.FuturesOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FuturesOrderRepository extends JpaRepository<FuturesOrder, Long> {
    List<FuturesOrder> findByUserId(Long userId);
    List<FuturesOrder> findByUserIdAndStatus(Long userId, String status);
    List<FuturesOrder> findBySymbolAndStatus(String symbol, String status);
}
