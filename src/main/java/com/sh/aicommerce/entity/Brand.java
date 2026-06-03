package com.sh.aicommerce.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_brand_name", columnNames = "brandName")
        }
)
@Getter
@NoArgsConstructor
public class Brand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "brand_id")
    private Long id;

    @Column(nullable = false)
    private String brandName;

    private String brandImageURL;

    @OneToMany(mappedBy = "brand")
    private List<Product> products = new ArrayList<>();
}
