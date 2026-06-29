package com.sh.aicommerce.redis.weather;

import com.sh.aicommerce.weather.dto.WeatherApiResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedisWeather {

    private final EmbeddingModel embeddingModel;
    private final RedisTemplate<String, Object> weatherRedisTemplate;
    private final static String WEATHER_KEY = "weather:info:vector";

    @Value("${weather.apiKey}")
    private String weatherAPIKey;

    public float[] getWeatherVectors() {
        Object vectors = weatherRedisTemplate.opsForValue().get(WEATHER_KEY);

        if (vectors instanceof List<?>) {
            List<?> list = (List<?>) vectors;
            float[] vectorArray = new float[list.size()];

            for (int i = 0; i < list.size(); i++) {
                vectorArray[i] = ((Number) list.get(i)).floatValue();
            }

            return vectorArray;
        }

        if (vectors instanceof float[]) {
            return (float[]) vectors;
        }

        return null;
    }

//    @Scheduled(cron = "0 * * * * *")
    public void setWeatherEmbedding() {

        String embeddingQuery = getWeatherInfo();
        float[] weatherVectors = embeddingModel.embed(embeddingQuery);

        // Redis Vector 값 저장
        weatherRedisTemplate.opsForValue().set(WEATHER_KEY, weatherVectors);
    }

    // 기상청 API를 활용하여 현재 날씨 정보 요청
    public String getWeatherInfo() {
        String embeddingQuery = "";
        String baseDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String baseTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH")) + "00";

        String url = "http://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getUltraSrtNcst";
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(url)
                .queryParam("serviceKey", weatherAPIKey)
                .queryParam("pageNo", 1)
                .queryParam("numOfRows", 10)
                .queryParam("dataType", "JSON")
                .queryParam("base_date", baseDate)
                .queryParam("base_time", baseTime)
                .queryParam("nx", 60)
                .queryParam("ny", 127);

        RestTemplate restTemplate = new RestTemplate();

        String requestUrl = uriBuilder.build(false).toUriString();

        WeatherApiResponseDto weatherAPIResponse = restTemplate.getForObject(requestUrl, WeatherApiResponseDto.class);

        if (weatherAPIResponse != null && weatherAPIResponse.response().body() != null) {

            // 기온 값 가져오기
            String temperature = weatherAPIResponse.response().body().items().item().stream()
                    .filter(weatherItem -> "T1H".equals(weatherItem.category()))
                    .map(weatherItem -> weatherItem.obsrValue())
                    .findFirst()
                    .orElse("0.0");

            // 강수 상태 가져오기
            String ptyCode = weatherAPIResponse.response().body().items().item().stream()
                    .filter(weatherItem -> "PTY".equals(weatherItem.category()))
                    .map(weatherItem -> weatherItem.obsrValue())
                    .findFirst().get();

            embeddingQuery = String.format("현재 날씨 기온은 %s 이고, 현재 강수형태는 %s입니다.", temperature, convertPtyCode(ptyCode));

            log.info("[날씨 정보] : " + embeddingQuery);
        }

        return embeddingQuery;
    }

    public String convertPtyCode(String ptyCode) {
        return switch (ptyCode){
            case "1" -> "비가오는 날씨";
            case "2" -> "비나 눈이 섞여 오는";
            case "3" -> "눈이 오는";
            case "4" -> "소나기가 내리는";
            default -> "맑은(비 안오는)";

        };
    }
}