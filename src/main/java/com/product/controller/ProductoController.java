package com.product.controller;

import com.product.model.Producto;
import com.product.repository.ProductoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/productos")
public class ProductoController {

    @Autowired
    private ProductoRepository repository;

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
    public Producto save(@RequestBody Producto producto) {
        log.info("Guardando nuevo producto: {}", producto.getNombre());
        return repository.save(producto);
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
