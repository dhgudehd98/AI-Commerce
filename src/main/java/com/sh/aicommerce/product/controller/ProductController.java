package com.sh.aicommerce.product.controller;

import com.sh.aicommerce.product.dto.request.ProductCreateRequestDto;
import com.sh.aicommerce.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class ProductController {

    private final ProductService productService;
    @PostMapping("product")
    public ResponseEntity<?> createProduct(
            @Valid @RequestBody ProductCreateRequestDto dto) {
        return ResponseEntity.ok(productService.createProduct(dto));
    }

    @PostMapping("delete/{id}")
    public ResponseEntity<?> deleteProduct(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(productService.deleteProduct(id));
    }

}