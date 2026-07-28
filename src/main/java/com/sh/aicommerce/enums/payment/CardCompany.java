package com.sh.aicommerce.enums.payment;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum CardCompany {

    IBK_BC("3K"),
    GWANGJUBANK("46"),
    LOTTE("71"),
    KDBBANK("30"),
    BC("31"),
    SAMSUNG("51"),
    SAEMAUL("38"),
    SHINHAN("41"),
    SHINHYEOP("62"),
    CITI("36"),
    WOORI_BC("33"),
    WOORI("W1"),
    POST("37"),
    SAVINGBANK("39"),
    JEONBUKBANK("35"),
    JEJUBANK("42"),
    KAKAOBANK("15"),
    KBANK("3A"),
    TOSSBANK("24"),
    HANA("21"),
    HYUNDAI("61"),
    KOOKMIN("11"),
    NONGHYEOP("91"),
    SUHYEOP("34"),
    현대("100"), //! 여기에 대한 값은 나중에 어떻게 할지 좀 생각을 해봐야겠따

    UNKNOWN(null);

    private final String code;

    CardCompany(String code) {
        this.code = code;
    }

    private static final Map<String, CardCompany> CODE_MAP =
            Arrays.stream(values())
                    .filter(cardCompany -> cardCompany.code != null)
                    .collect(Collectors.toUnmodifiableMap(
                            CardCompany::getCode,
                            Function.identity()
                    ));

    public String getCode() {
        return code;
    }

    public static CardCompany fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }

        return CODE_MAP.getOrDefault(code, UNKNOWN);
    }

    public static CardCompany fromCodeOrThrow(String code) {
        CardCompany cardCompany = CODE_MAP.get(code);

        if (cardCompany == null) {
            throw new IllegalArgumentException(
                    "지원하지 않는 카드사 코드입니다: " + code
            );
        }

        return cardCompany;
    }
}