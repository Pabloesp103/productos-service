package com.product.controller;

import com.product.model.Producto;
import com.product.repository.ProductoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/productos")
public class ProductoController {

    private static final Logger log = LoggerFactory.getLogger(ProductoController.class);

    @Autowired
    private ProductoRepository repository;

    @Autowired
    private org.springframework.kafka.core.KafkaTemplate<String, Object> kafkaTemplate;

    @GetMapping
    public List<Producto> getAll() {
        log.info("Consultando todos los productos");
        return repository.findAll();
    }

    @GetMapping("/{id}")
    public Producto getById(@PathVariable String id) {
        log.info("Consultando producto con ID: {}", id);
        return repository.findById(id).orElse(null);
    }

    @PostMapping
    public Producto save(@RequestBody Producto producto, @RequestHeader(value = "X-Retry-Attempt", required = false) String isRetry) {
        log.info("ENTRADA POST /productos: {}", producto.getNombre());
        try {
            log.info("Intentando guardar en MongoDB...");
            return repository.save(producto);
        } catch (Throwable e) {
            log.error("¡FALLO DETECTADO! Causa: {}", e.getMessage());

            if ("true".equals(isRetry)) {
                log.warn("El reintento falló de nuevo. NO se re-enviará a Kafka.");
                throw new RuntimeException("Fallo persistente en MongoDB. Deteniendo ciclo.", e);
            }
            
            log.info("Iniciando flujo Kafka ASÍNCRONO...");
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    log.info("[HILO-KAFKA] Preparando payload...");
                    java.util.Map<String, Object> payload = new java.util.HashMap<>();
                    payload.put("data", producto);
                    payload.put("sendEmail", new java.util.HashMap<String, String>() {{
                        put("status", "PENDING");
                        put("message", "Pendiente de reintento");
                    }});
                    payload.put("updateRetryJobs", new java.util.HashMap<String, String>() {{
                        put("status", "PENDING");
                        put("message", "Pendiente de reintento");
                    }});
                    
                    log.info("[HILO-KAFKA] Enviando a tópico product_retry_jobs...");
                    kafkaTemplate.send("product_retry_jobs", payload).whenComplete((result, ex) -> {
                        if (ex == null) {
                            log.info("[HILO-KAFKA] ¡Publicación exitosa! Offset: {}", result.getRecordMetadata().offset());
                        } else {
                            log.error("[HILO-KAFKA] Error en el callback de Kafka: {}", ex.getMessage());
                        }
                    });
                } catch (Exception ex) {
                    log.error("[HILO-KAFKA] Error crítico en el hilo: {}", ex.getMessage());
                }
            });
            
            throw new RuntimeException("Error al guardar producto. Iniciado proceso de reintento asíncrono.", e);
        }
    }

    @PutMapping("/{id}")
    public Producto update(@PathVariable String id, @RequestBody Producto producto) {
        log.info("Actualizando producto ID: {}", id);
        producto.setId(id);
        return repository.save(producto);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        log.info("Eliminando producto ID: {}", id);
        repository.deleteById(id);
    }
}
