package com.example.demo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.stereotype.Service;

import java.net.URI;

@Service
public class BinanceWebSocketService {

    private final StockService stockService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private WebSocketClient webSocketClient;

    public BinanceWebSocketService(StockService stockService) {
        this.stockService = stockService;
    }

    @PostConstruct
    public void connectToBinance() {
        String streamUrl = "wss://stream.binance.com:9443/stream?streams=" +
            "btcusdc@ticker/ethusdc@ticker/solusdc@ticker/bnbusdc@ticker/adausdc@ticker" +
            "/xrpusdc@ticker/dogeusdc@ticker/avaxusdc@ticker/dotusdc@ticker/linkusdc@ticker";

        try {
            webSocketClient = new WebSocketClient(new URI(streamUrl)) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    System.out.println(">>> WebSocket connected to Binance (10 USDC Streams)!");
                }

                @Override
                public void onMessage(String message) {
                    try {
                        JsonNode jsonNode = objectMapper.readTree(message);
                        JsonNode ticker = jsonNode.has("data") ? jsonNode.get("data") : jsonNode;

                        if (ticker.has("s") && ticker.has("c")) {
                            String symbol = ticker.get("s").asText();
                            double price = ticker.get("c").asDouble();

                            stockService.updateStockPrice(symbol, price);
                        }
                    } catch (Exception e) {
                        if (webSocketClient != null && webSocketClient.isClosed()) return;
                        e.printStackTrace();
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    System.out.println(">>> Binance WebSocket connection closed: " + reason);
                }

                @Override
                public void onError(Exception ex) {
                    if (webSocketClient != null && webSocketClient.isOpen()) ex.printStackTrace();
                }
            };

            webSocketClient.connect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @PreDestroy
    public void stopWebSocket() {
        if (webSocketClient != null) {
            System.out.println(">>> Closing Binance WebSocket connection...");
            webSocketClient.close();
        }
    }
}