package com.example.demo.repository;

import com.example.demo.entity.Position;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PositionRepository extends JpaRepository<Position, Long> {
    List<Position> findByUserIdAndStatus(Long userId, String status);
    List<Position> findByUserUsernameAndStatus(String username, String status);
    List<Position> findBySymbolAndStatus(String symbol, String status);
}