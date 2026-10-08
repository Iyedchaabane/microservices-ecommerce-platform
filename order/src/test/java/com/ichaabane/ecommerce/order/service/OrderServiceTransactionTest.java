package com.ichaabane.ecommerce.order.service;

import com.ichaabane.ecommerce.customer.CustomerClient;
import com.ichaabane.ecommerce.customer.CustomerResponse;
import com.ichaabane.ecommerce.kafka.OrderConfirmation;
import com.ichaabane.ecommerce.kafka.OrderProducer;
import com.ichaabane.ecommerce.order.dto.OrderRequest;
import com.ichaabane.ecommerce.order.mapper.OrderMapper;
import com.ichaabane.ecommerce.order.model.Order;
import com.ichaabane.ecommerce.order.model.PaymentMethod;
import com.ichaabane.ecommerce.order.repository.OrderRepository;
import com.ichaabane.ecommerce.orderline.dto.OrderLineRequest;
import com.ichaabane.ecommerce.orderline.service.OrderLineService;
import com.ichaabane.ecommerce.payment.PaymentClient;
import com.ichaabane.ecommerce.payment.PaymentRequest;
import com.ichaabane.ecommerce.product.ProductClient;
import com.ichaabane.ecommerce.product.PurchaseRequest;
import com.ichaabane.ecommerce.product.PurchaseResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService transaction & multi-line Unit Tests")
class OrderServiceTransactionTest {

    @Mock
    private OrderRepository repository;
    @Mock
    private CustomerClient customerClient;
    @Mock
    private PaymentClient paymentClient;
    @Mock
    private ProductClient productClient;
    @Mock
    private OrderLineService orderLineService;
    @Mock
    private OrderProducer orderProducer;

    private final OrderMapper mapper = new OrderMapper();

    private OrderService service;

    private final CustomerResponse customer =
            new CustomerResponse("customer-1", "John", "Doe", "john@doe.com");

    @BeforeEach
    void setUp() {
        service = new OrderService(repository, mapper, customerClient, paymentClient,
                productClient, orderLineService, orderProducer);
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private void stubPersist() {
        when(repository.saveAndFlush(any(Order.class))).thenAnswer(inv -> {
            Order submitted = inv.getArgument(0);
            return Order.builder()
                    .id(7)
                    .reference(submitted.getReference())
                    .totalAmount(submitted.getTotalAmount())
                    .build();
        });
    }

    @Test
    @DisplayName("Should create one order line per product and compute the summed total")
    void shouldCreateOneLinePerProductAndSumTotal() {
        var request = new OrderRequest("REF-1", PaymentMethod.CREDIT_CARD, "customer-1", List.of(
                new PurchaseRequest(21, 2),
                new PurchaseRequest(22, 1)));
        var purchased = List.of(
                new PurchaseResponse(21, "Book", "A book", BigDecimal.valueOf(50), 2),
                new PurchaseResponse(22, "Pen", "A pen", BigDecimal.valueOf(10), 1));

        when(customerClient.findCustomerById("customer-1")).thenReturn(Optional.of(customer));
        when(repository.existsByReference("REF-1")).thenReturn(false);
        when(productClient.purchaseProducts(request.products())).thenReturn(purchased);
        stubPersist();

        service.createOrder(request);

        var lineCaptor = ArgumentCaptor.forClass(OrderLineRequest.class);
        verify(orderLineService, times(2)).saveOrderLine(lineCaptor.capture());
        assertEquals(21, lineCaptor.getAllValues().get(0).productId());
        assertEquals(22, lineCaptor.getAllValues().get(1).productId());

        var paymentCaptor = ArgumentCaptor.forClass(PaymentRequest.class);
        verify(paymentClient).requestOrderPayment(paymentCaptor.capture());
        // (50 * 2) + (10 * 1) = 110
        assertEquals(0, BigDecimal.valueOf(110).compareTo(paymentCaptor.getValue().amount()));
    }

    @Test
    @DisplayName("Should defer the confirmation until the transaction commits")
    void shouldPublishOnlyAfterCommit() {
        var request = new OrderRequest("REF-1", PaymentMethod.CREDIT_CARD, "customer-1",
                List.of(new PurchaseRequest(21, 2)));
        var purchased = List.of(
                new PurchaseResponse(21, "Book", "A book", BigDecimal.valueOf(50), 2));

        when(customerClient.findCustomerById("customer-1")).thenReturn(Optional.of(customer));
        when(repository.existsByReference("REF-1")).thenReturn(false);
        when(productClient.purchaseProducts(request.products())).thenReturn(purchased);
        stubPersist();

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.createOrder(request);

            // Not sent yet: it is registered to run after commit.
            verify(orderProducer, never()).sendOrderConfirmation(any(OrderConfirmation.class));
            var synchronizations = TransactionSynchronizationManager.getSynchronizations();
            assertEquals(1, synchronizations.size());

            synchronizations.get(0).afterCommit();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        var confirmationCaptor = ArgumentCaptor.forClass(OrderConfirmation.class);
        verify(orderProducer, times(1)).sendOrderConfirmation(confirmationCaptor.capture());
        assertEquals("REF-1", confirmationCaptor.getValue().orderReference());
    }
}
