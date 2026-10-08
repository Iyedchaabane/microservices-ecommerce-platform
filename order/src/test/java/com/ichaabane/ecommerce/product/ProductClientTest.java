package com.ichaabane.ecommerce.product;

import com.ichaabane.ecommerce.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductClient Unit Tests")
class ProductClientTest {

    @Mock
    private RestTemplate restTemplate;

    private ProductClient client;

    private final List<PurchaseRequest> requestBody = List.of(new PurchaseRequest(21, 2));

    @BeforeEach
    void setUp() {
        client = new ProductClient(restTemplate);
        ReflectionTestUtils.setField(client, "productUrl", "http://localhost:8222/api/v1/products");
    }

    @SuppressWarnings("unchecked")
    private void stubExchangeThrowing(RuntimeException exception) {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
                org.mockito.ArgumentMatchers.<ParameterizedTypeReference<List<PurchaseResponse>>>any()))
                .thenThrow(exception);
    }

    private void stubExchangeReturning(ResponseEntity<List<PurchaseResponse>> response) {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
                org.mockito.ArgumentMatchers.<ParameterizedTypeReference<List<PurchaseResponse>>>any()))
                .thenReturn(response);
    }

    private static HttpClientErrorException clientError(HttpStatus status, String body) {
        return HttpClientErrorException.create(status, status.getReasonPhrase(), null,
                body.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }

    private static HttpServerErrorException serverError(HttpStatus status, String body) {
        return HttpServerErrorException.create(status, status.getReasonPhrase(), null,
                body.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }

    @Nested
    @DisplayName("successful purchases")
    class Success {

        @Test
        @DisplayName("Should return the purchased products from the product service")
        void shouldReturnPurchasedProducts() {
            var expected = List.of(new PurchaseResponse(21, "Book", "A book", BigDecimal.TEN, 2));
            stubExchangeReturning(ResponseEntity.ok(expected));

            List<PurchaseResponse> result = client.purchaseProducts(requestBody);

            assertSame(expected, result);
        }

        @Test
        @DisplayName("Should post to the purchase endpoint with the request body")
        void shouldPostToPurchaseEndpoint() {
            stubExchangeReturning(ResponseEntity.ok(List.of()));

            client.purchaseProducts(requestBody);

            var urlCaptor = ArgumentCaptor.forClass(String.class);
            var entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
            verify(restTemplate).exchange(urlCaptor.capture(), eq(HttpMethod.POST), entityCaptor.capture(),
                    org.mockito.ArgumentMatchers.<ParameterizedTypeReference<List<PurchaseResponse>>>any());

            assertEquals("http://localhost:8222/api/v1/products/purchase", urlCaptor.getValue());
            assertSame(requestBody, entityCaptor.getValue().getBody());
        }

        @Test
        @DisplayName("Should return an empty list when the product service returns none")
        void shouldReturnEmptyList() {
            stubExchangeReturning(ResponseEntity.ok(List.of()));

            assertTrue(client.purchaseProducts(requestBody).isEmpty());
        }
    }

    @Nested
    @DisplayName("error handling")
    class Errors {

        @Test
        @DisplayName("Should surface the upstream reason for a 4xx response")
        void shouldSurfaceClientErrorReason() {
            stubExchangeThrowing(clientError(HttpStatus.BAD_REQUEST, "Insufficient stock"));

            var exception = assertThrows(BusinessException.class, () -> client.purchaseProducts(requestBody));

            assertEquals("Cannot purchase products:: Insufficient stock", exception.getMsg());
        }

        @Test
        @DisplayName("Should surface the upstream reason for a 404 response")
        void shouldSurfaceNotFoundReason() {
            stubExchangeThrowing(clientError(HttpStatus.NOT_FOUND, "Product not found"));

            var exception = assertThrows(BusinessException.class, () -> client.purchaseProducts(requestBody));

            assertTrue(exception.getMsg().contains("Product not found"));
        }

        @Test
        @DisplayName("Should hide the upstream detail for a 5xx response")
        void shouldHideServerErrorDetail() {
            stubExchangeThrowing(serverError(HttpStatus.INTERNAL_SERVER_ERROR, "stacktrace"));

            var exception = assertThrows(BusinessException.class, () -> client.purchaseProducts(requestBody));

            assertEquals("Product service failed to process the purchase, please try again later", exception.getMsg());
        }

        @Test
        @DisplayName("Should report an unreachable product service on a connection failure")
        void shouldReportUnreachableService() {
            stubExchangeThrowing(new ResourceAccessException("connection refused"));

            var exception = assertThrows(BusinessException.class, () -> client.purchaseProducts(requestBody));

            assertEquals("Product service is unreachable, please try again later", exception.getMsg());
        }

        @Test
        @DisplayName("Should reject a null response body")
        void shouldRejectNullBody() {
            stubExchangeReturning(ResponseEntity.ok(null));

            var exception = assertThrows(BusinessException.class, () -> client.purchaseProducts(requestBody));

            assertEquals("Product service returned an empty purchase response", exception.getMsg());
        }
    }
}
