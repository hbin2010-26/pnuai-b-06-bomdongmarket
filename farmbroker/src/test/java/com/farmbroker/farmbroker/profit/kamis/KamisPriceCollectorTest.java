package com.farmbroker.farmbroker.profit.kamis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import com.farmbroker.farmbroker.profit.dto.KamisCollectResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class KamisPriceCollectorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 13);
    private static final KamisProperties ENABLED_PROPERTIES =
            new KamisProperties("key", null, "02", null, "상품", 7, 14, true,
                    true, 0, "Asia/Seoul", 3000, 5000);

    @Mock
    private KamisPriceClient client;
    @Mock
    private KamisItemCodes itemCodes;
    @Mock
    private KamisSnapshotWriter writer;

    @Test
    @DisplayName("시세가 있는 작물만 스냅샷을 저장한다")
    void writes_only_crops_with_prices() {
        KamisItemCodes.ItemCode lettuceCode = new KamisItemCodes.ItemCode("200", "212");
        KamisItemCodes.ItemCode strawberryCode = new KamisItemCodes.ItemCode("200", "226");
        KamisPriceClient.DailyPrice lettucePrice = new KamisPriceClient.DailyPrice(TODAY, 9500, 4);
        given(itemCodes.all()).willReturn(items(
                Map.entry("상추", lettuceCode),
                Map.entry("딸기", strawberryCode)));
        given(client.fetchLatest(lettuceCode, TODAY)).willReturn(found(lettucePrice));
        given(client.fetchLatest(strawberryCode, TODAY))
                .willReturn(new KamisPriceClient.Result.NoSurvey());
        given(client.callInterval()).willReturn(Duration.ZERO);

        int updated = collector(ENABLED_PROPERTIES).collect(TODAY);

        assertThat(updated).isEqualTo(1);
        verify(writer).upsert(
                eq("상추"), eq(lettucePrice), eq("02"), eq(""), eq("상품"), any(LocalDateTime.class));
        verify(writer, never()).upsert(
                eq("딸기"), any(), anyString(), anyString(), anyString(), any(LocalDateTime.class));
        verifyNoMoreInteractions(writer);
    }

    @Test
    @DisplayName("한 작물 저장이 실패해도 다음 작물 수집을 계속한다")
    void continues_after_writer_failure() {
        KamisItemCodes.ItemCode lettuceCode = new KamisItemCodes.ItemCode("200", "212");
        KamisItemCodes.ItemCode spinachCode = new KamisItemCodes.ItemCode("200", "213");
        KamisPriceClient.DailyPrice lettucePrice = new KamisPriceClient.DailyPrice(TODAY, 9500, 4);
        KamisPriceClient.DailyPrice spinachPrice = new KamisPriceClient.DailyPrice(TODAY, 8200, 3);
        given(itemCodes.all()).willReturn(items(
                Map.entry("상추", lettuceCode),
                Map.entry("시금치", spinachCode)));
        given(client.fetchLatest(lettuceCode, TODAY)).willReturn(found(lettucePrice));
        given(client.fetchLatest(spinachCode, TODAY)).willReturn(found(spinachPrice));
        given(client.callInterval()).willReturn(Duration.ZERO);
        willThrow(new IllegalStateException("unique constraint"))
                .given(writer).upsert(
                        eq("상추"), eq(lettucePrice), anyString(), anyString(), anyString(), any(LocalDateTime.class));

        int updated = collector(ENABLED_PROPERTIES).collect(TODAY);

        assertThat(updated).isEqualTo(1);
        verify(writer).upsert(
                eq("시금치"), eq(spinachPrice), eq("02"), eq(""), eq("상품"), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("KAMIS 설정을 사용할 수 없으면 수집을 시작하지 않는다")
    void skips_collection_when_properties_are_not_usable() {
        KamisProperties disabledProperties =
                new KamisProperties("", null, "02", null, "상품", 7, 14, true,
                        true, 0, "Asia/Seoul", 3000, 5000);

        int updated = collector(disabledProperties).collect(TODAY);

        assertThat(updated).isZero();
        verifyNoInteractions(client, itemCodes, writer);
    }

    // ── #129 리뷰 세 경로 ────────────────────────────────────────────────

    @Test
    @DisplayName("외부 조회 실패는 조사 없음이 아니라 실패로 센다")
    void query_failure_is_not_reported_as_missing() {
        // KAMIS 가 죽어 있는 동안 모든 작물이 MISSING 으로 보고되면 장애가 정상으로 읽힌다.
        KamisItemCodes.ItemCode lettuceCode = new KamisItemCodes.ItemCode("200", "212");
        given(itemCodes.all()).willReturn(items(Map.entry("상추", lettuceCode)));
        given(client.fetchLatest(lettuceCode, TODAY))
                .willReturn(new KamisPriceClient.Result.QueryFailed("read timed out"));

        KamisCollectResponse result = collector(ENABLED_PROPERTIES).collectWithReport(TODAY);

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.missing()).isZero();
        assertThat(result.items()).singleElement()
                .extracting(KamisCollectResponse.Item::status).isEqualTo("QUERY_FAILED");
        verifyNoInteractions(writer);
    }

    @Test
    @DisplayName("저장 실패는 조회 실패와 다른 상태로 남긴다")
    void save_failure_has_its_own_status() {
        KamisItemCodes.ItemCode lettuceCode = new KamisItemCodes.ItemCode("200", "212");
        KamisPriceClient.DailyPrice lettucePrice = new KamisPriceClient.DailyPrice(TODAY, 9500, 4);
        given(itemCodes.all()).willReturn(items(Map.entry("상추", lettuceCode)));
        given(client.fetchLatest(lettuceCode, TODAY)).willReturn(found(lettucePrice));
        willThrow(new IllegalStateException("unique constraint"))
                .given(writer).upsert(
                        eq("상추"), eq(lettucePrice), anyString(), anyString(), anyString(),
                        any(LocalDateTime.class));

        KamisCollectResponse result = collector(ENABLED_PROPERTIES).collectWithReport(TODAY);

        assertThat(result.items()).singleElement()
                .extracting(KamisCollectResponse.Item::status).isEqualTo("SAVE_FAILED");
    }

    @Test
    @DisplayName("수동 수집은 최소 간격 안에 다시 부르면 외부를 부르지 않는다")
    void manual_collection_is_rate_limited() {
        // AtomicBoolean 은 동시 실행만 막는다. 순차 반복 호출은 이 간격이 막는다.
        KamisItemCodes.ItemCode lettuceCode = new KamisItemCodes.ItemCode("200", "212");
        KamisPriceClient.DailyPrice lettucePrice = new KamisPriceClient.DailyPrice(TODAY, 9500, 4);
        given(itemCodes.all()).willReturn(items(Map.entry("상추", lettuceCode)));
        given(client.fetchLatest(lettuceCode, TODAY)).willReturn(found(lettucePrice));
        KamisPriceCollector collector = collector(ENABLED_PROPERTIES.withManualCollect(true, 600));

        assertThat(collector.collectWithReport(TODAY, true).updated()).isEqualTo(1);
        KamisCollectResponse second = collector.collectWithReport(TODAY, true);

        assertThat(second.skipped()).isTrue();
        assertThat(second.skipReason()).isEqualTo(KamisCollectResponse.SKIP_COOLDOWN);
        // 두 번째 호출은 외부를 한 번도 부르지 않아야 한다 — 할당량이 여기서 마른다.
        verify(client).fetchLatest(lettuceCode, TODAY);
    }

    @Test
    @DisplayName("정기 배치는 수동 수집 간격에 걸리지 않는다")
    void the_daily_batch_ignores_the_manual_cooldown() {
        KamisItemCodes.ItemCode lettuceCode = new KamisItemCodes.ItemCode("200", "212");
        KamisPriceClient.DailyPrice lettucePrice = new KamisPriceClient.DailyPrice(TODAY, 9500, 4);
        given(itemCodes.all()).willReturn(items(Map.entry("상추", lettuceCode)));
        given(client.fetchLatest(lettuceCode, TODAY)).willReturn(found(lettucePrice));
        KamisPriceCollector collector = collector(ENABLED_PROPERTIES.withManualCollect(true, 600));

        collector.collectWithReport(TODAY, true);

        assertThat(collector.collectWithReport(TODAY).updated()).isEqualTo(1);
    }

    @Test
    @DisplayName("수동 수집을 꺼 두면 버튼으로는 외부를 부르지 못한다")
    void manual_collection_can_be_switched_off() {
        KamisPriceCollector collector = collector(ENABLED_PROPERTIES.withManualCollect(false, 600));

        KamisCollectResponse result = collector.collectWithReport(TODAY, true);

        assertThat(result.skipped()).isTrue();
        assertThat(result.skipReason()).isEqualTo(KamisCollectResponse.SKIP_DISABLED);
        verifyNoInteractions(client, itemCodes, writer);
    }

    private static KamisPriceClient.Result found(KamisPriceClient.DailyPrice price) {
        return new KamisPriceClient.Result.Found(price);
    }

    private KamisPriceCollector collector(KamisProperties properties) {
        return new KamisPriceCollector(client, itemCodes, writer, properties);
    }

    @SafeVarargs
    private static Map<String, KamisItemCodes.ItemCode> items(
            Map.Entry<String, KamisItemCodes.ItemCode>... entries) {
        Map<String, KamisItemCodes.ItemCode> items = new LinkedHashMap<>();
        for (Map.Entry<String, KamisItemCodes.ItemCode> entry : entries) {
            items.put(entry.getKey(), entry.getValue());
        }
        return items;
    }
}
