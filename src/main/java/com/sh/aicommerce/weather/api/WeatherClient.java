package com.sh.aicommerce.weather.api;

import com.sh.aicommerce.common.exception.search.SearchException;
import com.sh.aicommerce.common.exception.search.WeatherException;
import com.sh.aicommerce.weather.dto.WeatherApiResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
@Slf4j
@RequiredArgsConstructor
public class WeatherClient {

    @Value("${weather.apiKey}")
    private String weatherAPIKey;

    private static final int MAX_RETRY_COUNT = 3;

    @Qualifier("weatherRestTemplate")
    private final RestTemplate weatherRestTemplate;

    public String createWeatherInfoNLP() {
        WeatherApiResponseDto response = getCurrentWeatherInfo();


        String temperature = extractTemperature(response);
        String ptyCode = convertPtyCode(extractPtyCode(response));

        log.info("[날씨 API 요청 기온 : {}]", temperature);
        log.info("[날씨 API 요청 강수형태 : {}", ptyCode);

        String embeddingQuery = String.format("현재 날씨 기온은 %s 이고, 현재 강수형태는 %s입니다.", temperature, ptyCode);

        log.info("[임베딩할 날씨 정보] : {}", embeddingQuery);

        return embeddingQuery;
    }
    // 현재 날씨 기준으로 날씨 API 요청 URI 생성
    public WeatherApiResponseDto getCurrentWeatherInfo() {

        for (int retryCount = 1; retryCount <= MAX_RETRY_COUNT; retryCount++) {
            try {
                String requestUri = createCurrentWeatherUri();
                ResponseEntity<WeatherApiResponseDto> response = weatherRestTemplate.getForEntity(requestUri, WeatherApiResponseDto.class);

                WeatherApiResponseDto weatherApiResponseDto = response.getBody();

                validateWeatherApiResponse(weatherApiResponseDto);

                return weatherApiResponseDto;
            }catch (ResourceAccessException e) {
                log.warn("[날씨 API 연결/응답 Timeout] retryCount: {}", retryCount, e);
            } catch (HttpClientErrorException e) {
                log.warn(
                        "[날씨 API 4xx 응답] retryCount: {}, statusCode: {}, responseBody: {}",
                        retryCount,
                        e.getStatusCode(),
                        e.getResponseBodyAsString(),
                        e
                );
                throw new WeatherException("날씨 API HTTP 응답 오류 ");
            } catch (HttpServerErrorException e) {
                log.warn(
                        "[날씨 API 5xx 응답] retryCount: {}, statusCode: {}, responseBody: {}",
                        retryCount,
                        e.getStatusCode(),
                        e.getResponseBodyAsString(),
                        e
                );
                throw new WeatherException("날씨 API HTTP 응답 오류 ");
            } catch (RestClientException e) {
                log.warn("[날씨 API 요청 처리 실패] retryCount: {}", retryCount, e);
            } catch (WeatherException e) {
                log.warn("[날씨 API 응답 검증 실패] retryCount: {}, message: {}", retryCount,
                        e.getMessage());
            }
        }

        throw new WeatherException("날씨 API 요청이 실패하였습니다.");
    }

    private String createCurrentWeatherUri() {
        String baseDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String baseTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH")) + "00";

        String url = "http://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getUltraSrtNcst";
        return UriComponentsBuilder.fromHttpUrl(url)
                .queryParam("serviceKey", weatherAPIKey)
                .queryParam("pageNo", 1)
                .queryParam("numOfRows", 10)
                .queryParam("dataType", "JSON")
                .queryParam("base_date", baseDate)
                .queryParam("base_time", baseTime)
                .queryParam("nx", 60)
                .queryParam("ny", 127)
                .build(false)
                .toUriString();
    }

    private void validateWeatherApiResponse(WeatherApiResponseDto response) {
        if (response == null || response.response() == null)
        {
            throw new WeatherException("날씨 API 응답이 비어 있습니다.");
        }

        WeatherApiResponseDto.Response weatherResponse =
                response.response();

        if (weatherResponse.header() == null ||!"00".equals(weatherResponse.header().resultCode())) {
            throw new WeatherException("날씨 API 응답 헤더의 오류가 발생하였 습니다.");
        }

        if (weatherResponse.body() == null ||
                weatherResponse.body().items() == null ||
                weatherResponse.body().items().item() == null ||
                weatherResponse.body().items().item().isEmpty()) {
            throw new WeatherException("날씨 API 응답 Body 값이 비어있습니다.");
        }
    }

    // 현재 날씨 형태(강수형태)값 가져오기
    private String extractPtyCode(WeatherApiResponseDto weatherApiResponseDto) {
        return weatherApiResponseDto.response().body().items().item().stream()
                .filter(weatherItem -> "PTY".equals(weatherItem.category()))
                .map(weatherItem -> weatherItem.obsrValue())
                .findFirst()
                .orElseThrow(() -> new WeatherException("[날씨 API PTY 응답 에러] "));
    }

    // 현재 기온에 대한 값 가져오기
    private String extractTemperature(WeatherApiResponseDto weatherApiResponseDto) {
        return weatherApiResponseDto.response().body().items().item().stream()
                .filter(weatherItem -> "T1H".equals(weatherItem.category()))
                .map(weatherItem -> weatherItem.obsrValue())
                .findFirst()
                .orElse("0.0");
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