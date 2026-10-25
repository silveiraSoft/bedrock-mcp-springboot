package io.github.adalberto.bedrockmcp.catalog;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** SAMPLE business REST API (the API you want to drive with natural language). */
@RestController
@RequestMapping("/api/v1")
public class CatalogController {

    private final CatalogService service;

    public CatalogController(CatalogService service) {
        this.service = service;
    }

    @GetMapping("/products")
    public List<Product> search(@RequestParam(required = false) String query,
            @RequestParam(required = false) String category) {
        return service.search(query, category);
    }

    @GetMapping("/products/{id}")
    public Product get(@PathVariable String id) {
        return service.find(id).orElseThrow(() -> new CatalogService.NotFoundException("Product " + id + " not found"));
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public Order createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return service.createOrder(request);
    }
}
