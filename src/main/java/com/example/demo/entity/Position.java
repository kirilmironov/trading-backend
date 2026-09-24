package com.example.demo.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "positions")
public class Position {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String symbol;      // напр. BTCUSDC
    private String side;        // "LONG" или "SHORT"
    private double quantity;    // Напр. 1.0
    private double entryPrice;  // Цена на отваряне (напр. 86000.0)
    
    private Double takeProfit;  // Незадължителна цена за Take Profit
    private Double stopLoss;    // Незадължителна цена за Stop Loss

    private String status;      // "OPEN" или "CLOSED"
    private LocalDateTime createdAt;
    private LocalDateTime closedAt;

    public Position() {
        this.createdAt = LocalDateTime.now();
    }

    public Position(User user, String symbol, String side, double quantity, double entryPrice, Double takeProfit, Double stopLoss) {
        this.user = user;
        this.symbol = symbol;
        this.side = side;
        this.quantity = quantity;
        this.entryPrice = entryPrice;
        this.takeProfit = takeProfit;
        this.stopLoss = stopLoss;
        this.status = "OPEN";
        this.createdAt = LocalDateTime.now();
    }

    // --- GETTERS & SETTERS ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }

    public double getQuantity() { return quantity; }
    public void setQuantity(double quantity) { this.quantity = quantity; }

    public double getEntryPrice() { return entryPrice; }
    public void setEntryPrice(double entryPrice) { this.entryPrice = entryPrice; }

    public Double getTakeProfit() { return takeProfit; }
    public void setTakeProfit(Double takeProfit) { this.takeProfit = takeProfit; }

    public Double getStopLoss() { return stopLoss; }
    public void setStopLoss(Double stopLoss) { this.stopLoss = stopLoss; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }
}