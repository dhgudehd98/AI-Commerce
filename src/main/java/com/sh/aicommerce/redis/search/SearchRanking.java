package com.sh.aicommerce.redis.search;

import com.sh.aicommerce.search.dto.RankingDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;


@Component
@RequiredArgsConstructor
public class SearchRanking {

    private final StringRedisTemplate stringRedisTemplate;
    private final static String PREV_KEY = "popular:ranking:prev:";
    private final static String CURRENT_KEY = "popular:ranking:cur"; // UI 기준 검색어 랭킹을 추출 해주는 곳
    private final static String NEW_KEY = "popular:ranking:new";

    public List<RankingDto> getKeywordRanking() {
        /**
         * 현재 시간 기준 2시, 출력에 대한 값은 11~12시에 대한 검색어 결과
         * 10시 - 11시 : 1위 나이키, 2위 아디다스 , 3위 폴햄
         * 11시 - 12시 : 1위 아이앱, 2위 나이키, 3위 : 아크테릭스
         * 12시 - 1시 : 1위 : 애플 , 2위 : 로지텍 3위 : 아디다스
         */
        List<String> prevRankingKeyword = new ArrayList<>( stringRedisTemplate.opsForZSet().reverseRange(PREV_KEY, 0, 9));
        List<String> curRankingKeyword = new ArrayList<>(stringRedisTemplate.opsForZSet().reverseRange(CURRENT_KEY, 0, 9));

        return curRankingKeyword.stream()
                .map(keyword ->{
                    RankingDto rankingDto = new RankingDto();
                    rankingDto.setKeyword(keyword);

                    int prevRank = prevRankingKeyword.indexOf(keyword);
                    int currentRank = curRankingKeyword.indexOf(keyword);

                    if(prevRank < 0) rankingDto.setKeywordRank("NEW");
                    else{
                        int diff = prevRank - currentRank;

                        if(diff > 0) rankingDto.setKeywordRank("+" + diff);
                        else if(diff < 0) rankingDto.setKeywordRank(String.valueOf(diff));
                        else rankingDto.setKeywordRank("");
                    }

                    return rankingDto;
                })
                .toList();

    }

    // 검색어 저장
    public void saveKeyword(String keyword) {

        if(keyword == null || keyword.isBlank()) return;
        keyword = keyword.trim();

        stringRedisTemplate.opsForZSet().incrementScore(NEW_KEY, keyword, 1);
    }

//    @Scheduled(cron = "*/10 * * * * *")
    public void rotate() {
        Boolean newExists = stringRedisTemplate.hasKey(NEW_KEY);

        if (!Boolean.TRUE.equals(newExists)) {
            return;
        }

        Boolean currentExists =
                stringRedisTemplate.hasKey(CURRENT_KEY);

        stringRedisTemplate.delete(PREV_KEY);

        if (Boolean.TRUE.equals(currentExists)) {
            stringRedisTemplate.rename(CURRENT_KEY, PREV_KEY);
        }

        stringRedisTemplate.rename(NEW_KEY, CURRENT_KEY);
    }




}