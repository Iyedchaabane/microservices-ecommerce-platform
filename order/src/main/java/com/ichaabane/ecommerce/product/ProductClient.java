package com.ichaabane.ecommerce.product;

import com.ichaabane.ecommerce.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Service
@RequiredArgsConstructor
public class ProductClient {

    @Value("${application.config.product-url}")
    private String productUrl;
    private final RestTemplate restTemplate;

    public List<PurchaseResponse> purchaseProducts(List<PurchaseRequest> requestBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(CONTENT_TYPE, APPLICATION_JSON_VALUE);

        HttpEntity<List<PurchaseRequest>> requestEntity = new HttpEntity<>(requestBody, headers);
        ParameterizedTypeReference<List<PurchaseResponse>> responseType = new ParameterizedTypeReference<>() {};
        // RestTemplate's default error handler throws on 4xx/5xx, so errors must be
        // handled in a catch block (checking the status code afterwards is dead code).
        ResponseEntity<List<PurchaseResponse>> responseEntity;
        try {
            responseEntity = restTemplate.exchange(
                    productUrl + "/purchase",
                    POST,
                    requestEntity,
                    responseType
            );
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().is4xxClientError()) {
                // The product service explains why the purchase was refused (unknown product, no stock...)
                throw new BusinessException("Cannot purchase products:: " + e.getResponseBodyAsString());
            }
            throw new BusinessException("Product service failed to process the purchase, please try again later");
        } catch (ResourceAccessException e) {
            throw new BusinessException("Product service is unreachable, please try again later");
        }

        var purchased = responseEntity.getBody();
        if (purchased == null) {
            throw new BusinessException("Product service returned an empty purchase response");
        }
        return  responseEntity.getBody();
    }

}
