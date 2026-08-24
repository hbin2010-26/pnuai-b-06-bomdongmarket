package com.farmbroker.farmbroker.crop.init;

import com.farmbroker.farmbroker.crop.domain.Crop;
import com.farmbroker.farmbroker.crop.domain.CropDataSource;
import com.farmbroker.farmbroker.crop.domain.CropDifficulty;
import com.farmbroker.farmbroker.crop.domain.LightRequirement;
import com.farmbroker.farmbroker.crop.repository.CropRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 예전 로더는 crops 테이블이 비어 있을 때만 넣었다. 그래서 시드에 작물을 더해도 이미 돌고 있는
// 환경에는 영원히 들어가지 않았고, 단가가 없는 작물은 수익 계산에서 조용히 빠진 채로 남았다.
class CropDataInitializerTest {

    private CropRepository repository;
    private CropDataInitializer initializer;

    @BeforeEach
    void setUp() {
        repository = mock(CropRepository.class);
        initializer = new CropDataInitializer(repository);
    }

    private static Crop stored(String name, Integer price, CropDataSource source) {
        return Crop.builder()
                .name(name)
                .category("잎채소")
                .growingPeriodDays(30)
                .difficulty(CropDifficulty.EASY)
                .lightRequirement(LightRequirement.MEDIUM)
                .avgPricePerKg(price)
                .dataSource(source)
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<Crop> capturedSaves() {
        ArgumentCaptor<Iterable<Crop>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(repository).saveAll(captor.capture());
        List<Crop> saved = new java.util.ArrayList<>();
        captor.getValue().forEach(saved::add);
        return saved;
    }

    @Test
    @DisplayName("비어 있으면 시드 전체를 넣는다")
    void seeds_everything_when_empty() {
        when(repository.findAll()).thenReturn(List.of());

        initializer.run(null);

        assertThat(capturedSaves()).hasSameSizeAs(initializer.seedCrops());
    }

    @Test
    @DisplayName("이미 작물이 있어도 시드에 새로 생긴 작물은 넣는다")
    void adds_only_the_crops_that_are_missing() {
        // 애플민트·쪽파·병풀이 없는 옛 DB 상태를 흉내 낸다.
        List<Crop> old = initializer.seedCrops().stream()
                .filter(crop -> !List.of("애플민트", "쪽파", "병풀").contains(crop.getName()))
                .toList();
        when(repository.findAll()).thenReturn(old);

        initializer.run(null);

        assertThat(capturedSaves()).extracting(Crop::getName)
                .containsExactlyInAnyOrder("애플민트", "쪽파", "병풀");
    }

    @Test
    @DisplayName("모두 있으면 아무것도 넣지 않는다")
    void inserts_nothing_when_all_present() {
        when(repository.findAll()).thenReturn(initializer.seedCrops());

        initializer.run(null);

        verify(repository, never()).saveAll(anyIterable());
    }

    // 단가는 수익 계산의 단일 소스라, 시드에서 고친 값이 반영되지 않으면 계산이 코드와 어긋난다.
    @Test
    @DisplayName("시드 단가가 바뀌면 저장된 행의 단가도 맞춘다")
    void syncs_seed_prices_onto_stored_rows() {
        Crop basil = stored("바질", 20000, CropDataSource.SEED);
        List<Crop> all = new java.util.ArrayList<>(initializer.seedCrops().stream()
                .filter(crop -> !crop.getName().equals("바질"))
                .toList());
        all.add(basil);
        when(repository.findAll()).thenReturn(all);

        initializer.run(null);

        assertThat(basil.getAvgPricePerKg()).isEqualTo(25000);
    }

    // 공공 API 로 채운 값을 배포마다 시드값으로 되돌리면 적재한 의미가 없어진다.
    @Test
    @DisplayName("시드가 아닌 출처의 단가는 건드리지 않는다")
    void leaves_non_seed_rows_alone() {
        Crop basil = stored("바질", 31000, CropDataSource.NONGSARO);
        List<Crop> all = new java.util.ArrayList<>(initializer.seedCrops().stream()
                .filter(crop -> !crop.getName().equals("바질"))
                .toList());
        all.add(basil);
        when(repository.findAll()).thenReturn(all);

        initializer.run(null);

        assertThat(basil.getAvgPricePerKg()).isEqualTo(31000);
    }
}
