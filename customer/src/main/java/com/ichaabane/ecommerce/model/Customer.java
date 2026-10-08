package com.ichaabane.ecommerce.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@Document
public class Customer {
  @Id
  private String id;
  private String firstname;
  private String lastname;
  // Database-level guarantee: MongoDB rejects a second document with the same
  // email even if a concurrent request slips past the service check.
  @Indexed(unique = true)
  private String email;
  private Address address;
}
