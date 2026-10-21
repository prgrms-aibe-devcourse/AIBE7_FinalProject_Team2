package com.team2.project.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.team2.project.pipeline.config.PipelineProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class HttpPipelineClientTest {

	private static final String BASE = "http://pipeline.test:8000";

	private MockRestServiceServer server;
	private RestClient restClient;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
		server = MockRestServiceServer.bindTo(builder).build();
		restClient = builder.build();
	}

	private HttpPipelineClient client() {
		return client(BASE);
	}

	private HttpPipelineClient client(String baseUrl) {
		return new HttpPipelineClient(restClient,
			new PipelineProperties(baseUrl, List.of("gemini:test"), null, Duration.ofSeconds(30)));
	}

	private static PipelineJobRequest request() {
		return new PipelineJobRequest(List.of(new PipelineJobRequest.Source("판결문 본문", null)),
			List.of("gemini:test"), null, null);
	}

	@Test
	@DisplayName("작업을 만들면 파이프라인이 준 작업 ID와 단계 상태를 그대로 돌려준다")
	void createJob_returnsJob() {
		server.expect(requestTo(BASE + "/jobs"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(jsonPath("$.sources[0].originalText").value("판결문 본문"))
			.andExpect(jsonPath("$.models[0]").value("gemini:test"))
			.andRespond(withStatus(HttpStatus.ACCEPTED).contentType(MediaType.APPLICATION_JSON)
				.body("""
					{"jobId":"abc123","status":"RUNNING","stages":[{"name":"extract","status":"PENDING"}],
					 "error":null,"createdAt":"2026-10-21T12:00:00+00:00","startedAt":null,"finishedAt":null}"""));

		PipelineJob job = client().createJob(request());

		assertThat(job.jobId()).isEqualTo("abc123");
		assertThat(job.status()).isEqualTo("RUNNING");
		assertThat(job.stages()).singleElement()
			.satisfies(stage -> assertThat(stage.name()).isEqualTo("extract"));
		server.verify();
	}

	@Test
	@DisplayName("파이프라인이 모르는 필드를 더 보내도 깨지지 않는다 (서비스가 응답을 늘릴 수 있다)")
	void createJob_ignoresUnknownFields() {
		server.expect(requestTo(BASE + "/jobs")).andRespond(withStatus(HttpStatus.ACCEPTED)
			.contentType(MediaType.APPLICATION_JSON)
			.body("{\"jobId\":\"abc123\",\"status\":\"QUEUED\",\"stages\":[],\"newField\":123}"));

		assertThat(client().createJob(request()).jobId()).isEqualTo("abc123");
		server.verify();
	}

	@Test
	@DisplayName("이미 도는 작업이 있으면(409) Conflict로 구분해 던진다 — A-14가 IMPORT_IN_PROGRESS로 바꾼다")
	void createJob_conflict() {
		server.expect(requestTo(BASE + "/jobs"))
			.andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
				.body("{\"detail\":\"이미 실행 중인 작업이 있습니다\"}"));

		assertThatThrownBy(() -> client().createJob(request()))
			.isInstanceOf(PipelineException.Conflict.class);
		server.verify();
	}

	@Test
	@DisplayName("작업 ID가 없는 응답은 실패로 본다 (추적할 수 없는 작업을 만들지 않는다)")
	void createJob_withoutJobId_fails() {
		server.expect(requestTo(BASE + "/jobs")).andRespond(withStatus(HttpStatus.ACCEPTED)
			.contentType(MediaType.APPLICATION_JSON).body("{\"status\":\"QUEUED\"}"));

		assertThatThrownBy(() -> client().createJob(request())).isInstanceOf(PipelineException.class);
		server.verify();
	}

	@Test
	@DisplayName("서비스 오류(500)는 PipelineException으로 바꾼다")
	void createJob_serverError() {
		server.expect(requestTo(BASE + "/jobs")).andRespond(withServerError());

		assertThatThrownBy(() -> client().createJob(request()))
			.isInstanceOf(PipelineException.class)
			.hasMessageContaining("500");
		server.verify();
	}

	@Test
	@DisplayName("서비스가 요청 값을 되비추어도(422) 판결문 원문이 예외에 묻어 나오지 않는다")
	void createJob_validationErrorBody_doesNotLeakJudgmentText() {
		String judgment = "피고인은 2099. 1. 1. 피해자를 ... (판결문 원문)";
		// FastAPI 기본 422 응답은 문제가 된 입력값(input)을 그대로 되돌려준다
		server.expect(requestTo(BASE + "/jobs")).andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
			.contentType(MediaType.APPLICATION_JSON)
			.body("{\"detail\":[{\"loc\":[\"body\",\"sources\",0],\"msg\":\"field required\",\"input\":\""
				+ judgment + "\"}]}"));

		assertThatThrownBy(() -> client().createJob(
			new PipelineJobRequest(List.of(new PipelineJobRequest.Source(judgment, null)),
				List.of("gemini:test"), null, null)))
			.isInstanceOf(PipelineException.class)
			.hasMessageNotContaining(judgment)
			// 원인 예외의 메시지에도 본문이 들어 있어 아예 달지 않는다
			.hasNoCause();
		server.verify();
	}

	@Test
	@DisplayName("작업 ID가 컬럼 길이를 넘으면 받지 않는다 (저장할 때 터지면 되돌리기 어렵다)")
	void createJob_tooLongJobId_fails() {
		server.expect(requestTo(BASE + "/jobs")).andRespond(withStatus(HttpStatus.ACCEPTED)
			.contentType(MediaType.APPLICATION_JSON)
			.body("{\"jobId\":\"" + "a".repeat(33) + "\",\"status\":\"QUEUED\",\"stages\":[]}"));

		assertThatThrownBy(() -> client().createJob(request()))
			.isInstanceOf(PipelineException.class)
			.hasMessageContaining("너무 깁니다");
		server.verify();
	}

	@Test
	@DisplayName("서비스에 닿지 못하면(연결 실패) PipelineException으로 바꾼다")
	void createJob_connectionFailure() {
		server.expect(requestTo(BASE + "/jobs"))
			.andRespond(request -> { throw new java.io.IOException("연결 거부"); });

		assertThatThrownBy(() -> client().createJob(request()))
			.isInstanceOf(PipelineException.class)
			.hasMessageContaining("닿지 못했습니다");
		server.verify();
	}

	@Test
	@DisplayName("없는 작업(404)은 NotFound로 구분해 던진다")
	void getJob_notFound() {
		server.expect(requestTo(BASE + "/jobs/abc123"))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withStatus(HttpStatus.NOT_FOUND));

		assertThatThrownBy(() -> client().getJob("abc123"))
			.isInstanceOf(PipelineException.NotFound.class);
		server.verify();
	}

	@Test
	@DisplayName("상태 조회는 단계별 상태를 그대로 옮긴다 (SKIPPED 포함)")
	void getJob_returnsStages() {
		server.expect(requestTo(BASE + "/jobs/abc123"))
			.andRespond(withSuccess("""
				{"jobId":"abc123","status":"RUNNING","stages":[
				  {"name":"extract","status":"DONE"},{"name":"rag","status":"SKIPPED"}],
				 "error":null,"finishedAt":null}""", MediaType.APPLICATION_JSON));

		PipelineJob job = client().getJob("abc123");

		assertThat(job.stages()).extracting(PipelineJob.Stage::status).containsExactly("DONE", "SKIPPED");
		server.verify();
	}

	@Test
	@DisplayName("주소가 없으면 꺼진 것으로 보고, 부르면 바로 실패한다 (서버는 정상으로 뜬다)")
	void disabledWithoutBaseUrl() {
		HttpPipelineClient disabled = client("");

		assertThat(disabled.enabled()).isFalse();
		assertThatThrownBy(() -> disabled.createJob(request()))
			.isInstanceOf(PipelineException.class)
			.hasMessageContaining("PIPELINE_SERVICE_URL");
	}

	@Test
	@DisplayName("작업 삭제가 실패해도 예외를 던지지 않는다 (정리 실패가 등록 흐름을 막지 않는다)")
	void deleteJobQuietly_swallowsFailure() {
		server.expect(requestTo(BASE + "/jobs/abc123"))
			.andExpect(method(HttpMethod.DELETE))
			.andRespond(withStatus(HttpStatus.CONFLICT));

		client().deleteJobQuietly("abc123");	// 예외 없음

		server.verify();
	}

	@Test
	@DisplayName("꺼져 있으면 삭제는 호출조차 하지 않는다")
	void deleteJobQuietly_whenDisabled_doesNotCall() {
		client("").deleteJobQuietly("abc123");

		server.verify();	// 기대한 요청이 없다 = 아무 호출도 없었다
	}
}
