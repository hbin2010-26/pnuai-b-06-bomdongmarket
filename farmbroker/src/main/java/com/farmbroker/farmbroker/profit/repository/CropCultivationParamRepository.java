package com.farmbroker.farmbroker.profit.repository;

import com.farmbroker.farmbroker.profit.domain.CropCultivationParam;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CropCultivationParamRepository extends JpaRepository<CropCultivationParam, Long> {

    Optional<CropCultivationParam> findByCropName(String cropName);

    // 조회 순서를 고정한다 — 작물 선택 목록을 가나다순으로 보여주기로 했고(#98),
    // 순서가 매번 달라지면 같은 화면이 흔들려 보인다.
    List<CropCultivationParam> findAllByOrderByCropNameAsc();
}
