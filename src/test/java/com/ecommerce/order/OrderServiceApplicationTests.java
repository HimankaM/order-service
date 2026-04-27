package com.ecommerce.order;

import com.ecommerce.order.client.ProductClient;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.service.CreateOrderRequest;
import com.ecommerce.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@SpringBootTest
class OrderServiceApplicationTests {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @MockBean
    private ProductClient productClient;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
    }

    @Test
    void contextLoads() {
    }

    @Test
    void shouldCreateOrderSuccessfully() {
        // Mock Product Service response
        ProductClient.ProductDetails mockProduct = ProductClient.ProductDetails.builder()
                .id(1L)
                .name("Laptop Pro 15")
                .price(new BigDecimal("1299.99"))
                .stockQuantity(50)
                .build();

        when(productClient.getProduct(1L)).thenReturn(mockProduct);
        when(productClient.reserveStock(anyLong(), anyInt())).thenReturn(true);

        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerName("John Doe");
        request.setCustomerEmail("john@example.com");

        CreateOrderRequest.OrderItemRequest item = new CreateOrderRequest.OrderItemRequest();
        item.setProductId(1L);
        item.setQuantity(2);
        request.setItems(List.of(item));

        Order order = orderService.createOrder(request);

        assertThat(order.getId()).isNotNull();
        assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.CONFIRMED);
        assertThat(order.getTotalAmount()).isEqualByComparingTo(new BigDecimal("2599.98"));
    }

    @Test
    void shouldFailWhenProductNotFound() {
        when(productClient.getProduct(anyLong())).thenReturn(null);

        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerName("Jane Doe");
        request.setCustomerEmail("jane@example.com");

        CreateOrderRequest.OrderItemRequest item = new CreateOrderRequest.OrderItemRequest();
        item.setProductId(999L);
        item.setQuantity(1);
        request.setItems(List.of(item));

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Product not found");
    }
}
