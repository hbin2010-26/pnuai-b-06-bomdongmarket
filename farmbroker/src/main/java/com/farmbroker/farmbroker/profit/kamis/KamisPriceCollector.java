package com.farmbroker.farmbroker.profit.kamis;

import com.farmbroker.farmbroker.profit.dto.KamisCollectResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

// 하루 한 번 KAMIS 시세를 받아 스냅샷 표에 반영한다.
// 조사는 매일 있지 않으므로(주말·공휴일 공백) 매번 며칠치를 거슬러 조회해 가장 최근 조사일을 찾는다.
// 시세를 못 받은 작물은 기존 스냅샷을 지우지 않는다 — 지우면 백과사전 기준값으로 떨어져
// 예상 수익이 이유 없이 출렁인다.
@Slf4j
@Component
@RequiredArgsConstructor
public class KamisPriceCollector {

    private final KamisPriceClient client;
    private final KamisItemCodes itemCodes;
    private final KamisSnapshotWriter writer;
    private final KamisProperties properties;

    // 수집은 외부 API 를 작물 수만큼 부르므로 겹쳐 돌면 상대 서버를 몰아친다.
    // 수동 실행 버튼을 연타해도 한 번만 돌게 막는다.
    private final AtomicBoolean running = new AtomicBoolean(false);

    // 마지막으로 외부를 부르기 시작한 시각. 수동 수집의 최소 간격을 재는 기준이다.
    private final AtomicReference<Instant> lastCollectStartedAt = new AtomicReference<>();

    // 매일 새벽 4시. KAMIS 조사 결과가 전날 오후에 올라오므로 그 이후 시간대로 둔다.
    // 주기를 설정으로 뺀 이유 — 배포 후 수집이 실제로 도는지 확인하려면 4시까지 기다릴 수 없다.
    @Scheduled(cron = "${kamis.cron:0 0 4 * * *}", zone = "${kamis.timezone:Asia/Seoul}")
    public void collectDaily() {
        collect(LocalDate.now(properties.zone()));
    }

    public int collect(LocalDate today) {
        return collectWithReport(today).updated();
    }

    // 새벽 배치용. 쿨다운을 보지 않는다 — 하루 한 번이라 쿨다운에 걸릴 일이 없고,
    // 사람이 낮에 눌러 둔 것 때문에 정기 수집이 빠지면 안 된다.
    public KamisCollectResponse collectWithReport(LocalDate today) {
        return collectWithReport(today, false);
    }

    // 수집을 돌리고 작물별 결과까지 돌려준다. 화면의 "지금 받아오기"가 이 결과를 그대로 보여 준다.
    //
    // manual=true 는 사람이 버튼을 누른 경우다. 이 경로는 로그인만 하면 누구나 부를 수 있고
    // 한 번에 외부 API 를 작물 수만큼 부르므로, 연타·반복 호출로 일일 할당량이 마르지 않게
    // 최소 간격을 둔다. AtomicBoolean 은 동시 실행만 막을 뿐 순차 반복은 막지 못한다(#129 리뷰).
    public KamisCollectResponse collectWithReport(LocalDate today, boolean manual) {
        if (!properties.usable()) {
            log.info("KAMIS 수집 건너뜀 — 비활성화 상태이거나 서비스 키가 없습니다.");
            return KamisCollectResponse.skipped(today, KamisCollectResponse.SKIP_DISABLED);
        }
        if (manual && !properties.manualCollectAllowed()) {
            log.info("KAMIS 수동 수집 건너뜀 — 설정에서 꺼져 있습니다.");
            return KamisCollectResponse.skipped(today, KamisCollectResponse.SKIP_DISABLED);
        }
        // 실행 중 여부를 쿨다운보다 먼저 본다. 수집을 시작하면서 곧바로 시작 시각을 찍기 때문에,
        // 순서를 바꾸면 도는 중에 들어온 요청이 전부 COOLDOWN 으로 나간다. 그러면 아직 돌고 있는
        // 작업을 "조금 전에 다 받아왔다"로 잘못 알리게 된다(#129 리뷰).
        if (running.get()) {
            log.info("KAMIS 수집이 이미 실행 중이라 건너뜁니다.");
            return KamisCollectResponse.skipped(today, KamisCollectResponse.SKIP_ALREADY_RUNNING);
        }
        if (manual && withinCooldown()) {
            log.info("KAMIS 수동 수집 건너뜀 — 직전 수집 후 {}초가 지나지 않았습니다.",
                    properties.manualCooldownSeconds());
            return KamisCollectResponse.skipped(today, KamisCollectResponse.SKIP_COOLDOWN);
        }
        // 위 검사와 여기 사이에 다른 요청이 먼저 들어갔을 수 있다. 실제 진입은 CAS 가 정한다.
        if (!running.compareAndSet(false, true)) {
            log.info("KAMIS 수집이 이미 실행 중이라 건너뜁니다.");
            return KamisCollectResponse.skipped(today, KamisCollectResponse.SKIP_ALREADY_RUNNING);
        }

        try {
            // 실제로 외부를 부른 시각을 남긴다. 실패한 호출도 할당량을 쓰므로 결과와 무관하게 찍는다.
            lastCollectStartedAt.set(Instant.now());
            return runCollect(today);
        } finally {
            running.set(false);
        }
    }

    private boolean withinCooldown() {
        Instant last = lastCollectStartedAt.get();
        return last != null
                && Instant.now().isBefore(last.plusSeconds(properties.manualCooldownSeconds()));
    }

    private KamisCollectResponse runCollect(LocalDate today) {
        int updated = 0;
        int missing = 0;
        int failed = 0;
        List<KamisCollectResponse.Item> items = new ArrayList<>();
        List<Map.Entry<String, KamisItemCodes.ItemCode>> entries = List.copyOf(itemCodes.all().entrySet());
        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<String, KamisItemCodes.ItemCode> entry = entries.get(i);
            String cropName = entry.getKey();
            switch (client.fetchLatest(entry.getValue(), today)) {
                // 제철이 아니면 조사 자체가 없다(예: 8월의 딸기). 실패가 아니라 정상 상황이다.
                case KamisPriceClient.Result.NoSurvey ignored -> {
                    missing++;
                    items.add(KamisCollectResponse.Item.missing(cropName));
                }
                // 조회를 못 한 것은 조사가 없는 것과 다르다 — 여기를 MISSING 으로 적으면
                // KAMIS 장애가 "전부 비제철"로 보고된다(#129 리뷰).
                case KamisPriceClient.Result.QueryFailed queryFailed -> {
                    failed++;
                    items.add(KamisCollectResponse.Item.queryFailed(cropName));
                    log.warn("KAMIS 조회 실패 (작물 {}): {}", cropName, queryFailed.detail());
                }
                case KamisPriceClient.Result.Found found -> {
                    KamisPriceClient.DailyPrice daily = found.price();
                    try {
                        writer.upsert(
                                cropName, daily, properties.saleType(), properties.normalizedRegion(),
                                properties.grade(), LocalDateTime.now(properties.zone()));
                        updated++;
                        items.add(KamisCollectResponse.Item.updated(
                                cropName, daily.pricePerKgKrw(), daily.surveyedOn(), daily.sampleCount()));
                    } catch (Exception e) {
                        // 동시 배치나 유니크 충돌은 그 작물만 롤백하고 다음 작물 수집을 이어 간다.
                        failed++;
                        items.add(KamisCollectResponse.Item.saveFailed(cropName));
                        log.warn("KAMIS 스냅샷 저장 실패 (작물 {}): {}", cropName, e.toString());
                    }
                }
            }

            if (i < entries.size() - 1 && !sleepBetweenCalls()) {
                log.info("KAMIS 시세 수집 중단 — 갱신 {}건, 조사 없음 {}건, 실패 {}건", updated, missing, failed);
                return new KamisCollectResponse(today, false, null, updated, missing, failed, items);
            }
        }

        log.info("KAMIS 시세 수집 완료 — 갱신 {}건, 조사 없음 {}건, 실패 {}건", updated, missing, failed);
        return new KamisCollectResponse(today, false, null, updated, missing, failed, items);
    }

    private boolean sleepBetweenCalls() {
        try {
            Thread.sleep(client.callInterval().toMillis());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
