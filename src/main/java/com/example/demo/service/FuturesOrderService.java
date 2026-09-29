package com.example.demo.service;

import com.example.demo.dto.futures.FuturesOrderRequest;
import com.example.demo.entity.Stock;
import com.example.demo.entity.User;
import com.example.demo.entity.futures.FuturesOrder;
import com.example.demo.entity.futures.FuturesPosition;
import com.example.demo.entity.futures.FuturesWallet;
import com.example.demo.repository.StockRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.futures.FuturesOrderRepository;
import com.example.demo.repository.futures.FuturesPositionRepository;
import com.example.demo.repository.futures.FuturesWalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class FuturesOrderService {
    private final FuturesOrderRepository orderRepository;
    private final FuturesPositionRepository positionRepository;
    private final StockRepository stockRepository;
    private final UserRepository userRepository;
    private final FuturesWalletRepository walletRepository;

    public FuturesOrderService(FuturesOrderRepository orderRepository, FuturesPositionRepository positionRepository,
                               StockRepository stockRepository, UserRepository userRepository,
                               FuturesWalletRepository walletRepository) {
        this.orderRepository = orderRepository;
        this.positionRepository = positionRepository;
        this.stockRepository = stockRepository;
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
    }

    @Transactional
    public FuturesOrder createMarketOrder(FuturesOrderRequest request, Long userId) {
        return createOrder(request, userId);
    }

    @Transactional
    public FuturesOrder createOrder(FuturesOrderRequest request, Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        Stock stock = stockRepository.findById(request.getSymbol()).orElseThrow(() -> new RuntimeException("Stock not found"));
        String side = normalize(request.getSide());
        String positionSide = normalize(request.getPositionSide());
        int leverage = request.getLeverage();
        double quantity = request.getQuantity();
        double price = stock.getPrice();
        String orderType = normalize(request.getOrderType());
        Double targetPrice = request.getTargetPrice();

        if (!"MARKET".equals(orderType) && !"LIMIT".equals(orderType)) throw new IllegalArgumentException("Invalid Futures order type.");
        if (!"BUY".equals(side) && !"SELL".equals(side)) throw new IllegalArgumentException("Invalid Futures side.");
        if (!"LONG".equals(positionSide) && !"SHORT".equals(positionSide)) throw new IllegalArgumentException("Position must be LONG or SHORT.");
        if (("BUY".equals(side) && !"LONG".equals(positionSide)) || ("SELL".equals(side) && !"SHORT".equals(positionSide))) {
            throw new IllegalArgumentException("BUY opens LONG and SELL opens SHORT positions.");
        }
        if (!Double.isFinite(quantity) || quantity <= 0) throw new IllegalArgumentException("Quantity must be greater than zero.");
        if (leverage < 1 || leverage > 20) throw new IllegalArgumentException("Leverage must be between 1x and 20x.");
        if (!Double.isFinite(price) || price <= 0) throw new IllegalArgumentException("Current Futures price is unavailable.");
        if ("LIMIT".equals(orderType) && (targetPrice == null || !Double.isFinite(targetPrice) || targetPrice <= 0)) {
            throw new IllegalArgumentException("A valid Futures limit price is required.");
        }

        FuturesWallet wallet = getOrCreateWallet(user);
        if (orderRepository.findByUserIdAndStatus(userId, "PENDING").stream()
                .anyMatch(order -> request.getSymbol().equalsIgnoreCase(order.getSymbol()))) {
            throw new IllegalArgumentException("Cancel pending Futures orders before changing leverage or placing another order for this symbol.");
        }
        applySymbolLeverage(userId, request.getSymbol(), leverage, wallet);

        FuturesOrder order = new FuturesOrder();
        order.setUser(user);
        order.setSymbol(request.getSymbol());
        order.setQuantity(quantity);
        order.setSide(side);
        order.setPositionSide(positionSide);
        order.setOrderType(orderType);
        order.setTargetPrice(targetPrice);
        order.setLeverage(leverage);
        order.setReduceOnly(false);
        order.setTimestamp(LocalDateTime.now());

        boolean executeImmediately = "MARKET".equals(orderType)
                || ("BUY".equals(side) && targetPrice >= price)
                || ("SELL".equals(side) && targetPrice <= price);
        if (executeImmediately) {
            double executionPrice = price;
            double margin = executionPrice * quantity / leverage;
            if (wallet.getAvailableBalance() + 1e-8 < margin) throw new IllegalArgumentException("Insufficient Futures wallet balance for margin.");
            wallet.setAvailableBalance(wallet.getAvailableBalance() - margin);
            walletRepository.save(wallet);
            addToPosition(user, request.getSymbol(), positionSide, quantity, executionPrice, leverage, margin);
            order.setPrice(executionPrice);
            order.setStatus("EXECUTED");
        } else {
            double reservedMargin = targetPrice * quantity / leverage;
            if (wallet.getAvailableBalance() + 1e-8 < reservedMargin) throw new IllegalArgumentException("Insufficient Futures wallet balance to reserve margin.");
            wallet.setAvailableBalance(wallet.getAvailableBalance() - reservedMargin);
            walletRepository.save(wallet);
            order.setPrice(null);
            order.setStatus("PENDING");
        }
        return orderRepository.save(order);
    }

    private void addToPosition(User user, String symbol, String positionSide, double quantity, double price, int leverage, double margin) {
        FuturesPosition position = positionRepository.findByUserIdAndStatus(user.getId(), "OPEN").stream()
                .filter(existing -> existing.getSymbol().equalsIgnoreCase(symbol)
                        && existing.getPositionSide().equalsIgnoreCase(positionSide))
                .findFirst().orElseGet(FuturesPosition::new);
        if (position.getId() == null) {
            position.setUser(user);
            position.setSymbol(symbol);
            position.setPositionSide(positionSide);
            position.setStatus("OPEN");
            position.setRealizedPnl(0);
            position.setUnrealizedPnl(0);
            position.setQuantity(0);
            position.setInitialMargin(0);
            position.setMaintenanceMargin(0);
        }
        double oldQuantity = position.getQuantity();
        double combinedQuantity = oldQuantity + quantity;
        position.setEntryPrice((oldQuantity * position.getEntryPrice() + quantity * price) / combinedQuantity);
        position.setQuantity(combinedQuantity);
        position.setLeverage(leverage);
        position.setInitialMargin(position.getInitialMargin() + margin);
        position.setMaintenanceMargin(position.getMaintenanceMargin() + price * quantity * 0.005);
        position.setLiquidationPrice("LONG".equals(positionSide)
                ? position.getEntryPrice() * (1 - 1.0 / leverage)
                : position.getEntryPrice() * (1 + 1.0 / leverage));
        positionRepository.save(position);
    }

    @Transactional
    public void processPendingOrders(String symbol, double currentPrice) {
        for (FuturesOrder order : orderRepository.findBySymbolAndStatus(symbol, "PENDING")) {
            if (order.getTargetPrice() == null) continue;
            boolean triggered = ("BUY".equalsIgnoreCase(order.getSide()) && currentPrice <= order.getTargetPrice())
                    || ("SELL".equalsIgnoreCase(order.getSide()) && currentPrice >= order.getTargetPrice());
            if (!triggered) continue;
                double executionPrice = currentPrice;
                double reservedMargin = order.getTargetPrice() * order.getQuantity() / order.getLeverage();
                double executionMargin = executionPrice * order.getQuantity() / order.getLeverage();
                FuturesWallet wallet = getOrCreateWallet(order.getUser());
                double additionalMargin = executionMargin - reservedMargin;
                if (additionalMargin > 0 && wallet.getAvailableBalance() + 1e-8 < additionalMargin) continue;
                wallet.setAvailableBalance(wallet.getAvailableBalance() - additionalMargin);
                walletRepository.save(wallet);
                addToPosition(order.getUser(), order.getSymbol(), order.getPositionSide(), order.getQuantity(), executionPrice,
                    order.getLeverage(), executionMargin);
            order.setPrice(executionPrice);
            order.setStatus("EXECUTED");
            order.setTimestamp(LocalDateTime.now());
            orderRepository.save(order);
        }
    }

    @Transactional
    public void cancelOrder(Long orderId, Long userId) {
        FuturesOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Futures order not found"));
        if (order.getUser() == null || !userId.equals(order.getUser().getId())) throw new RuntimeException("Futures order not found");
        if (!"PENDING".equalsIgnoreCase(order.getStatus())) throw new IllegalArgumentException("Only pending Futures orders can be canceled.");
        double reservedMargin = order.getTargetPrice() * order.getQuantity() / order.getLeverage();
        FuturesWallet wallet = getOrCreateWallet(order.getUser());
        wallet.setAvailableBalance(wallet.getAvailableBalance() + reservedMargin);
        walletRepository.save(wallet);
        order.setStatus("CANCELLED");
        orderRepository.save(order);
    }

    public List<FuturesOrder> getOrders(Long userId) { return orderRepository.findByUserId(userId); }

    public List<FuturesPosition> getOpenPositions(Long userId) {
        return positionRepository.findByUserIdAndStatus(userId, "OPEN").stream().map(position -> {
            stockRepository.findById(position.getSymbol()).ifPresent(stock ->
                    position.setUnrealizedPnl(calculatePnl(position, stock.getPrice())));
            return position;
        }).toList();
    }

    @Transactional
    public void closePosition(Long positionId, Long userId) {
        FuturesPosition position = positionRepository.findById(positionId).orElseThrow(() -> new RuntimeException("Futures position not found"));
        if (position.getUser() == null || !userId.equals(position.getUser().getId())) throw new RuntimeException("Futures position not found");
        if (!"OPEN".equalsIgnoreCase(position.getStatus())) throw new IllegalArgumentException("Futures position is already closed.");
        double currentPrice = stockRepository.findById(position.getSymbol()).orElseThrow(() -> new RuntimeException("Stock not found")).getPrice();
        double pnl = calculatePnl(position, currentPrice);
        User user = position.getUser();
        FuturesWallet wallet = getOrCreateWallet(user);
        wallet.setAvailableBalance(wallet.getAvailableBalance() + position.getInitialMargin() + pnl);
        walletRepository.save(wallet);
        position.setRealizedPnl(pnl);
        position.setUnrealizedPnl(0);
        position.setQuantity(0);
        position.setStatus("CLOSED");
        position.setClosedAt(LocalDateTime.now());
        positionRepository.save(position);
    }

    @Transactional
    public void processLiquidations(String symbol, double currentPrice) {
        positionRepository.findBySymbolAndStatus(symbol, "OPEN").stream()
                .filter(position -> position.getLiquidationPrice() != null)
                .filter(position -> ("LONG".equalsIgnoreCase(position.getPositionSide()) && currentPrice <= position.getLiquidationPrice())
                        || ("SHORT".equalsIgnoreCase(position.getPositionSide()) && currentPrice >= position.getLiquidationPrice()))
                .forEach(position -> {
                    position.setRealizedPnl(-position.getInitialMargin());
                    position.setUnrealizedPnl(0);
                    position.setQuantity(0);
                    position.setStatus("LIQUIDATED");
                    position.setClosedAt(LocalDateTime.now());
                    positionRepository.save(position);
                });
    }

    @Transactional
    public FuturesWallet getWallet(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        return getOrCreateWallet(user);
    }

    @Transactional
    public FuturesWallet transfer(Long userId, String direction, double amount) {
        if (!Double.isFinite(amount) || amount <= 0) throw new IllegalArgumentException("Transfer amount must be greater than zero.");
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        FuturesWallet wallet = getOrCreateWallet(user);
        String normalizedDirection = normalize(direction);
        if ("SPOT_TO_FUTURES".equals(normalizedDirection)) {
            if (user.getBalance() + 1e-8 < amount) throw new IllegalArgumentException("Insufficient Spot balance.");
            user.setBalance(user.getBalance() - amount);
            wallet.setAvailableBalance(wallet.getAvailableBalance() + amount);
            userRepository.save(user);
        } else if ("FUTURES_TO_SPOT".equals(normalizedDirection)) {
            if (wallet.getAvailableBalance() + 1e-8 < amount) throw new IllegalArgumentException("Insufficient Futures wallet balance.");
            wallet.setAvailableBalance(wallet.getAvailableBalance() - amount);
            user.setBalance(user.getBalance() + amount);
            userRepository.save(user);
        } else {
            throw new IllegalArgumentException("Direction must be SPOT_TO_FUTURES or FUTURES_TO_SPOT.");
        }
        return walletRepository.save(wallet);
    }

    @Transactional
    public FuturesWallet changeLeverage(Long userId, String symbol, int leverage) {
        if (leverage < 1 || leverage > 20) {
            throw new IllegalArgumentException("Leverage must be between 1x and 20x.");
        }
        FuturesWallet wallet = getWallet(userId);
        applySymbolLeverage(userId, symbol, leverage, wallet);
        return walletRepository.save(wallet);
    }

    private void applySymbolLeverage(Long userId, String symbol, int leverage, FuturesWallet wallet) {
        List<FuturesPosition> positions = positionRepository.findByUserIdAndStatus(userId, "OPEN").stream()
                .filter(position -> symbol.equalsIgnoreCase(position.getSymbol()))
                .toList();
        double requiredMargin = positions.stream()
                .mapToDouble(position -> position.getQuantity() * position.getEntryPrice() / leverage)
                .sum();
        double currentMargin = positions.stream().mapToDouble(FuturesPosition::getInitialMargin).sum();
        double marginDelta = requiredMargin - currentMargin;
        if (marginDelta > 0 && wallet.getAvailableBalance() + 1e-8 < marginDelta) {
            throw new IllegalArgumentException("Insufficient Futures wallet balance to change leverage.");
        }
        wallet.setAvailableBalance(wallet.getAvailableBalance() - marginDelta);
        positions.forEach(position -> {
            position.setLeverage(leverage);
            position.setInitialMargin(position.getQuantity() * position.getEntryPrice() / leverage);
            position.setLiquidationPrice("LONG".equalsIgnoreCase(position.getPositionSide())
                    ? position.getEntryPrice() * (1 - 1.0 / leverage)
                    : position.getEntryPrice() * (1 + 1.0 / leverage));
            positionRepository.save(position);
        });
    }

    private FuturesWallet getOrCreateWallet(User user) {
        return walletRepository.findByUserId(user.getId()).orElseGet(() -> walletRepository.save(new FuturesWallet(user)));
    }

    private double calculatePnl(FuturesPosition position, double currentPrice) {
        double direction = "LONG".equalsIgnoreCase(position.getPositionSide()) ? 1 : -1;
        return (currentPrice - position.getEntryPrice()) * position.getQuantity() * direction;
    }

    private String normalize(String value) { return value == null ? "" : value.trim().toUpperCase(); }
}
