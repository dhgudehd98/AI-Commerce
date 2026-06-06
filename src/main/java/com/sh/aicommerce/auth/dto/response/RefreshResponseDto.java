package com.sh.aicommerce.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class RefreshResponseDto {
    private String result;
    private String nickname;
    private String accessToken;

    public RefreshResponseDto(String accessToken, String nickname) {
        this.accessToken = accessToken;
        this.nickname = nickname;
    }
}