package com.example.demo;

import com.example.demo.dto.spot.SpotOrderRequest;
import com.example.demo.entity.Stock;
import com.example.demo.entity.User;
import com.example.demo.entity.spot.SpotOrder;
import com.example.demo.entity.spot.SpotPosition;
import com.example.demo.repository.StockRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.spot.SpotOrderRepository;
import com.example.demo.repository.spot.SpotOcoOrderRepository;
import com.example.demo.repository.spot.SpotPositionRepository;
import com.example.demo.service.SpotOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpotOrderServiceTest {
    private static final Long USER_ID = 7L;
    private static final String SYMBOL = "BTCUSDC";

    private final SpotOrderRepository orderRepository = mock(SpotOrderRepository.class);
    private final SpotOcoOrderRepository ocoOrderRepository = mock(SpotOcoOrderRepository.class);
    private final StockRepository stockRepository = mock(StockRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final SpotPositionRepository positionRepository = mock(SpotPositionRepository.class);
    private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
    private final SpotOrderService service = new SpotOrderService(orderRepository, ocoOrderRepository, stockRepository, userRepository, positionRepository, messagingTemplate);
    private User user;

    @BeforeEach
    void setUp() {
        user = new User("spot-user", "password", 1_000.0);
        user.setId(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(stockRepository.findById(SYMBOL)).thenReturn(Optional.of(new Stock(SYMBOL, "Bitcoin", 10.0)));
        when(positionRepository.findByUserIdAndStatus(USER_ID, "OPEN")).thenReturn(List.of());
        when(orderRepository.findByUserIdAndStatus(USER_ID, "PENDING")).thenReturn(List.of());
        when(ocoOrderRepository.findByUserIdAndStatus(USER_ID, "PENDING")).thenReturn(List.of());
        when(orderRepository.save(any(SpotOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void rejectsSellingMoreThanOwnedWithoutChangingBalance() {
        SpotPosition holding = holding(1.0);
        when(positionRepository.findByUserIdAndStatus(USER_ID, "OPEN")).thenReturn(List.of(holding));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.createOrder(request("SELL", "MARKET", 2.0, null), USER_ID));

        assertEquals("Insufficient spot quantity to sell.", error.getMessage());
        assertEquals(1_000.0, user.getBalance());
        verify(userRepository, never()).save(any(User.class));
        verify(orderRepository, never()).save(any(SpotOrder.class));
    }

    @Test
    void limitBuyStaysPendingUntilPriceIsReachedThenCreatesHolding() {
        SpotOrder order = service.createOrder(request("BUY", "LIMIT", 2.0, 9.0), USER_ID);

        assertEquals("PENDING", order.getStatus());
        assertEquals(1_000.0, user.getBalance());
        verify(positionRepository, never()).save(any(SpotPosition.class));

        service.executePendingOrder(order, 9.0);

        assertEquals("EXECUTED", order.getStatus());
        assertEquals(9.0, order.getPrice());
        assertEquals(982.0, user.getBalance());
        verify(positionRepository).save(any(SpotPosition.class));
    }

    @Test
    void marketableLimitBuyExecutesAtCurrentPrice() {
        user.setBalance(20.0);
        SpotOrder order = service.createOrder(request("BUY", "LIMIT", 2.0, 11.0), USER_ID);

        assertEquals("EXECUTED", order.getStatus());
        assertEquals(10.0, order.getPrice());
        assertEquals(0.0, user.getBalance());
    }

    @Test
    void marketSellReducesHoldingBeforeCreditingBalance() {
        SpotPosition holding = holding(1.0);
        when(positionRepository.findByUserIdAndStatus(USER_ID, "OPEN")).thenReturn(List.of(holding));

        SpotOrder order = service.createOrder(request("SELL", "MARKET", 1.0, null), USER_ID);

        assertEquals("EXECUTED", order.getStatus());
        assertEquals(1_010.0, user.getBalance());
        assertEquals("CLOSED", holding.getStatus());
        assertEquals(0.0, holding.getQuantity());
        verify(positionRepository).save(holding);
    }

    @Test
    void pendingBuyReservesBalanceFromOtherOrders() {
        user.setBalance(100.0);
        when(orderRepository.findByUserIdAndStatus(USER_ID, "PENDING")).thenReturn(List.of(pendingOrder("BUY", 4.0, 20.0)));

        assertThrows(IllegalArgumentException.class,
                () -> service.createOrder(request("BUY", "MARKET", 3.0, null), USER_ID));

        assertEquals(100.0, user.getBalance());
        verify(orderRepository, never()).save(any(SpotOrder.class));
    }

    @Test
    void pendingSellReservesOwnedQuantityFromOtherOrders() {
        SpotPosition holding = holding(2.0);
        when(positionRepository.findByUserIdAndStatus(USER_ID, "OPEN")).thenReturn(List.of(holding));
        when(orderRepository.findByUserIdAndStatus(USER_ID, "PENDING")).thenReturn(List.of(pendingOrder("SELL", 1.0, 12.0)));

        assertThrows(IllegalArgumentException.class,
                () -> service.createOrder(request("SELL", "MARKET", 1.5, null), USER_ID));

        assertEquals(2.0, holding.getQuantity());
        verify(orderRepository, never()).save(any(SpotOrder.class));
    }

    private SpotPosition holding(double quantity) {
        SpotPosition position = new SpotPosition();
        position.setUser(user);
        position.setSymbol(SYMBOL);
        position.setQuantity(quantity);
        position.setEntryPrice(8.0);
        position.setStatus("OPEN");
        return position;
    }

    private SpotOrderRequest request(String side, String orderType, double quantity, Double targetPrice) {
        SpotOrderRequest request = new SpotOrderRequest();
        request.setSymbol(SYMBOL);
        request.setSide(side);
        request.setOrderType(orderType);
        request.setQuantity(quantity);
        request.setTargetPrice(targetPrice);
        return request;
    }

    private SpotOrder pendingOrder(String side, double quantity, double targetPrice) {
        SpotOrder order = new SpotOrder();
        order.setUser(user);
        order.setSymbol(SYMBOL);
        order.setSide(side);
        order.setOrderType("LIMIT");
        order.setQuantity(quantity);
        order.setTargetPrice(targetPrice);
        order.setStatus("PENDING");
        return order;
    }
}
