package com.sh.aicommerce.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Entity
@AllArgsConstructor
@NoArgsConstructor
public class ProductIndexFailLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "index_fail_log_id")
    private Long id;
    private Long productId;
    private String messageId;
    private String failReason;
    private String action;

    public ProductIndexFailLog(Long productId, String messageId, String failReason, String action) {
        this.productId = productId;
        this.messageId = messageId;
        this.failReason = failReason;
        this.action = action;
    }
}