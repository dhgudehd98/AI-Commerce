package com.sh.aicommerce.product.es.document;

import com.sh.aicommerce.entity.*;
import com.sh.aicommerce.enums.product.ProductImageType;
import com.sh.aicommerce.product.es.record.ProductIndexRecord;
import com.sh.aicommerce.product.es.record.ProductOptionIndexRecord;
import com.sh.aicommerce.product.es.record.ProductVariantIndexRecord;
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



    public static ProductDocument baseDocumentFromRecord(ProductIndexRecord productRecord, ProductVariantIndexRecord variantRecord) {
        ProductDocument document = new ProductDocument();

        document.productVariantId = variantRecord.productVariantId();
        document.productId = productRecord.productId();
        document.baseProductName = productRecord.baseProductName();
        document.tags = new ArrayList<>(productRecord.tags());
        document.variantName = variantRecord.variantName();
        document.productDescription = productRecord.productDescription();
        document.brandId = productRecord.brandId();
        document.brandName = productRecord.brandName();
        document.category = productRecord.productCategory();
        document.productVariantStatus = variantRecord.productVariantStatus();
        document.color = variantRecord.color();
        document.modelNumber = variantRecord.modelNumber();
        document.price = variantRecord.price();
        document.thumbnailUrl = variantRecord.thumbnailUrl();
        document.imageUrls = variantRecord.imageUrls();
        return document;
    }

    public static ProductDocument createProduct(ProductIndexRecord productRecord, ProductVariantIndexRecord variantRecord, float[] descriptionVector) {
        ProductDocument document = baseDocumentFromRecord(productRecord, variantRecord);

        document.options = variantRecord.options().stream()
                .map(optionRecord -> new ProductOptionDocument(
                        optionRecord.optionId(),
                        optionRecord.sku(),
                        optionRecord.size(),
                        optionRecord.additionalPrice(),
                        optionRecord.totalPrice(variantRecord.price()),
                        optionRecord.optionStatus(),
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

    public static ProductDocument inboundProductVariantDocumentFromRecord(ProductIndexRecord productRecord, ProductVariantIndexRecord variantRecord, float[] descriptionVector) {
        ProductDocument document = baseDocumentFromRecord(productRecord, variantRecord);

        document.options = variantRecord.options().stream()
                .map(optionRecord -> new ProductOptionDocument(
                        optionRecord.optionId(),
                        optionRecord.sku(),
                        optionRecord.size(),
                        optionRecord.additionalPrice(),
                        optionRecord.totalPrice(variantRecord.price()),
                        optionRecord.optionStatus(),
                        optionRecord.availableStock(),
                        optionRecord.inStock()
                ))
                .toList();

        document.totalAvailableStock = document.options.stream()
                .mapToInt(ProductOptionDocument::getAvailableStock)
                .sum();
        document.inStock = document.totalAvailableStock > 0;
        document.descriptionVector = descriptionVector;

        return document;
    }
}
