package com.example.demo.dto;

public class OrderRequest {
    private String symbol;
    private int quantity;
    private String type;        // BUY / SELL
    private String side;        // BUY / SELL
    private String orderType;   // MARKET / LIMIT / STOP_LOSS / TAKE_PROFIT
    private Double targetPrice; // Цената за Limit/Stop поръчки

    // --- GETTERS & SETTERS ---
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getSide() { return side != null ? side : type; }
    public void setSide(String side) { this.side = side; }

    public String getOrderType() { return orderType; }
    public void setOrderType(String orderType) { this.orderType = orderType; }

    public Double getTargetPrice() { return targetPrice; }
    public void setTargetPrice(Double targetPrice) { this.targetPrice = targetPrice; }
}