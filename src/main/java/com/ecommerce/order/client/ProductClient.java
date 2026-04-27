package com.ecommerce.order.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;

/**
 * HTTP client for communicating with the Product Service.
 * This class demonstrates inter-service communication between microservices.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProductClient {

    private final RestTemplate restTemplate;

    @Value("${product.service.url:http://localhost:8081}")
    private String productServiceUrl;

    /**
     * Fetch product details from Product Service.
     */
    @SuppressWarnings("unchecked")
    public ProductDetails getProduct(Long productId) {
        String url = productServiceUrl + "/api/products/" + productId;
        log.info("Calling Product Service at: {}", url);
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            Map<String, Object> body = response.getBody();
            if (body == null) return null;

            return ProductDetails.builder()
                    .id(productId)
                    .name((String) body.get("name"))
                    .price(new BigDecimal(body.get("price").toString()))
                    .stockQuantity((Integer) body.get("stockQuantity"))
                    .build();
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Product not found with id: {}", productId);
            return null;
        } catch (ResourceAccessException e) {
            log.error("Cannot connect to Product Service: {}", e.getMessage());
            throw new RuntimeException("Product Service is unavailable. Please try again later.");
        }
    }

    /**
     * Reserve stock in Product Service for an order.
     * This is the key integration point between Order Service and Product Service.
     */
    @SuppressWarnings("unchecked")
    public boolean reserveStock(Long productId, int quantity) {
        String url = productServiceUrl + "/api/products/" + productId + "/reserve?quantity=" + quantity;
        log.info("Reserving {} units of product {} via Product Service", quantity, productId);
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, null, Map.class);
            Map<String, Object> body = response.getBody();
            return body != null && Boolean.TRUE.equals(body.get("success"));
        } catch (HttpClientErrorException.Conflict e) {
            log.warn("Insufficient stock for product id: {}, quantity: {}", productId, quantity);
            return false;
        } catch (ResourceAccessException e) {
            log.error("Cannot connect to Product Service: {}", e.getMessage());
            throw new RuntimeException("Product Service is unavailable. Please try again later.");
        }
    }

    @lombok.Builder
    @lombok.Data
    public static class ProductDetails {
        private Long id;
        private String name;
        private BigDecimal price;
        private Integer stockQuantity;
    }
}
