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
@RequestMapping("/api/product")
public class ProductController {

    private final ProductService productService;

    @GetMapping("{variantId}")
    public ResponseEntity<?> getProduct(
            @PathVariable Long variantId
    ) {

        return ResponseEntity.ok(productService.getProduct(variantId));
    }

    // 상품 등록
    @PostMapping("")
    public ResponseEntity<?> createProduct(
            @Valid @RequestBody ProductCreateRequestDto dto) {
        return ResponseEntity.ok(productService.createProduct(dto));
    }

    // 상품 삭제
    @DeleteMapping("{variantId}")
    public ResponseEntity<?> deleteProduct(
            @PathVariable Long variantId
    ) {
        return ResponseEntity.ok(productService.deleteProduct(variantId));
    }

}