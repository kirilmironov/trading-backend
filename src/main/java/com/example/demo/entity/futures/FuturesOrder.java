package com.example.demo.entity.futures;

import com.example.demo.entity.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "futures_orders")
public class FuturesOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String symbol;
    private double quantity;
    private Double price;
    private String side;
    private String positionSide;
    private String orderType;
    private Double targetPrice;
    private Integer leverage;
    private boolean reduceOnly;
    private String status;
    private LocalDateTime timestamp;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public double getQuantity() { return quantity; }
    public void setQuantity(double quantity) { this.quantity = quantity; }
    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }
    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }
    public String getPositionSide() { return positionSide; }
    public void setPositionSide(String positionSide) { this.positionSide = positionSide; }
    public String getOrderType() { return orderType; }
    public void setOrderType(String orderType) { this.orderType = orderType; }
    public Double getTargetPrice() { return targetPrice; }
    public void setTargetPrice(Double targetPrice) { this.targetPrice = targetPrice; }
    public Integer getLeverage() { return leverage; }
    public void setLeverage(Integer leverage) { this.leverage = leverage; }
    public boolean isReduceOnly() { return reduceOnly; }
    public void setReduceOnly(boolean reduceOnly) { this.reduceOnly = reduceOnly; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
