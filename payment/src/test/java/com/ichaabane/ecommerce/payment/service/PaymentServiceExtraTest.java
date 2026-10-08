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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService complementary Unit Tests")
class PaymentServiceExtraTest {

    @Mock
    private PaymentRepository repository;
    @Mock
    private PaymentMapper mapper;
    @Mock
    private NotificationProducer notificationProducer;

    private PaymentService service;

    @BeforeEach
    void setUp() {
        service = new PaymentService(repository, mapper, notificationProducer);
    }

    private PaymentRequest request() {
        return new PaymentRequest(BigDecimal.valueOf(100), PaymentMethod.VISA, 7, "REF-1",
                new Customer("c1", "John", "Doe", "john@doe.com"));
    }

    @Test
    @DisplayName("Should map the full customer name and email into the notification")
    void shouldMapCustomerIntoNotification() {
        var payment = Payment.builder().id(1).build();
        when(mapper.toPayment(any())).thenReturn(payment);
        when(repository.save(payment)).thenReturn(payment);

        service.createPayment(request());

        var captor = ArgumentCaptor.forClass(PaymentNotificationRequest.class);
        verify(notificationProducer).sendNotification(captor.capture());
        assertEquals("John", captor.getValue().customerFirstname());
        assertEquals("Doe", captor.getValue().customerLastname());
        assertEquals("john@doe.com", captor.getValue().customerEmail());
    }

    @Test
    @DisplayName("Should propagate a notification failure (the payment is already saved)")
    void shouldPropagateNotificationFailure() {
        var payment = Payment.builder().id(1).build();
        when(mapper.toPayment(any())).thenReturn(payment);
        when(repository.save(payment)).thenReturn(payment);
        doThrow(new RuntimeException("broker down"))
                .when(notificationProducer).sendNotification(any(PaymentNotificationRequest.class));

        var exception = assertThrows(RuntimeException.class, () -> service.createPayment(request()));

        assertEquals("broker down", exception.getMessage());
        verify(repository).save(payment);
    }
}
