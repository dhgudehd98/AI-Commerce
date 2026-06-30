package com.sh.aicommerce.product.es.document;

import com.sh.aicommerce.entity.*;
import com.sh.aicommerce.enums.product.ProductImageType;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Document(indexName = "products", createIndex = false)
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Slf4j
public class ProductDocument {


    @Id
    private Long productVariantId;

    @Field(type = FieldType.Long)
    private Long productId;

    @MultiField(
            mainField = @Field(
                    type = FieldType.Text,
                    analyzer = "products_index_analyzer",
                    searchAnalyzer = "products_search_analyzer"
            ),
            otherFields = {
                    @InnerField(suffix = "keyword", type = FieldType.Keyword)
            }
    )
    private String baseProductName;

    @MultiField(
            mainField = @Field(
                    type = FieldType.Text,
                    analyzer = "products_index_analyzer",
                    searchAnalyzer = "products_search_analyzer"
            ),
            otherFields = {
                    @InnerField(suffix = "keyword", type = FieldType.Keyword)
            }
    )
    private List<String> tags = new ArrayList<>();

    @MultiField(
            mainField = @Field(
                    type = FieldType.Text,
                    analyzer = "products_index_analyzer",
                    searchAnalyzer = "products_search_analyzer"
            ),
            otherFields = {
                    @InnerField(suffix = "keyword", type = FieldType.Keyword)
            }
    )
    private String variantName;

    @Field(
            type = FieldType.Text,
            analyzer = "products_index_analyzer",
            searchAnalyzer = "products_search_analyzer"
    )
    private String productDescription;

    @Field(type = FieldType.Long)
    private Long brandId;

    @MultiField(
            mainField = @Field(
                    type = FieldType.Text,
                    analyzer = "products_index_analyzer",
                    searchAnalyzer = "products_search_analyzer"
            ),
            otherFields = {
                    @InnerField(suffix = "keyword", type = FieldType.Keyword)
            }
    )
    private String brandName;

    @Field(type = FieldType.Keyword)
    private String category;

    @Field(type = FieldType.Keyword)
    private String color;

    @Field(type = FieldType.Keyword)
    private String modelNumber;

    @Field(type = FieldType.Integer)
    private Integer price;

    @Field(type = FieldType.Keyword)
    private String productVariantStatus;

    @Field(type = FieldType.Keyword, index = false)
    private String thumbnailUrl;

    @Field(type = FieldType.Keyword, index = false)
    private List<String> imageUrls;

    @Field(type = FieldType.Nested)
    private List<ProductOptionDocument> options;

    @Field(type = FieldType.Integer)
    private Integer totalAvailableStock;

    @Field(type = FieldType.Boolean)
    private Boolean inStock;

    @Field(type = FieldType.Dense_Vector, dims = 1536)
    private float[] descriptionVector;


    // 벡터에 대한 값 임시 제거 - OpenAI API 결제 후, 벡터에 대한 값 다시 사용

    // 공통 값 설정
    public static ProductDocument baseDocument(Product product, ProductVariant variant) {
        ProductDocument document = new ProductDocument();

        document.productVariantId = variant.getId();
        document.productId = product.getId();
        document.baseProductName = product.getBaseProductName();
        document.tags = new ArrayList<>(product.getTags());
        document.variantName = variant.getVariantName();
        document.productDescription = product.getProductDescription();
        document.brandId = product.getBrand().getId();
        document.brandName = product.getBrand().getBrandName();
        document.category = product.getProductCategory().name();
        document.productVariantStatus = String.valueOf(variant.getProductVariantStatus());
        document.color = variant.getColor();
        document.modelNumber = variant.getModelNumber();
        document.price = variant.getPrice();
        document.thumbnailUrl = variant.getImages().stream()
                .filter(productImage -> productImage.getImageType() == ProductImageType.THUMBNAIL)
                .map(productImage -> productImage.getImageUrl())
                .findFirst()
                .orElse(null);
        document.imageUrls = variant.getImages().stream()
                .filter(productImage -> productImage.getImageType() != ProductImageType.THUMBNAIL)
                .sorted(Comparator.comparing(ProductImage::getDisplayOrder))
                .map(ProductImage::getImageUrl)
                .toList();

        return document;
    }
    public static ProductDocument createProduct(Product product, ProductVariant variant, float[] descriptionVector) {
        ProductDocument document = baseDocument(product, variant);
        document.options = variant.getOptions().stream()
                .map(option -> new ProductOptionDocument(
                        option.getId(),
                        option.getSku(),
                        option.getSize(),
                        option.getAdditionalPrice(),
                        variant.getPrice() + option.getAdditionalPrice(),
                        String.valueOf(option.getStatus()),
                        0,
                        false
                ))
                .toList();
        // 초기 상품 등록 할 때는 이용 가능한 재고에 대한 값 0으로 설정
        document.totalAvailableStock = 0;
        document.inStock = false;
        document.descriptionVector = descriptionVector;

        return document;
    }


    // 상품 입고 후 , 옵션별 재고 업데이트
    public static ProductDocument inboundProduct(Product product, ProductVariant variant) {
        ProductDocument document = baseDocument(product, variant);
        document.options = variant.getOptions().stream()
                .map(option -> {
                    int stock = option.getInventories().stream()
                            .mapToInt(ProductInventory::getAvailableQuantity)
                            .sum();

                    return new ProductOptionDocument(
                            option.getId(),
                            option.getSku(),
                            option.getSize(),
                            option.getAdditionalPrice(),
                            variant.getPrice() + option.getAdditionalPrice(),
                            option.getStatus().name(),
                            stock,
                            stock > 0
                    );
                })
                .toList();

        document.totalAvailableStock = document.options.stream()
                .mapToInt(ProductOptionDocument::getAvailableStock)
                .sum();

        document.inStock = document.totalAvailableStock > 0;

        return document;
    }
}
