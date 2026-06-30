package com.sh.aicommerce.redis.search;

import com.sh.aicommerce.search.dto.RankingDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
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

    private static final String ROTATE_SCRIPT = """
          if redis.call('EXISTS', KEYS[3]) == 0 then
              return 0
          end

          redis.call('DEL', KEYS[1])

          if redis.call('EXISTS', KEYS[2]) == 1 then
              redis.call('RENAME', KEYS[2], KEYS[1])
          end

          redis.call('RENAME', KEYS[3], KEYS[2])

          return 1
          """;


    public List<RankingDto> getKeywordRanking() {
        /**
         * 현재 시간 딱 1시 : rotate() 시작
         * PREV -> 10 - 11 : D A C
         * CUR -> 11 - 12 : B A C
         * NEW -> 12 - 1시  : A B C
         */
        List<String> prevRankingKeyword = new ArrayList<>( stringRedisTemplate.opsForZSet().reverseRange(PREV_KEY, 0, 19));
        List<String> curRankingKeyword = new ArrayList<>(stringRedisTemplate.opsForZSet().reverseRange(CURRENT_KEY, 0, 19));

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


        stringRedisTemplate.opsForZSet().incrementScore(NEW_KEY, keyword, 1);
    }

//    @Scheduled(cron = "*/10 * * * * *")
    public void rotate() {

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();

        script.setResultType(Long.class);
        script.setScriptText(ROTATE_SCRIPT);
        /**
         * Redis는 싱글스레드로 이루어져 있어서 명령어가 단일 명령으로 시작이 되기 때문에
         * KEY에 대한 값을 원자적으로 변경하기 위해서 Lua Script로 변경 Redis 명령어를 LuaScript를 사용하여 원자적으로 변경하도록 설정
         */
        // KEYS[1] : PREV_KEY, KEYS[2] : CUR_KEY, KEYS[3] : NEW_KEY
        // Rua에서 List에 대한 부분은 1부터 시작하기 떄문에 스크립트 부분에서 KEYS[1]로 실행
        // Lua 스크립트에서
        stringRedisTemplate.execute(script, List.of(PREV_KEY, CURRENT_KEY, NEW_KEY));
    }




}