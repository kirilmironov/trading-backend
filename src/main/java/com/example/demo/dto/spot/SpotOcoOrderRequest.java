package com.example.demo.dto.spot;

public class SpotOcoOrderRequest {
    private String symbol;
    private String side;
    private double quantity;
    private Double takeProfitPrice;
    private Double stopPrice;
    private Double stopLimitPrice;

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }
    public double getQuantity() { return quantity; }
    public void setQuantity(double quantity) { this.quantity = quantity; }
    public Double getTakeProfitPrice() { return takeProfitPrice; }
    public void setTakeProfitPrice(Double takeProfitPrice) { this.takeProfitPrice = takeProfitPrice; }
    public Double getStopPrice() { return stopPrice; }
    public void setStopPrice(Double stopPrice) { this.stopPrice = stopPrice; }
    public Double getStopLimitPrice() { return stopLimitPrice; }
    public void setStopLimitPrice(Double stopLimitPrice) { this.stopLimitPrice = stopLimitPrice; }
}
