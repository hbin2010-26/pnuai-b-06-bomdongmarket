"""계산 블록 8: 재료비 계산."""

from __future__ import annotations


def calculate_material_cost(
    space_result: dict[str, float],
    crop: dict[str, float | str],
    water_result: dict[str, float],
    standard: dict[str, float],
) -> dict[str, float]:
    """재배면적 기준 월 모종비와 작물 관수량 기준 양액비를 합산한다.

    월 환산 모종비는 기존 1회 단가의 1/3이며, 같은 금액을 매월
    반영하면 기존 단가로 연 4회 구입하는 것과 같은 연간 비용이 된다.
    월 양액량은 수도비 단계에서 배액률까지 반영해 계산한 작물 관수량을
    그대로 사용하며, 공실 전체면적 기준 기타 용수는 포함하지 않는다.
    """
    cultivation_area_m2 = float(space_result["cultivation_area_m2"])
    seedling_cost_rate = float(crop["seedling_cost_per_m2_month_krw"])
    seedling_cost = cultivation_area_m2 * seedling_cost_rate

    monthly_nutrient_solution_l = float(
        water_result["monthly_crop_irrigation_l"]
    )
    nutrient_cost_rate = float(standard["nutrient_cost_per_l_krw"])
    nutrient_cost = monthly_nutrient_solution_l * nutrient_cost_rate

    total_material_cost = seedling_cost + nutrient_cost

    return {
        "seedling_cost_per_m2_month_krw": seedling_cost_rate,
        "monthly_seedling_cost_krw": seedling_cost,
        "monthly_nutrient_solution_l": monthly_nutrient_solution_l,
        "nutrient_cost_per_l_krw": nutrient_cost_rate,
        "monthly_nutrient_cost_krw": nutrient_cost,
        "monthly_material_cost_krw": total_material_cost,
    }
