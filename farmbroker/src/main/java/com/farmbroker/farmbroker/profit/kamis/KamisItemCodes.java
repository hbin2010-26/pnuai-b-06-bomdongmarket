package com.farmbroker.farmbroker.profit.kamis;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

// 작물명 → KAMIS 부류코드·품목코드 표.
// KAMIS는 작물명이 아니라 코드로만 조회할 수 있고, 두 코드가 모두 필수 파라미터다.
// 백과사전 작물 중 KAMIS에 대응 품목이 없는 것은 여기 없으면 수집 대상에서 빠진다.
//
// ── 2026-08-23 실제 응답으로 전수 대조한 결과 ──
// 코드를 잘못 적으면 다른 작물 가격이 조용히 들어온다. 실제로 청경채(258)는 깐마늘,
// 쑥갓(252)은 미나리, 케일(257)은 멜론 가격을 가져오고 있었다. 세 작물은 이 API 에
// 품목 자체가 없어 행을 지웠다(미나리는 제 코드로 다시 넣었다).
//
// 딸기도 뺐다 — 품목코드 226 은 존재하지 않고, 부류 200·300·400 을 전수 조회해도
// 딸기 품목이 없다. 계산기 작물이지만 이 엔드포인트로는 시세를 받을 수 없다.
//
// 부추(254)·양상추(262)·양송이버섯(321)·표고버섯(322)은 코드는 맞지만 친환경(se_cd=07)
// 조사만 있다. 07 은 백화점·생협 소매가라 중도매가(02)와 성격이 달라 섞으면 안 되고,
// 조사도 45일에 2~4일뿐이라 신선도 기준을 넘기지 못한다. 그래서 넣지 않는다.
@Slf4j
@Component
public class KamisItemCodes {

    public record ItemCode(String categoryCode, String itemCode) {
    }

    private final Map<String, ItemCode> codes = new LinkedHashMap<>();

    @PostConstruct
    void load() {
        ClassPathResource resource = new ClassPathResource("profit/kamis_item_codes.csv");
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            reader.readLine(); // 헤더
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] parts = line.split(",");
                if (parts.length < 3) continue;
                codes.put(parts[0].trim(), new ItemCode(parts[1].trim(), parts[2].trim()));
            }
        } catch (IOException e) {
            throw new IllegalStateException("KAMIS 품목코드 표를 읽지 못했습니다.", e);
        }
        log.info("KAMIS 품목코드 {}건 로드", codes.size());
    }

    public Map<String, ItemCode> all() {
        return Map.copyOf(codes);
    }
}
