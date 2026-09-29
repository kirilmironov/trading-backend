package com.example.demo.repository.futures;

import com.example.demo.entity.futures.FuturesPosition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FuturesPositionRepository extends JpaRepository<FuturesPosition, Long> {
    List<FuturesPosition> findByUserIdAndStatus(Long userId, String status);
    List<FuturesPosition> findBySymbolAndStatus(String symbol, String status);
}
