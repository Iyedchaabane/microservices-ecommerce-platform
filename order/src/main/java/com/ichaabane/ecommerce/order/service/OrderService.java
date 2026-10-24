package com.ichaabane.ecommerce.order.service;

import com.ichaabane.ecommerce.kafka.OrderConfirmation;
import com.ichaabane.ecommerce.customer.CustomerClient;
import com.ichaabane.ecommerce.exception.BusinessException;
import com.ichaabane.ecommerce.kafka.OrderProducer;
import com.ichaabane.ecommerce.order.dto.OrderRequest;
import com.ichaabane.ecommerce.order.dto.OrderResponse;
import com.ichaabane.ecommerce.order.mapper.OrderMapper;
import com.ichaabane.ecommerce.order.repository.OrderRepository;
import com.ichaabane.ecommerce.orderline.dto.OrderLineRequest;
import com.ichaabane.ecommerce.orderline.service.OrderLineService;
import com.ichaabane.ecommerce.payment.PaymentClient;
import com.ichaabane.ecommerce.payment.PaymentRequest;
import com.ichaabane.ecommerce.product.ProductClient;
import com.ichaabane.ecommerce.product.PurchaseRequest;
import com.ichaabane.ecommerce.product.PurchaseResponse;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository repository;
    private final OrderMapper mapper;
    private final CustomerClient customerClient;
    private final PaymentClient paymentClient;
    private final ProductClient productClient;
    private final OrderLineService orderLineService;
    private final OrderProducer orderProducer;

    @Transactional
    public Integer createOrder(OrderRequest request) {
        log.info("Starting order creation for reference='{}', customerId='{}'", request.reference(), request.customerId());
        var customer = this.customerClient.findCustomerById(request.customerId())
                .orElseThrow(() -> {
                    log.warn("Order creation rejected: no customer found with id='{}'", request.customerId());
                    return new BusinessException("Cannot create order:: No customer exists with the provided ID");
                });

        // Fail BEFORE the stock is decremented in the product service.
        if (repository.existsByReference(request.reference())) {
            log.warn("Order creation rejected: order with reference='{}' already exists", request.reference());
            throw new BusinessException("Cannot create order:: an order with this reference already exists");
        }

        log.info("Requesting purchase of {} product item(s) for order reference='{}'", request.products().size(), request.reference());
        var purchasedProducts = productClient.purchaseProducts(request.products());

        // The amount is computed server-side from the product prices: never trust the client.
        var totalAmount = computeTotalAmount(purchasedProducts);
        log.info("Calculated order total amount: {} for reference='{}'", totalAmount, request.reference());

        // saveAndFlush: constraint violations surface now, before the payment is requested.
        var order = this.repository.saveAndFlush(mapper.toOrder(request, totalAmount));
        log.info("Order entity persisted with id={}, reference='{}'", order.getId(), order.getReference());

        for (PurchaseRequest purchaseRequest : request.products()) {
            orderLineService.saveOrderLine(
                    new OrderLineRequest(
                            order.getId(),
                            purchaseRequest.productId(),
                            purchaseRequest.quantity()
                    )
            );
        }
        var paymentRequest = new PaymentRequest(
                totalAmount,
                request.paymentMethod(),
                order.getId(),
                order.getReference(),
                customer
        );
        log.info("Requesting payment for order id={}, reference='{}'", order.getId(), order.getReference());
        paymentClient.requestOrderPayment(paymentRequest);

        publishAfterCommit(
                new OrderConfirmation(
                        request.reference(),
                        totalAmount,
                        request.paymentMethod(),
                        customer,
                        purchasedProducts
                )
        );

        log.info("Order creation flow completed successfully for order id={}, reference='{}'", order.getId(), order.getReference());
        return order.getId();
    }

    public List<OrderResponse> findAllOrders() {
        return this.repository.findAll()
                .stream()
                .map(this.mapper::fromOrder)
                .collect(Collectors.toList());
    }

    public OrderResponse findById(Integer id) {
        log.debug("Fetching order id={}", id);
        return this.repository.findById(id)
                .map(this.mapper::fromOrder)
                .orElseThrow(() -> {
                    log.warn("Order id={} not found", id);
                    return new EntityNotFoundException(String.format("No order found with the provided ID: %d", id));
                });
    }

    private BigDecimal computeTotalAmount(List<PurchaseResponse> purchasedProducts) {
        return purchasedProducts.stream()
                .map(p -> p.price().multiply(BigDecimal.valueOf(p.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Sends the confirmation only once the DB transaction has committed, so a rollback
     * can never leave the customer with a confirmation for an order that does not exist.
     */
    private void publishAfterCommit(OrderConfirmation confirmation) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    orderProducer.sendOrderConfirmation(confirmation);
                }
            });
        } else {
            orderProducer.sendOrderConfirmation(confirmation);
        }
    }
}
