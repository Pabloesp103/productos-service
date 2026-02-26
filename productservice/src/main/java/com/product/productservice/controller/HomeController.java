package com.product.productservice.controller;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/products-api")
public class HomeController {

	@GetMapping("/")
	public String home() {
		return "Hola mundo";
	}

	@GetMapping("/allProducts")
	public List<Map<String, Object>> products() {
		return
		Arrays.asList(
			Map.of("id", 1, "name", "Product 1", "price", 100),
			Map.of("id", 2, "name", "Product 2", "price", 200),
			Map.of("id", 3, "name", "Product 3", "price", 300)
		);
	}
}

