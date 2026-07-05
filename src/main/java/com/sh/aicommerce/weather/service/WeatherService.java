package com.sh.aicommerce.weather.service;


import com.sh.aicommerce.product.es.repository.ProductDocumentRepository;
import com.sh.aicommerce.redis.weather.RedisWeather;
import com.sh.aicommerce.search.dto.SearchResultProductDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WeatherService {

    private final RedisWeather redisWeather;
    private final ProductDocumentRepository productDocumentRepository;


    public List<SearchResultProductDto> recommendProductByWeather() {
        return productDocumentRepository.findByVectors(redisWeather.getWeatherVectors());
    }
}