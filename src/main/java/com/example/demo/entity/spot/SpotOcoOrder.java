package com.example.demo.entity.spot;

import com.example.demo.entity.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "spot_oco_orders")
public class SpotOcoOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String symbol;
    private String side;
    private double quantity;
    private double takeProfitPrice;
    private double stopPrice;
    private double stopLimitPrice;
    private boolean stopTriggered;
    private String status;
    private String executedLeg;
    private Double executionPrice;
    private LocalDateTime createdAt;
    private LocalDateTime closedAt;

    public SpotOcoOrder() { this.createdAt = LocalDateTime.now(); }

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
    public double getTakeProfitPrice() { return takeProfitPrice; }
    public void setTakeProfitPrice(double takeProfitPrice) { this.takeProfitPrice = takeProfitPrice; }
    public double getStopPrice() { return stopPrice; }
    public void setStopPrice(double stopPrice) { this.stopPrice = stopPrice; }
    public double getStopLimitPrice() { return stopLimitPrice; }
    public void setStopLimitPrice(double stopLimitPrice) { this.stopLimitPrice = stopLimitPrice; }
    public boolean isStopTriggered() { return stopTriggered; }
    public void setStopTriggered(boolean stopTriggered) { this.stopTriggered = stopTriggered; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getExecutedLeg() { return executedLeg; }
    public void setExecutedLeg(String executedLeg) { this.executedLeg = executedLeg; }
    public Double getExecutionPrice() { return executionPrice; }
    public void setExecutionPrice(Double executionPrice) { this.executionPrice = executionPrice; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }
}
