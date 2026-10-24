package com.ichaabane.ecommerce.orderline.service;

import com.ichaabane.ecommerce.orderline.dto.OrderLineRequest;
import com.ichaabane.ecommerce.orderline.dto.OrderLineResponse;
import com.ichaabane.ecommerce.orderline.mapper.OrderLineMapper;
import com.ichaabane.ecommerce.orderline.repository.OrderLineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderLineService {

    private final OrderLineRepository repository;
    private final OrderLineMapper mapper;

    public Integer saveOrderLine(OrderLineRequest request) {
        log.debug("Saving order line for order id={}, product id={}, quantity={}", request.orderId(), request.productId(), request.quantity());
        var order = mapper.toOrderLine(request);
        return repository.save(order).getId();
    }

    public List<OrderLineResponse> findAllByOrderId(Integer orderId) {
        log.debug("Finding all order lines for order id={}", orderId);
        return repository.findAllByOrderId(orderId)
                .stream()
                .map(mapper::toOrderLineResponse)
                .collect(Collectors.toList());
    }
}

