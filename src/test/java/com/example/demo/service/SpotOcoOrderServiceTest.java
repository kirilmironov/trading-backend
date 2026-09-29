package com.example.demo.service;

import com.example.demo.dto.spot.SpotOcoOrderRequest;
import com.example.demo.entity.Stock;
import com.example.demo.entity.User;
import com.example.demo.entity.spot.SpotOcoOrder;
import com.example.demo.repository.StockRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.spot.SpotOcoOrderRepository;
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

class SpotOcoOrderServiceTest {
    private static final Long USER_ID = 23L;
    private static final String SYMBOL = "BTCUSDC";

    private final SpotOcoOrderRepository ocoRepository = mock(SpotOcoOrderRepository.class);
    private final StockRepository stockRepository = mock(StockRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final SpotOrderService spotOrderService = mock(SpotOrderService.class);
    private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
    private final SpotOcoOrderService service = new SpotOcoOrderService(
            ocoRepository, stockRepository, userRepository, spotOrderService, messagingTemplate);
    private User user;

    @BeforeEach
    void setUp() {
        user = new User("oco-user", "password", 1_000.0);
        user.setId(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(stockRepository.findById(SYMBOL)).thenReturn(Optional.of(new Stock(SYMBOL, "Bitcoin", 100.0)));
        when(spotOrderService.availableBaseQuantity(user, SYMBOL)).thenReturn(2.0);
        when(spotOrderService.availableQuoteBalance(user)).thenReturn(1_000.0);
        when(spotOrderService.availableBaseQuantityExcludingOco(user, SYMBOL, null)).thenReturn(2.0);
        when(spotOrderService.availableQuoteBalanceExcludingOco(user, null)).thenReturn(1_000.0);
        when(ocoRepository.save(any(SpotOcoOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void sellOcoStopLimitExecutesAfterStopTriggersAndPriceRecoversToLimit() {
        SpotOcoOrder order = service.create(request("SELL", 1.0, 110.0, 90.0, 89.0), USER_ID);
        when(spotOrderService.availableBaseQuantityExcludingOco(user, SYMBOL, order.getId())).thenReturn(2.0);
        when(ocoRepository.findPendingForUpdate(SYMBOL, "PENDING")).thenReturn(List.of(order));

        service.processPendingOrders(SYMBOL, 88.0);
        assertEquals("PENDING", order.getStatus());
        assertEquals(true, order.isStopTriggered());
        verify(spotOrderService, never()).executeOcoTrade(user, SYMBOL, "SELL", 1.0, 88.0);

        service.processPendingOrders(SYMBOL, 89.0);

        assertEquals("EXECUTED", order.getStatus());
        assertEquals("STOP_LOSS", order.getExecutedLeg());
        assertEquals(89.0, order.getExecutionPrice());
        verify(spotOrderService).executeOcoTrade(user, SYMBOL, "SELL", 1.0, 89.0);
    }

    @Test
    void rejectsSellOcoWithBinanceInvalidPriceOrdering() {
        assertThrows(IllegalArgumentException.class,
                () -> service.create(request("SELL", 1.0, 95.0, 90.0, 91.0), USER_ID));
    }

    @Test
    void buyOcoTakeProfitExecutesAndClosesTheOcoGroup() {
        SpotOcoOrder order = service.create(request("BUY", 1.0, 90.0, 110.0, 111.0), USER_ID);
        when(spotOrderService.availableQuoteBalanceExcludingOco(user, order.getId())).thenReturn(1_000.0);
        when(ocoRepository.findPendingForUpdate(SYMBOL, "PENDING")).thenReturn(List.of(order));

        service.processPendingOrders(SYMBOL, 89.0);

        assertEquals("EXECUTED", order.getStatus());
        assertEquals("TAKE_PROFIT", order.getExecutedLeg());
        verify(spotOrderService).executeOcoTrade(user, SYMBOL, "BUY", 1.0, 89.0);
    }

    @Test
    void buyOcoRequiresEnoughBalanceForTheLargerAlternative() {
        when(spotOrderService.availableQuoteBalance(user)).thenReturn(110.0);

        assertThrows(IllegalArgumentException.class,
                () -> service.create(request("BUY", 1.0, 90.0, 110.0, 111.0), USER_ID));
        verify(ocoRepository, never()).save(any(SpotOcoOrder.class));
    }

    @Test
    void sellTakeProfitExecutesAndClosesSiblingLeg() {
        SpotOcoOrder order = service.create(request("SELL", 1.0, 110.0, 90.0, 89.0), USER_ID);
        when(spotOrderService.availableBaseQuantityExcludingOco(user, SYMBOL, order.getId())).thenReturn(2.0);
        when(ocoRepository.findPendingForUpdate(SYMBOL, "PENDING")).thenReturn(List.of(order));

        service.processPendingOrders(SYMBOL, 111.0);

        assertEquals("EXECUTED", order.getStatus());
        assertEquals("TAKE_PROFIT", order.getExecutedLeg());
        verify(spotOrderService).executeOcoTrade(user, SYMBOL, "SELL", 1.0, 111.0);
    }

    private SpotOcoOrderRequest request(String side, double quantity, double takeProfit, double stop, double stopLimit) {
        SpotOcoOrderRequest request = new SpotOcoOrderRequest();
        request.setSymbol(SYMBOL);
        request.setSide(side);
        request.setQuantity(quantity);
        request.setTakeProfitPrice(takeProfit);
        request.setStopPrice(stop);
        request.setStopLimitPrice(stopLimit);
        return request;
    }
}
