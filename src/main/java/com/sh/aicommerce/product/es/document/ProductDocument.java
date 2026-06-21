package com.sh.aicommerce.product.es.document;

import com.sh.aicommerce.entity.Product;
import com.sh.aicommerce.entity.ProductImage;
import com.sh.aicommerce.entity.ProductOption;
import com.sh.aicommerce.entity.ProductVariant;
import com.sh.aicommerce.enums.product.ProductImageType;
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

    // Variant마다 개별 문서가 생성되므로 Variant ID 사용
    @Id
    private Long productVariantId;

    // 동일한 상위 상품의 Variant들을 묶어서 조회할 때 사용
    @Field(type = FieldType.Long)
    private Long productId;

    @Field(type = FieldType.Text)
    private String baseProductName;

    @Field(type = FieldType.Text)
    private String variantName;

    @Field(type = FieldType.Text)
    private String productDescription;

    @Field(type = FieldType.Long)
    private Long brandId;

    @Field(type = FieldType.Keyword)
    private String brandName;

    @Field(type = FieldType.Keyword)
    private String category;

    @Field(type = FieldType.Keyword)
    private String productVariantStatus;

    @Field(type = FieldType.Keyword)
    private String color;

    @Field(type = FieldType.Keyword)
    private String modelNumber;

    @Field(type = FieldType.Integer)
    private Integer price;

    @Field(type = FieldType.Keyword)
    private List<String> sizes;

    @Field(type = FieldType.Keyword)
    private List<String> skus;

    @Field(type = FieldType.Keyword)
    private String thumbnailUrl;

    @Field(type = FieldType.Keyword)
    private List<String> imageUrls;

    @Field(type = FieldType.Integer)
    private Integer availableStock;

    @Field(type = FieldType.Boolean)
    private Boolean inStock;

    @Field(type = FieldType.Dense_Vector, dims = 1536)
    private float[] descriptionVector;


    // 벡터에 대한 값 임시 제거 - OpenAI API 결제 후, 벡터에 대한 값 다시 사용
    public static ProductDocument create(Product product, ProductVariant variant) {
        ProductDocument document = new ProductDocument();
        document.productVariantId = variant.getId();
        document.productId = product.getId();
        document.baseProductName = product.getBaseProductName();
        document.variantName = variant.getVariantName();
        document.productDescription = product.getProductDescription();
        document.brandId = product.getBrand().getId();
        document.brandName = product.getBrand().getBrandName();
        document.category = String.valueOf(product.getProductCategory());
        document.productVariantStatus = String.valueOf(variant.getProductVariantStatus()); // 색인 과정이 진행되면 상품상태에 대한 값은 판매중으로 변경
        document.color = variant.getColor();
        document.modelNumber = variant.getModelNumber();
        document.price = variant.getPrice();
        document.sizes = variant.getOptions().stream()
                .map(productOption -> productOption.getSize())
                .toList();
        document.skus = variant.getOptions().stream()
                .map(ProductOption::getSku)
                .toList();
        document.thumbnailUrl = variant.getImages().stream()
                .filter(productImage -> productImage.getImageType() == ProductImageType.THUMBNAIL)
                .map(ProductImage::getImageUrl)
                .findFirst()
                .orElse(null);
        document.imageUrls = variant.getImages().stream()
                .filter(productImage -> productImage.getImageType() != ProductImageType.THUMBNAIL)
                .map(ProductImage::getImageUrl)
                .toList();
        document.inStock = false;
//       document.descriptionVector = vector;

        return document;
    }

}