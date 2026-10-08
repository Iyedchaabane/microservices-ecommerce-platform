package com.ichaabane.ecommerce.payment.service;

import com.ichaabane.ecommerce.notification.NotificationProducer;
import com.ichaabane.ecommerce.notification.PaymentNotificationRequest;
import com.ichaabane.ecommerce.payment.dto.Customer;
import com.ichaabane.ecommerce.payment.dto.PaymentRequest;
import com.ichaabane.ecommerce.payment.mapper.PaymentMapper;
import com.ichaabane.ecommerce.payment.model.Payment;
import com.ichaabane.ecommerce.payment.model.PaymentMethod;
import com.ichaabane.ecommerce.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService Unit Tests")
class PaymentServiceTest {

    @Mock
    private PaymentRepository repository;
    @Mock
    private PaymentMapper mapper;
    @Mock
    private NotificationProducer notificationProducer;

    @InjectMocks
    private PaymentService paymentService;

    private PaymentRequest request;
    private Payment payment;

    @BeforeEach
    void setUp() {
        Customer customer = new Customer("1", "Test", "Test", "test@example.com");

        request = new PaymentRequest(
                BigDecimal.valueOf(2000),
                PaymentMethod.CREDIT_CARD,
                1,
                "REF123",
                customer
        );

        payment = new Payment();
        payment.setId(1);
        payment.setAmount(request.amount());
        payment.setOrderId(request.orderId());
    }

    @Nested
    @DisplayName("createPayment() method")
    class CreatePaymentTests {

        @Test
        @DisplayName("Should create payment and send notification built from the request")
        void shouldCreatePaymentSuccessfully() {
            when(mapper.toPayment(request)).thenReturn(payment);
            when(repository.save(payment)).thenReturn(payment);

            Integer paymentId = paymentService.createPayment(request);

            assertNotNull(paymentId);
            assertEquals(1, paymentId);

            verify(mapper, times(1)).toPayment(request);
            verify(repository, times(1)).save(payment);

            ArgumentCaptor<PaymentNotificationRequest> captor =
                    ArgumentCaptor.forClass(PaymentNotificationRequest.class);
            verify(notificationProducer, times(1)).sendNotification(captor.capture());
            assertEquals("REF123", captor.getValue().orderReference());
            assertEquals(BigDecimal.valueOf(2000), captor.getValue().amount());
            assertEquals(PaymentMethod.CREDIT_CARD, captor.getValue().paymentMethod());
            assertEquals("test@example.com", captor.getValue().customerEmail());
        }

        @Test
        @DisplayName("Should not send notification when the repository fails")
        void shouldNotNotifyWhenRepositoryFails() {
            when(mapper.toPayment(request)).thenReturn(payment);
            when(repository.save(payment)).thenThrow(new RuntimeException("DB unavailable"));

            RuntimeException exception = assertThrows(RuntimeException.class,
                    () -> paymentService.createPayment(request));

            assertEquals("DB unavailable", exception.getMessage());
            verify(mapper, times(1)).toPayment(request);
            verify(repository, times(1)).save(payment);
            verify(notificationProducer, never()).sendNotification(any(PaymentNotificationRequest.class));
        }
    }
}