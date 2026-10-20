package com.ichaabane.ecommerce.payment.repository;

import com.ichaabane.ecommerce.payment.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Integer> {

    /**
     * Idempotency check used by {@code PaymentService.createPayment} so a
     * retried checkout returns the existing payment instead of recording a
     * second one for the same order.
     */
    Optional<Payment> findByOrderReference(String orderReference);
}
