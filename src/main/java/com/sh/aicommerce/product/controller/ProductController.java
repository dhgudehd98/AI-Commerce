package com.sh.aicommerce.product.controller;

import com.sh.aicommerce.product.dto.ProductCreateRequestDto;
import com.sh.aicommerce.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class ProductController {

    private final ProductService productService;
    @PostMapping("product")
    public ResponseEntity<?> createProduct(
            @Valid @RequestBody ProductCreateRequestDto productCreateRequestDto) {
        return ResponseEntity.ok(productService.createProduct(productCreateRequestDto));
    }

}