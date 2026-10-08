package com.ichaabane.ecommerce.email;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.math.BigDecimal;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailService complementary Unit Tests")
class EmailServiceExtraTest {

    @Mock
    private JavaMailSender mailSender;
    @Mock
    private SpringTemplateEngine templateEngine;

    private EmailService service;
    private MimeMessage message;

    @BeforeEach
    void setUp() {
        service = new EmailService(mailSender, templateEngine);
        ReflectionTestUtils.setField(service, "fromAddress", "no-reply@ecommerce.local");
        message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);
    }

    @Test
    @DisplayName("Should set the configured sender, the template subject and the recipient")
    void shouldSetHeadersAndRecipient() throws Exception {
        when(templateEngine.process(eq("order-confirmation.html"), any(Context.class)))
                .thenReturn("<html>ok</html>");

        service.sendOrderConfirmationEmail("john@doe.com", "John Doe", BigDecimal.TEN, "REF-1", List.of());

        assertEquals("no-reply@ecommerce.local", message.getFrom()[0].toString());
        assertEquals("Order confirmation", message.getSubject());
        assertEquals("john@doe.com", message.getAllRecipients()[0].toString());
        verify(mailSender).send(message);
    }

    @Test
    @DisplayName("Should use the payment template when sending a payment email")
    void shouldUsePaymentTemplate() throws Exception {
        when(templateEngine.process(eq("payment-confirmation.html"), any(Context.class)))
                .thenReturn("<html>ok</html>");

        service.sendPaymentSuccessEmail("john@doe.com", "John Doe", BigDecimal.ONE, "REF-2");

        verify(templateEngine).process(eq("payment-confirmation.html"), any(Context.class));
        assertEquals("Payment successfully processed", message.getSubject());
    }

    @Test
    @DisplayName("Should propagate an unchecked MailException from the mail sender")
    void shouldPropagateMailException() throws Exception {
        when(templateEngine.process(eq("payment-confirmation.html"), any(Context.class)))
                .thenReturn("<html>ok</html>");
        // JavaMailSender.send throws an unchecked MailException, which is not a
        // MessagingException and therefore is not caught by sendEmail().
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(MimeMessage.class));

        assertThrows(MailSendException.class, () ->
                service.sendPaymentSuccessEmail("john@doe.com", "John Doe", BigDecimal.ONE, "REF-2"));
    }
}
