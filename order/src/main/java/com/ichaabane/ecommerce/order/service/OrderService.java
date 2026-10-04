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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
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
        var customer = this.customerClient.findCustomerById(request.customerId())
                .orElseThrow(() -> new BusinessException("Cannot create order:: No customer exists with the provided ID"));

        // Fail BEFORE the stock is decremented in the product service.
        if (repository.existsByReference(request.reference())) {
            throw new BusinessException("Cannot create order:: an order with this reference already exists");
        }

        var purchasedProducts = productClient.purchaseProducts(request.products());

        // The amount is computed server-side from the product prices: never trust the client.
        var totalAmount = computeTotalAmount(purchasedProducts);

        // saveAndFlush: constraint violations surface now, before the payment is requested.
        var order = this.repository.saveAndFlush(mapper.toOrder(request, totalAmount));

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

        return order.getId();
    }

    public List<OrderResponse> findAllOrders() {
        return this.repository.findAll()
                .stream()
                .map(this.mapper::fromOrder)
                .collect(Collectors.toList());
    }

    public OrderResponse findById(Integer id) {
        return this.repository.findById(id)
                .map(this.mapper::fromOrder)
                .orElseThrow(() -> new EntityNotFoundException(String.format("No order found with the provided ID: %d", id)));
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
