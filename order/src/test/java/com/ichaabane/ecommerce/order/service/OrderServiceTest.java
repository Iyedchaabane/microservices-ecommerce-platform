package com.ichaabane.ecommerce.order.service;

import com.ichaabane.ecommerce.customer.CustomerClient;
import com.ichaabane.ecommerce.customer.CustomerResponse;
import com.ichaabane.ecommerce.exception.BusinessException;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService Unit Tests")
class OrderServiceTest {

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
    private final List<PurchaseRequest> purchases = List.of(new PurchaseRequest(21, 2));
    private final List<PurchaseResponse> purchasedProducts = List.of(
            new PurchaseResponse(21, "Book", "A book", BigDecimal.valueOf(50), 2));

    private OrderRequest request;

    @BeforeEach
    void setUp() {
        service = new OrderService(repository, mapper, customerClient, paymentClient,
                productClient, orderLineService, orderProducer);
        request = new OrderRequest("REF-1", PaymentMethod.CREDIT_CARD,
                "customer-1", purchases);
    }

    /** Simulates the DB: returns a persisted copy (id 7) and leaves the captured argument untouched. */
    private void stubSaveAndFlush() {
        when(repository.saveAndFlush(any(Order.class))).thenAnswer(inv -> {
            Order submitted = inv.getArgument(0);
            return Order.builder()
                    .id(7)
                    .reference(submitted.getReference())
                    .totalAmount(submitted.getTotalAmount())
                    .build();
        });
    }

    private static void assertSameAmount(String message, BigDecimal expected, BigDecimal actual) {
        // compareTo: 100 and 100.0 are the same amount but not BigDecimal.equals()
        assertEquals(0, expected.compareTo(actual), message);
    }

    @Nested
    @DisplayName("createOrder() happy path")
    class HappyPath {

        @Test
        @DisplayName("Should orchestrate persistence, stock, payment and notification")
        void shouldCreateOrder() {
            when(customerClient.findCustomerById("customer-1")).thenReturn(Optional.of(customer));
            when(repository.existsByReference("REF-1")).thenReturn(false);
            when(productClient.purchaseProducts(purchases)).thenReturn(purchasedProducts);
            stubSaveAndFlush();

            Integer id = service.createOrder(request);

            assertEquals(7, id);

            var orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(repository).saveAndFlush(orderCaptor.capture());
            assertNull(orderCaptor.getValue().getId());
            assertEquals("REF-1", orderCaptor.getValue().getReference());
            assertSameAmount("persisted total", BigDecimal.valueOf(100), orderCaptor.getValue().getTotalAmount());

            var lineCaptor = ArgumentCaptor.forClass(OrderLineRequest.class);
            verify(orderLineService).saveOrderLine(lineCaptor.capture());
            assertEquals(7, lineCaptor.getValue().orderId());
            assertEquals(21, lineCaptor.getValue().productId());
            assertEquals(2.0, lineCaptor.getValue().quantity());

            var paymentCaptor = ArgumentCaptor.forClass(PaymentRequest.class);
            verify(paymentClient).requestOrderPayment(paymentCaptor.capture());
            assertEquals(7, paymentCaptor.getValue().orderId());
            assertEquals("REF-1", paymentCaptor.getValue().orderReference());
            assertSameAmount("paid total", BigDecimal.valueOf(100), paymentCaptor.getValue().amount());
            assertSame(customer, paymentCaptor.getValue().customer());

            // No transaction is active in a plain unit test, so the confirmation is sent immediately.
            var confirmationCaptor = ArgumentCaptor.forClass(OrderConfirmation.class);
            verify(orderProducer).sendOrderConfirmation(confirmationCaptor.capture());
            assertEquals("REF-1", confirmationCaptor.getValue().orderReference());
            assertEquals(purchasedProducts, confirmationCaptor.getValue().products());
        }
    }

    @Nested
    @DisplayName("createOrder() failure paths")
    class FailurePaths {

        @Test
        @DisplayName("Should stop when the customer does not exist")
        void shouldStopWhenCustomerMissing() {
            when(customerClient.findCustomerById("customer-1")).thenReturn(Optional.empty());

            assertThrows(BusinessException.class, () -> service.createOrder(request));

            verify(repository, never()).saveAndFlush(any(Order.class));
            verifyNoInteractions(productClient, paymentClient, orderProducer);
        }

        @Test
        @DisplayName("Should refuse a duplicated order reference before touching the stock")
        void shouldRejectDuplicateReference() {
            when(customerClient.findCustomerById("customer-1")).thenReturn(Optional.of(customer));
            when(repository.existsByReference("REF-1")).thenReturn(true);

            assertThrows(BusinessException.class, () -> service.createOrder(request));

            verify(repository, never()).saveAndFlush(any(Order.class));
            verifyNoInteractions(productClient, paymentClient, orderProducer);
        }

        @Test
        @DisplayName("Should abort the order when the stock cannot be reserved")
        void shouldAbortWhenPurchaseFails() {
            when(customerClient.findCustomerById("customer-1")).thenReturn(Optional.of(customer));
            when(repository.existsByReference("REF-1")).thenReturn(false);
            when(productClient.purchaseProducts(purchases))
                    .thenThrow(new BusinessException("Cannot purchase products"));

            assertThrows(BusinessException.class, () -> service.createOrder(request));

            verify(repository, never()).saveAndFlush(any(Order.class));
            verifyNoInteractions(paymentClient, orderProducer);
        }

        @Test
        @DisplayName("Should not send a confirmation when the payment fails")
        void shouldNotConfirmWhenPaymentFails() {
            when(customerClient.findCustomerById("customer-1")).thenReturn(Optional.of(customer));
            when(repository.existsByReference("REF-1")).thenReturn(false);
            when(productClient.purchaseProducts(purchases)).thenReturn(purchasedProducts);
            stubSaveAndFlush();
            doThrow(new RuntimeException("payment down"))
                    .when(paymentClient).requestOrderPayment(any(PaymentRequest.class));

            assertThrows(RuntimeException.class, () -> service.createOrder(request));

            verifyNoInteractions(orderProducer);
        }
    }
}