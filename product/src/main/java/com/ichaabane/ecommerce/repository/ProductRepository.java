package com.ichaabane.ecommerce.repository;

import com.ichaabane.ecommerce.model.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;

/**
 * Product data access. Pagination and specification-based filtering come from
 * the inherited {@code findAll(Pageable)}/{@code findAll(Specification, Pageable)};
 * sorting fields are whitelisted by the service so a raw Pageable is safe.
 */
public interface ProductRepository extends JpaRepository<Product, Integer>, JpaSpecificationExecutor<Product> {

    /**
     * Locks the rows (SELECT ... FOR UPDATE) so two concurrent purchases cannot both
     * read the same stock and oversell. Rows are always locked in id order, which
     * keeps concurrent purchases from deadlocking each other. Call inside a transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Product> findAllByIdInOrderById(List<Integer> ids);
}
