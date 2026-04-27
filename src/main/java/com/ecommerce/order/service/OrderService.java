package com.ecommerce.order.service;

import com.ecommerce.order.client.ProductClient;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.model.OrderItem;
import com.ecommerce.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductClient productClient;

    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    public Optional<Order> getOrderById(Long id) {
        return orderRepository.findById(id);
    }

    public List<Order> getOrdersByEmail(String email) {
        return orderRepository.findByCustomerEmail(email);
    }

    /**
     * Create an order by:
     * 1. Calling Product Service to validate each product exists
     * 2. Calling Product Service to reserve stock
     * 3. Persisting the order locally
     *
     * This is the core inter-service communication flow.
     */
    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        log.info("Creating order for customer: {}", request.getCustomerEmail());

        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        // Step 1: Validate all products exist via Product Service
        for (CreateOrderRequest.OrderItemRequest itemReq : request.getItems()) {
            ProductClient.ProductDetails product = productClient.getProduct(itemReq.getProductId());

            if (product == null) {
                throw new IllegalArgumentException("Product not found with id: " + itemReq.getProductId());
            }

            if (product.getStockQuantity() < itemReq.getQuantity()) {
                throw new IllegalStateException(
                        "Insufficient stock for product: " + product.getName() +
                        ". Available: " + product.getStockQuantity() +
                        ", Requested: " + itemReq.getQuantity()
                );
            }

            BigDecimal itemTotal = product.getPrice().multiply(BigDecimal.valueOf(itemReq.getQuantity()));
            totalAmount = totalAmount.add(itemTotal);

            OrderItem item = OrderItem.builder()
                    .productId(product.getId())
                    .productName(product.getName())
                    .quantity(itemReq.getQuantity())
                    .unitPrice(product.getPrice())
                    .build();
            orderItems.add(item);
        }

        // Step 2: Reserve stock in Product Service for all items
        for (int i = 0; i < request.getItems().size(); i++) {
            CreateOrderRequest.OrderItemRequest itemReq = request.getItems().get(i);
            boolean reserved = productClient.reserveStock(itemReq.getProductId(), itemReq.getQuantity());
            if (!reserved) {
                throw new IllegalStateException(
                        "Failed to reserve stock for product id: " + itemReq.getProductId()
                );
            }
        }

        // Step 3: Save order
        Order order = Order.builder()
                .customerName(request.getCustomerName())
                .customerEmail(request.getCustomerEmail())
                .status(Order.OrderStatus.CONFIRMED)
                .totalAmount(totalAmount)
                .items(new ArrayList<>())
                .build();

        Order savedOrder = orderRepository.save(order);

        for (OrderItem item : orderItems) {
            item.setOrder(savedOrder);
            savedOrder.getItems().add(item);
        }

        Order finalOrder = orderRepository.save(savedOrder);
        log.info("Order created successfully with id: {}", finalOrder.getId());
        return finalOrder;
    }

    @Transactional
    public Optional<Order> updateOrderStatus(Long id, Order.OrderStatus newStatus) {
        return orderRepository.findById(id).map(order -> {
            order.setStatus(newStatus);
            log.info("Updated order {} status to {}", id, newStatus);
            return orderRepository.save(order);
        });
    }
}
