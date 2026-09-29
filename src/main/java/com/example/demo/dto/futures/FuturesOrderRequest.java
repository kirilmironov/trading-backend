package com.example.demo.dto.futures;

public class FuturesOrderRequest {
    private String symbol;
    private double quantity;
    private String side;
    private String positionSide;
    private String orderType = "MARKET";
    private Double targetPrice;
    private Integer leverage = 1;

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public double getQuantity() { return quantity; }
    public void setQuantity(double quantity) { this.quantity = quantity; }
    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }
    public String getPositionSide() { return positionSide; }
    public void setPositionSide(String positionSide) { this.positionSide = positionSide; }
    public String getOrderType() { return orderType == null ? "MARKET" : orderType; }
    public void setOrderType(String orderType) { this.orderType = orderType; }
    public Double getTargetPrice() { return targetPrice; }
    public void setTargetPrice(Double targetPrice) { this.targetPrice = targetPrice; }
    public Integer getLeverage() { return leverage == null ? 1 : leverage; }
    public void setLeverage(Integer leverage) { this.leverage = leverage; }
}
