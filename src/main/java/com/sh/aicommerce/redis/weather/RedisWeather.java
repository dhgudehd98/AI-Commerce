package com.sh.aicommerce.redis.weather;

import com.sh.aicommerce.weather.api.WeatherClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedisWeather {

    private final EmbeddingModel embeddingModel;
    private final RedisTemplate<String, Object> weatherRedisTemplate;
    private final WeatherClient weatherClient;
    private final static String WEATHER_KEY = "weather:info:vector";


    public float[] getWeatherVectors() {

        Object vectors = weatherRedisTemplate.opsForValue().get(WEATHER_KEY);

        // vector 대한 값이 정상적으로 들어왔는지확인(Redis Key 미존재 || Redis Key Value 값 없음) -> 직접 날씨 저장하는 API 호출
        float[] weatherVectors = validateWeatherVectors(vectors);

        if (weatherVectors == null || weatherVectors.length == 0) {
            setWeatherEmbedding();

            Object refreshVectors = weatherRedisTemplate.opsForValue().get(WEATHER_KEY);
            weatherVectors = validateWeatherVectors(refreshVectors);
        }

        return weatherVectors;
    }

    // 반환하는 타입별로 유효성 검사
    private float[] validateWeatherVectors(Object value) {

        if(value == null) return null;

        if (value instanceof float[] vectors) {
            return vectors.length == 0 ? null : vectors;
        }

        if (value instanceof double[] vectors) {
            if(vectors.length == 0) return null;

            float[] result = new float[vectors.length];

            for (int i = 0; i < vectors.length; i++) {
                result[i] = (float) vectors[i];
            }

            return result;
        }

        if (value instanceof List<?> vectors) {
            if(vectors.isEmpty()) return null;

            float[] result = new float[vectors.size()];

            for (int i = 0; i < vectors.size(); i++) {
                Object item = vectors.get(i);

                if(!(item instanceof Number number)) return null;

                result[i] = number.floatValue();
            }

            return result;
        }

        return null;

    }


    // 날씨 데이터 Embedding 해서 Redis 저장
//    @Scheduled(cron = "0 * * * * *")
    public void setWeatherEmbedding() {
        String embeddingQuery = weatherClient.createWeatherInfoNLP();
        float[] weatherVectors = embeddingModel.embed(embeddingQuery);

        // Redis Vector 값 저장
        weatherRedisTemplate.opsForValue().set(WEATHER_KEY, weatherVectors);
    }

    // 기상청 API를 활용하여 현재 날씨 정보 요청

}