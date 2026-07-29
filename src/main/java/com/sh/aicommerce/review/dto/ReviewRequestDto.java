package com.sh.aicommerce.review.dto;


import com.sh.aicommerce.enums.review.FitEvaluation;
import com.sh.aicommerce.enums.review.FitPreference;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ReviewRequestDto {

    Integer rating;
    String content;
    Integer heightCm;
    Integer weightKg;
    FitEvaluation fitEvaluation;
    FitPreference fitPreference;
}