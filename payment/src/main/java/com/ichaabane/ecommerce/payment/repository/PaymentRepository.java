package com.ichaabane.ecommerce.payment.repository;

import com.ichaabane.ecommerce.payment.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Integer> {

}
