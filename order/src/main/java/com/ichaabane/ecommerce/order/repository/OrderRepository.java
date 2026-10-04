package com.ichaabane.ecommerce.order.repository;

import com.ichaabane.ecommerce.order.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Integer> {

    boolean existsByReference(String reference);
}
