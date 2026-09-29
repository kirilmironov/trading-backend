package com.example.demo.entity.futures;

import com.example.demo.entity.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "futures_positions")
public class FuturesPosition {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String symbol;
    private String positionSide;
    private double quantity;
    private double entryPrice;
    private int leverage;
    private double initialMargin;
    private double maintenanceMargin;
    private double unrealizedPnl;
    private double realizedPnl;
    private Double liquidationPrice;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime closedAt;

    public FuturesPosition() { this.createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getPositionSide() { return positionSide; }
    public void setPositionSide(String positionSide) { this.positionSide = positionSide; }
    public double getQuantity() { return quantity; }
    public void setQuantity(double quantity) { this.quantity = quantity; }
    public double getEntryPrice() { return entryPrice; }
    public void setEntryPrice(double entryPrice) { this.entryPrice = entryPrice; }
    public int getLeverage() { return leverage; }
    public void setLeverage(int leverage) { this.leverage = leverage; }
    public double getInitialMargin() { return initialMargin; }
    public void setInitialMargin(double initialMargin) { this.initialMargin = initialMargin; }
    public double getMaintenanceMargin() { return maintenanceMargin; }
    public void setMaintenanceMargin(double maintenanceMargin) { this.maintenanceMargin = maintenanceMargin; }
    public double getUnrealizedPnl() { return unrealizedPnl; }
    public void setUnrealizedPnl(double unrealizedPnl) { this.unrealizedPnl = unrealizedPnl; }
    public double getRealizedPnl() { return realizedPnl; }
    public void setRealizedPnl(double realizedPnl) { this.realizedPnl = realizedPnl; }
    public Double getLiquidationPrice() { return liquidationPrice; }
    public void setLiquidationPrice(Double liquidationPrice) { this.liquidationPrice = liquidationPrice; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }
}
