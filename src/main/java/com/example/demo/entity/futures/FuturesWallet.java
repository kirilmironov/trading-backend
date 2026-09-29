package com.example.demo.entity.futures;

import com.example.demo.entity.User;
import jakarta.persistence.*;

@Entity
@Table(name = "futures_wallets")
public class FuturesWallet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false)
    private double availableBalance;

    public FuturesWallet() { }

    public FuturesWallet(User user) {
        this.user = user;
        this.availableBalance = 0.0;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public double getAvailableBalance() { return availableBalance; }
    public void setAvailableBalance(double availableBalance) { this.availableBalance = availableBalance; }
}
