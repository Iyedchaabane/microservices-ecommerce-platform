package com.ichaabane.ecommerce.email;

import com.ichaabane.ecommerce.kafka.order.Product;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.math.BigDecimal;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailService Unit Tests")
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;
    @Mock
    private SpringTemplateEngine templateEngine;

    private EmailService service;

    @BeforeEach
    void setUp() {
        service = new EmailService(mailSender, templateEngine);
        ReflectionTestUtils.setField(service, "fromAddress", "no-reply@ecommerce.local");
        when(mailSender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getInstance(new Properties())));
    }

    @Test
    @DisplayName("Should render the payment template with the payment variables")
    void shouldSendPaymentEmail() throws Exception {
        when(templateEngine.process(eq("payment-confirmation.html"), any(Context.class)))
                .thenReturn("<html>ok</html>");

        service.sendPaymentSuccessEmail("john@doe.com", "John Doe", BigDecimal.valueOf(99), "REF-1");

        var contextCaptor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq("payment-confirmation.html"), contextCaptor.capture());
        assertEquals("John Doe", contextCaptor.getValue().getVariable("customerName"));
        assertEquals(BigDecimal.valueOf(99), contextCaptor.getValue().getVariable("amount"));
        assertEquals("REF-1", contextCaptor.getValue().getVariable("orderReference"));

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("Should render the order template with the order variables")
    void shouldSendOrderEmail() throws Exception {
        when(templateEngine.process(eq("order-confirmation.html"), any(Context.class)))
                .thenReturn("<html>ok</html>");
        var products = List.of(new Product(1, "Book", "A book", BigDecimal.TEN, 2));

        service.sendOrderConfirmationEmail("john@doe.com", "John Doe", BigDecimal.TEN, "REF-2", products);

        var contextCaptor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq("order-confirmation.html"), contextCaptor.capture());
        assertEquals("John Doe", contextCaptor.getValue().getVariable("customerName"));
        assertEquals(BigDecimal.TEN, contextCaptor.getValue().getVariable("totalAmount"));
        assertEquals("REF-2", contextCaptor.getValue().getVariable("orderReference"));
        assertEquals(products, contextCaptor.getValue().getVariable("products"));

        verify(mailSender).send(any(MimeMessage.class));
    }
}