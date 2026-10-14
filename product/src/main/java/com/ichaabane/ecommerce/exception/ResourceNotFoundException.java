package com.ichaabane.ecommerce.exception;

import jakarta.persistence.EntityNotFoundException;

/**
 * A missing product/category is a 404 (resource not present), not a 400.
 * Carries the missing identifier so the handler can render a stable message.
 * Extends {@link EntityNotFoundException} because that is the exception the
 * service layer already throws for lookups that miss.
 */
public class ResourceNotFoundException extends EntityNotFoundException {

    public ResourceNotFoundException(String resourceName, Object id) {
        super(resourceName + " not found with ID:: " + id);
    }
}
