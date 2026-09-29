package com.example.demo;

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
import com.example.demo.service.FuturesOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FuturesOrderServiceTest {
    private static final Long USER_ID = 19L;
    private static final String SYMBOL = "BTCUSDC";

    private final FuturesOrderRepository orderRepository = mock(FuturesOrderRepository.class);
    private final FuturesPositionRepository positionRepository = mock(FuturesPositionRepository.class);
    private final StockRepository stockRepository = mock(StockRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final FuturesWalletRepository walletRepository = mock(FuturesWalletRepository.class);
    private final FuturesOrderService service = new FuturesOrderService(orderRepository, positionRepository,
            stockRepository, userRepository, walletRepository);
    private User user;
    private FuturesWallet wallet;

    @BeforeEach
    void setUp() {
        user = new User("futures-user", "password", 1_000.0);
        user.setId(USER_ID);
        wallet = new FuturesWallet(user);
        wallet.setAvailableBalance(100.0);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(stockRepository.findById(SYMBOL)).thenReturn(Optional.of(new Stock(SYMBOL, "Bitcoin", 10.0)));
        when(walletRepository.findByUserId(USER_ID)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any(FuturesWallet.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(positionRepository.findByUserIdAndStatus(USER_ID, "OPEN")).thenReturn(List.of());
        when(orderRepository.findByUserIdAndStatus(USER_ID, "PENDING")).thenReturn(List.of());
        when(orderRepository.save(any(FuturesOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(positionRepository.save(any(FuturesPosition.class))).thenAnswer(invocation -> {
            FuturesPosition position = invocation.getArgument(0);
            if (position.getId() == null) position.setId(51L);
            return position;
        });
    }

    @Test
    void pendingLimitBuyReservesMarginThenFillsAtLimitPrice() {
        FuturesOrder order = service.createOrder(request("BUY", "LONG", "LIMIT", 2.0, 5, 9.0), USER_ID);
        when(orderRepository.findBySymbolAndStatus(SYMBOL, "PENDING")).thenReturn(List.of(order));

        assertEquals("PENDING", order.getStatus());
        assertEquals(96.4, wallet.getAvailableBalance(), 1e-8);
        verify(positionRepository, never()).save(any(FuturesPosition.class));

        service.processPendingOrders(SYMBOL, 9.0);

        assertEquals("EXECUTED", order.getStatus());
        assertEquals(9.0, order.getPrice());
        assertEquals(96.4, wallet.getAvailableBalance(), 1e-8);
        verify(positionRepository).save(any(FuturesPosition.class));
    }

    @Test
    void canceledLimitOrderReleasesReservedMargin() {
        FuturesOrder order = new FuturesOrder();
        order.setId(8L);
        order.setUser(user);
        order.setSymbol(SYMBOL);
        order.setSide("BUY");
        order.setPositionSide("LONG");
        order.setOrderType("LIMIT");
        order.setTargetPrice(9.0);
        order.setQuantity(2.0);
        order.setLeverage(5);
        order.setStatus("PENDING");
        wallet.setAvailableBalance(96.4);
        when(orderRepository.findById(8L)).thenReturn(Optional.of(order));

        service.cancelOrder(8L, USER_ID);

        assertEquals("CANCELLED", order.getStatus());
        assertEquals(100.0, wallet.getAvailableBalance(), 1e-8);
    }

    @Test
    void marketableLimitExecutesAtCurrentBetterPrice() {
        FuturesOrder order = service.createOrder(request("BUY", "LONG", "LIMIT", 2.0, 5, 11.0), USER_ID);

        assertEquals("EXECUTED", order.getStatus());
        assertEquals(10.0, order.getPrice());
        assertEquals(96.0, wallet.getAvailableBalance(), 1e-8);
    }

    private FuturesOrderRequest request(String side, String positionSide, String orderType, double quantity,
                                        int leverage, Double targetPrice) {
        FuturesOrderRequest request = new FuturesOrderRequest();
        request.setSymbol(SYMBOL);
        request.setSide(side);
        request.setPositionSide(positionSide);
        request.setOrderType(orderType);
        request.setQuantity(quantity);
        request.setLeverage(leverage);
        request.setTargetPrice(targetPrice);
        return request;
    }
}
