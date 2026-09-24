package com.example.demo.service;

import com.example.demo.entity.Stock;
import com.example.demo.repository.StockRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class StockService {

    private final StockRepository stockRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final MatchingEngineService matchingEngineService; // 1. Инжектираме MatchingEngineService

    public StockService(StockRepository stockRepository, 
                        SimpMessagingTemplate messagingTemplate,
                        MatchingEngineService matchingEngineService) {
        this.stockRepository = stockRepository;
        this.messagingTemplate = messagingTemplate;
        this.matchingEngineService = matchingEngineService;
    }

    public void updateStockPrice(String symbol, double price) {
        int scale = (price < 1.0) ? 4 : 2;
        BigDecimal roundedPrice = BigDecimal.valueOf(price).setScale(scale, RoundingMode.HALF_UP);
        double finalPrice = roundedPrice.doubleValue();

        Stock stock = stockRepository.findById(symbol)
                .orElseGet(() -> new Stock(symbol, getCompanyName(symbol), finalPrice));

        stock.setPrice(finalPrice);
        stockRepository.save(stock);

        // 2. ИЗПОЛЗВАМЕ MATCHING ENGINE: Проверяваме LIMIT поръчки и TP/SL нива на позициите за новата цена
        try {
            matchingEngineService.processPendingOrders(symbol, finalPrice);
        } catch (Exception e) {
            System.err.println("Error processing matching engine for " + symbol + ": " + e.getMessage());
        }

        // Изпращаме новата цена към UI
        messagingTemplate.convertAndSend("/topic/ticks", stock);
    }

    private String getCompanyName(String symbol) {
        switch (symbol) {
            case "BTCUSDC": return "Bitcoin";
            case "ETHUSDC": return "Ethereum";
            case "SOLUSDC": return "Solana";
            case "BNBUSDC": return "BNB";
            case "ADAUSDC": return "Cardano";
            case "XRPUSDC": return "XRP";
            case "DOGEUSDC": return "Dogecoin";
            case "AVAXUSDC": return "Avalanche";
            case "DOTUSDC": return "Polkadot";
            case "LINKUSDC": return "Chainlink";
            default: return symbol;
        }
    }
}