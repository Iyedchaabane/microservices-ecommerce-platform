package com.ichaabane.ecommerce.repository;

import com.ichaabane.ecommerce.model.Customer;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface CustomerRepository extends MongoRepository<Customer, String > {

  Optional<Customer> findByEmailIgnoreCase(String email);

}
