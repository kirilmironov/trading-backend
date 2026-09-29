package com.example.demo.repository.futures;

import com.example.demo.entity.futures.FuturesWallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FuturesWalletRepository extends JpaRepository<FuturesWallet, Long> {
    Optional<FuturesWallet> findByUserId(Long userId);
}
