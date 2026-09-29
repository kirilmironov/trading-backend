package com.example.demo.repository.spot;

import com.example.demo.entity.spot.SpotPosition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SpotPositionRepository extends JpaRepository<SpotPosition, Long> {
    List<SpotPosition> findByUserIdAndStatus(Long userId, String status);
}
