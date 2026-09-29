package com.example.demo.repository.spot;

import com.example.demo.entity.spot.SpotOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SpotOrderRepository extends JpaRepository<SpotOrder, Long> {
    List<SpotOrder> findByUserId(Long userId);
    List<SpotOrder> findByUserIdAndStatus(Long userId, String status);
    List<SpotOrder> findBySymbolAndStatus(String symbol, String status);
}
