package com.team2.project.pipeline;

import com.team2.project.pipeline.config.PipelineProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * 내부 파이프라인 서비스에 HTTP로 붙는다 (BE-93 ↔ COMMON-50).
 *
 * <p>같은 Compose 네트워크 안이라 인증이 없다 — 서비스가 포트를 호스트로 열지 않고 백엔드만 있는
 * 네트워크에 있기 때문이다(deploy/docker-compose.pipeline.yml). 관리자 인증 · CSRF는 백엔드가 한다.
 *
 * <p>재시도하지 않는다. 작업 생성은 사람이 버튼을 누른 한 번이고, 상태 조회는 화면이 2초마다 다시 묻는다
 * — 두 경우 모두 여기서 다시 거는 것보다 실패를 그대로 알리는 쪽이 낫다.
 */
@Slf4j
@Component
public class HttpPipelineClient implements PipelineClient {

	/** case_import.job_id 컬럼 길이 (V24). 넘으면 저장할 수 없다 */
	private static final int MAX_JOB_ID_LENGTH = 32;

	private final RestClient restClient;
	private final PipelineProperties properties;

	@Autowired
	public HttpPipelineClient(@Qualifier("pipelineRestClient") RestClient restClient,
			PipelineProperties properties) {
		this.restClient = restClient;
		this.properties = properties;
	}

	@Override
	public boolean enabled() {
		return properties.enabled();
	}

	@Override
	public PipelineJob createJob(PipelineJobRequest request) {
		requireEnabled();
		try {
			PipelineJob job = restClient.post()
				.uri("/jobs")
				.contentType(MediaType.APPLICATION_JSON)
				.body(request)
				.retrieve()
				.body(PipelineJob.class);
			if (job == null || job.jobId() == null || job.jobId().isBlank()) {
				throw new PipelineException("파이프라인 서비스가 작업 ID를 주지 않았습니다");
			}
			// 작업 ID는 case_import.job_id(varchar 32)에 그대로 들어간다. 길면 저장할 때 터지는데,
			// 그 시점에는 이미 파이프라인이 돌고 있어 되돌리기 어렵다 — 부르기 전에 막는다
			if (job.jobId().length() > MAX_JOB_ID_LENGTH) {
				throw new PipelineException("파이프라인 작업 ID가 너무 깁니다 (" + job.jobId().length() + "자)");
			}
			return job;
		} catch (RestClientResponseException e) {
			throw translate(e, "작업 생성");
		} catch (RestClientException e) {
			// 원문이 들어간 요청 본문은 로그에 남기지 않는다.
			// 타임아웃이면 파이프라인은 작업을 만들어 돌고 있을 수 있다 — 그때는 백엔드에 행이 없는데도
			// 다음 등록이 파이프라인 쪽 잠금에 걸린다. 증상을 알아볼 수 있게 함께 적는다
			// (docs/decisions/BE-93.md "작업 생성이 타임아웃되면 고아 작업이 남는다")
			log.warn("파이프라인 서비스에 닿지 못했습니다 (작업 생성): {}. 타임아웃이었다면 파이프라인에"
				+ " 작업이 남아 다음 등록이 IMPORT_IN_PROGRESS가 될 수 있다 — pipeline 컨테이너를 확인한다",
				e.toString());
			throw new PipelineException("파이프라인 서비스에 닿지 못했습니다", e);
		}
	}

	@Override
	public PipelineJob getJob(String jobId) {
		requireEnabled();
		try {
			PipelineJob job = restClient.get().uri("/jobs/{jobId}", jobId).retrieve().body(PipelineJob.class);
			if (job == null) {
				throw new PipelineException("파이프라인 서비스가 빈 응답을 보냈습니다");
			}
			return job;
		} catch (RestClientResponseException e) {
			throw translate(e, "상태 조회");
		} catch (RestClientException e) {
			log.warn("파이프라인 서비스에 닿지 못했습니다 (상태 조회 jobId={}): {}", jobId, e.toString());
			throw new PipelineException("파이프라인 서비스에 닿지 못했습니다", e);
		}
	}

	@Override
	public void deleteJobQuietly(String jobId) {
		if (!enabled() || jobId == null || jobId.isBlank()) {
			return;
		}
		try {
			restClient.delete().uri("/jobs/{jobId}", jobId).retrieve().toBodilessEntity();
		} catch (RestClientException e) {
			// 정리 실패는 등록 흐름을 막지 않는다. 남은 작업은 COMMON-51이 치운다
			log.warn("파이프라인 작업을 지우지 못했습니다 (jobId={}): {}", jobId, e.toString());
		}
	}

	private void requireEnabled() {
		if (!enabled()) {
			throw new PipelineException("사건 등록이 꺼져 있습니다 (PIPELINE_SERVICE_URL 없음)");
		}
	}

	/**
	 * 서비스가 준 상태 코드를 뜻이 있는 예외로 바꾼다.
	 *
	 * <p><b>응답 본문을 로그에 남기지 않는다.</b> 작업 생성 요청의 본문이 판결문 원문이라, 서비스(FastAPI)가
	 * 요청 값을 되비추는 응답(예: 422 검증 오류의 `input` 필드)을 주면 원문이 그대로 로그에 남는다
	 * (PR 리뷰 지적, 요구사항 FR-5-3). 상태 코드만으로도 원인을 찾을 수 있고, 자세한 내용은 파이프라인
	 * 컨테이너 로그에 있다.
	 */
	private PipelineException translate(RestClientResponseException e, String what) {
		int status = e.getStatusCode().value();
		log.warn("파이프라인 서비스 오류 ({}): status={} (본문은 남기지 않는다 — 원문이 들어갈 수 있다. "
			+ "자세한 내용은 pipeline 컨테이너 로그)", what, status);
		if (status == HttpStatus.CONFLICT.value()) {
			return new PipelineException.Conflict("파이프라인이 다른 작업을 돌리는 중입니다");
		}
		if (status == HttpStatus.NOT_FOUND.value()) {
			return new PipelineException.NotFound("파이프라인에 그 작업이 없습니다");
		}
		// 원인 예외를 달지 않는다: RestClientResponseException의 메시지에는 응답 본문 일부가 들어 있어,
		// 어딘가에서 스택트레이스를 통째로 찍으면 원문이 새어 나간다
		return new PipelineException("파이프라인 서비스 오류 (" + status + ")");
	}
}
