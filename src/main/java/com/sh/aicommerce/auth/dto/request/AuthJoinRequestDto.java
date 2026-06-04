package com.sh.aicommerce.auth.dto.request;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class AuthJoinRequestDto {
    private String nickName;
    private String email;
    private String passwd;
    private String zipCode;
    private String address;
    private String addressDetail;

    public void setPasswd(String passwd) {
        this.passwd = passwd;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}