package com.sh.aicommerce.product.es;

import com.sh.aicommerce.entity.Product;
import com.sh.aicommerce.entity.ProductOption;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.util.List;
import java.util.stream.Collectors;

@Document(indexName = "product")
@Setting(settingPath = "classpath:elasticSearch/products_setting.json")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Slf4j
public class ProductDocument {

    @Id
    private Long productId;

    private String productName;
    private String productDescription;

    private Long brandId;
    private String brandName;

    private String category;
    private String productStatus;

    private Integer price;

    private List<String> colors;
    private List<String> sizes;
    private List<String> skus;
    private List<String> tags;
//    private List<String> imageUrls;

    private Integer availableStock;
    private Boolean inStock;

    @Field(type = FieldType.Dense_Vector, dims = 1536)
    private float[] descriptionVector;

    public ProductDocument(Product product, float[] descriptionVector) {
        this.productId = product.getId();
        this.productName = product.getProductName();
        this.productDescription = product.getProductDescription();
        this.brandId = product.getBrand().getId();
        this.brandName = product.getBrand().getBrandName();
        this.category = String.valueOf(product.getProductCategory());
        this.productStatus = String.valueOf(product.getProductStatus());
        this.price = product.getPrice();
        this.colors = product.getProductOptions()
                .stream()
                .map(ProductOption::getColor)
                .distinct()
                .collect(Collectors.toList());

        this.sizes = product.getProductOptions()
                .stream()
                .map(ProductOption::getSize)
                .distinct()
                .toList();

        this.skus = product.getProductOptions()
                .stream()
                .map(ProductOption::getSku)
                .toList();

        this.tags = List.of("상의", "후드"); // 임시 설정

        this.descriptionVector = descriptionVector;
    }

    // 벡터에 대한 값 임시 제거 - OpenAI API 결제 후, 벡터에 대한 값 다시 사용
    public ProductDocument(Product product) {
        this.productId = product.getId();
        this.productName = product.getProductName();
        this.productDescription = product.getProductDescription();
        this.brandId = product.getBrand().getId();
        this.brandName = product.getBrand().getBrandName();
        this.category = String.valueOf(product.getProductCategory());
        this.productStatus = String.valueOf(product.getProductStatus());
        this.price = product.getPrice();
        this.colors = product.getProductOptions()
                .stream()
                .map(ProductOption::getColor)
                .distinct()
                .collect(Collectors.toList());

        this.sizes = product.getProductOptions()
                .stream()
                .map(ProductOption::getSize)
                .distinct()
                .toList();

        this.skus = product.getProductOptions()
                .stream()
                .map(ProductOption::getSku)
                .toList();

        this.tags = List.of("상의", "후드"); // 임시 설정

//        this.descriptionVector = descriptionVector;
    }
}