package com.team2.project.support;

import com.team2.project.pipeline.PipelineClient;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 테스트에서 쓰는 가짜 파이프라인 클라이언트 (BE-93).
 *
 * <p><b>왜 {@code @MockitoBean}을 쓰지 않는가</b>: {@code @MockitoBean}은 테스트 컨텍스트 캐시 키를
 * 바꿔 <b>Spring 컨텍스트를 하나 더 만든다.</b> 컨텍스트마다 커넥션 풀(기본 10개)이 하나씩 열리는데,
 * CI의 PostgreSQL은 기본 상한이 100이라 컨텍스트가 10개가 되는 순간 뒤에 뜨는 컨텍스트가
 * "FATAL: sorry, too many clients already"로 무너졌다. 그러면 <b>바꾼 적도 없는 테스트가</b>
 * 실행 순서에 따라 깨져 원인을 찾기 어렵다 (PR #232 CI에서 실제로 겪었다).
 *
 * <p>이 설정은 {@code com.team2.project} 아래라 모든 {@code @SpringBootTest}가 똑같이 주워 간다.
 * 그래서 컨텍스트가 갈라지지 않고 하나를 같이 쓴다. 실제 파이프라인 서비스를 부르는 일도 없다
 * (부르면 외부 LLM 호출이 일어나고 수십 분이 걸린다).
 *
 * <p>가짜를 여러 테스트가 나눠 쓰므로 <b>쓰는 쪽에서 {@code Mockito.reset}으로 초기화</b>한다.
 */
@Configuration
public class TestPipelineClientConfig {

	@Bean
	@Primary
	public PipelineClient testPipelineClient() {
		return Mockito.mock(PipelineClient.class);
	}
}
