package com.example.demo;

import com.example.demo.entity.Stock;
import com.example.demo.repository.StockRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class DataInitializer implements CommandLineRunner {

    private final StockRepository stockRepository;

    public DataInitializer(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        if (stockRepository.count() < 10) {
            stockRepository.saveAll(Arrays.asList(
                new Stock("BTCUSDC", "Bitcoin", 0.0),
                new Stock("ETHUSDC", "Ethereum", 0.0),
                new Stock("SOLUSDC", "Solana", 0.0),
                new Stock("BNBUSDC", "BNB", 0.0),
                new Stock("ADAUSDC", "Cardano", 0.0),
                new Stock("XRPUSDC", "XRP", 0.0),
                new Stock("DOGEUSDC", "Dogecoin", 0.0),
                new Stock("AVAXUSDC", "Avalanche", 0.0),
                new Stock("DOTUSDC", "Polkadot", 0.0),
                new Stock("LINKUSDC", "Chainlink", 0.0)
            ));
        }
    }
}