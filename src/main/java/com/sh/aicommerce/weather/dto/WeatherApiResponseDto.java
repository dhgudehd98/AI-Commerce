package com.sh.aicommerce.weather.dto;

import java.util.List;

public record WeatherApiResponseDto(Response response) {

    public record Response(Header header, Body body) {}

    public record Header(String resultCode, String resultMsg) {}

    public record Body(String dataType, Items items) {}

    public record Items(List<WeatherItem> item) {}

    public record WeatherItem(
            String category,
            String obsrValue,
            String baseDate,
            String baseTime
    ) {}
}