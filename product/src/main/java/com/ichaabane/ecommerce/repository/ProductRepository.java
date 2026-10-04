package com.ichaabane.ecommerce.repository;

import com.ichaabane.ecommerce.model.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Integer> {

    /**
     * Locks the rows (SELECT ... FOR UPDATE) so two concurrent purchases cannot both
     * read the same stock and oversell. Rows are always locked in id order, which
     * keeps concurrent purchases from deadlocking each other. Call inside a transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Product> findAllByIdInOrderById(List<Integer> ids);
}
