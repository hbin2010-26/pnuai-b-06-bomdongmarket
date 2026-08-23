package com.farmbroker.farmbroker;

import com.farmbroker.farmbroker.common.config.ApplicationTimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

// JPA Auditing(@CreatedDate 자동 채움)은 common.config.JpaAuditingConfig 에서 활성화한다.
// (@WebMvcTest 등 웹 슬라이스 테스트에서 JPA metamodel 오류가 나지 않도록 메인 클래스에서 분리)
// KAMIS 시세는 요청마다 부르지 않고 하루 한 번 수집해 스냅샷으로 읽는다 — @EnableScheduling 필요.
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class FarmbrokerApplication {

	public static void main(String[] args) {
		// run() 전에 JVM 기본 시간대를 KST로 고정한다 — 감사 시각(@CreatedDate)과
		// 서비스의 LocalDateTime.now()가 배포 환경(Render 포함)과 무관하게 KST로 쌓이도록. (issue #71)
		ApplicationTimeZone.apply();
		SpringApplication.run(FarmbrokerApplication.class, args);
	}
}
