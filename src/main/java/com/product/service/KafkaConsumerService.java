package com.product.service;

import com.product.model.Producto;
import com.product.repository.ProductoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class KafkaConsumerService {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerService.class);

    @Autowired
    private ProductoRepository repository;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @KafkaListener(topics = "order_status_changed_events", groupId = "product-group")
    public void consumeOrderStatusChanged(Map<String, Object> payload) {
        try {
            log.info("Received order status changed event: {}", payload);
            String status = (String) payload.get("status");
            
            if ("PAGADO".equals(status)) {
                String productoId = (String) payload.get("productoId");
                Object cantidadObj = payload.get("cantidad");
                Integer cantidad = 0;
                
                if (cantidadObj instanceof Number) {
                    cantidad = ((Number) cantidadObj).intValue();
                }
                
                if (productoId != null && cantidad > 0) {
                    final Integer cantFinal = cantidad;
                    repository.findById(productoId).ifPresent(producto -> {
                        log.info("Reducing stock for product {}. Current: {}, Reducing by: {}", productoId, producto.getStock(), cantFinal);
                        int nuevoStock = (producto.getStock() != null ? producto.getStock() : 0) - cantFinal;
                        if (nuevoStock < 0) nuevoStock = 0;
                        producto.setStock(nuevoStock);
                        repository.save(producto);
                        
                        kafkaTemplate.send("inventory_update_events", producto);
                        log.info("Emitted inventory_update_events for product {}", productoId);
                    });
                }
            }
        } catch (Exception e) {
            log.error("Error processing order status changed event", e);
        }
    }
}
