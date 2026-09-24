package com.example.demo.dto;

public class OrderRequest {
    private String symbol;
    private double quantity;    // Променено от int на double за поддръжка на дробни крипто суми
    private String type;        // BUY / SELL
    private String side;        // BUY / SELL
    private String orderType;   // MARKET / LIMIT
    private Double targetPrice; // Цената за Limit поръчки
    
    private Double takeProfit; // Ценово ниво за Take Profit при отваряне на позиция
    private Double stopLoss;   // Ценово ниво за Stop Loss при отваряне на позиция

    // --- GETTERS & SETTERS ---
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public double getQuantity() { return quantity; }
    public void setQuantity(double quantity) { this.quantity = quantity; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getSide() { return side != null ? side : type; }
    public void setSide(String side) { this.side = side; }

    public String getOrderType() { return orderType; }
    public void setOrderType(String orderType) { this.orderType = orderType; }

    public Double getTargetPrice() { return targetPrice; }
    public void setTargetPrice(Double targetPrice) { this.targetPrice = targetPrice; }

    public Double getTakeProfit() { return takeProfit; }
    public void setTakeProfit(Double takeProfit) { this.takeProfit = takeProfit; }

    public Double getStopLoss() { return stopLoss; }
    public void setStopLoss(Double stopLoss) { this.stopLoss = stopLoss; }
}