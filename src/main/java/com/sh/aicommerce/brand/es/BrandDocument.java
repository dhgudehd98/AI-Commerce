package com.sh.aicommerce.brand.es;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.CompletionField;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Mapping;
import org.springframework.data.elasticsearch.annotations.Setting;
import org.springframework.data.elasticsearch.core.suggest.Completion;


@Document(indexName = "brands", createIndex = false)
// 인덱스 자동 생성할때만 Setting , Mapping에 대한 값 설정하기
//@Setting(settingPath = "classpath:elasticSearch/brands_setting.json")
//@Mapping(mappingPath = "classpath:elasticSearch/brands_mapping.json")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Slf4j
public class BrandDocument {
    @Id
    private Long brandId;
    private String brandName;

    @CompletionField
    private Completion suggest;

    private String brandImageUrl;
}