package com.team2.project.imagegen.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import com.team2.project.imagegen.CaseImageGenClient;
import com.team2.project.imagegen.CaseImagePromptBuilder;
import com.team2.project.imagegen.CaseImageStorage;
import com.team2.project.imagegen.ImageGenException;
import com.team2.project.legalcase.domain.CaseIncidentPhoto;
import com.team2.project.legalcase.domain.LegalCase;
import com.team2.project.legalcase.repository.CaseIncidentPhotoRepository;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * Cloudflare 생성 → S3 업로드 → DB 기록을 묶는 서비스 (BE-91). Cloudflare · S3는 가짜로 대체한다
 */
class CaseImageGenerationServiceTest {

	private FakeImageGenClient imageGenClient;
	private FakeImageStorage imageStorage;
	private CaseIncidentPhotoRepository incidentPhotoRepository;
	private CaseImageGenerationService service;

	@BeforeEach
	void setUp() {
		imageGenClient = new FakeImageGenClient();
		imageStorage = new FakeImageStorage();
		incidentPhotoRepository = Mockito.mock(CaseIncidentPhotoRepository.class);
		service = new CaseImageGenerationService(imageGenClient, imageStorage, new CaseImagePromptBuilder(),
			incidentPhotoRepository);
	}

	@Test
	@DisplayName("대표사진은 생성 · 업로드만 하고 URL을 돌려준다 (DB 기록은 호출하는 쪽의 몫)")
	void generateRepresentativePhoto_returnsUploadedUrl() {
		String url = service.generateRepresentativePhoto(7L, "a dispute over an unpaid loan");

		assertThat(url).isEqualTo("https://bucket.s3.test/case-photos/case-7/representative.jpg");
		assertThat(imageGenClient.lastPrompt).contains("a dispute over an unpaid loan").contains("featureless gray");
	}

	@Test
	@DisplayName("발단 사진은 순번(0부터)을 매겨 저장하고, 쓰인 모델명을 그대로 기록한다")
	void generateIncidentPhoto_assignsDisplayOrderAndModel() {
		Mockito.when(incidentPhotoRepository.countByLegalCaseId(7L)).thenReturn(2);
		Mockito.when(incidentPhotoRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));
		LegalCase legalCase = legalCaseWithId(7L);

		CaseIncidentPhoto photo = service.generateIncidentPhoto(legalCase, "the moment the argument began", "발단 장면");

		ArgumentCaptor<CaseIncidentPhoto> captor = ArgumentCaptor.forClass(CaseIncidentPhoto.class);
		Mockito.verify(incidentPhotoRepository).save(captor.capture());
		assertThat(captor.getValue().getDisplayOrder()).isEqualTo(2);
		assertThat(captor.getValue().getModel()).isEqualTo("@cf/test-model");
		assertThat(captor.getValue().getCaption()).isEqualTo("발단 장면");
		assertThat(photo.getDisplayOrder()).isEqualTo(2);
	}

	@Test
	@DisplayName("Cloudflare가 꺼져 있으면 IMAGE_GENERATION_FAILED로 묶어서 알린다")
	void generationDisabled_wrapsAsBusinessException() {
		imageGenClient.enabled = false;

		assertThatThrownBy(() -> service.generateRepresentativePhoto(7L, "a scene"))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode())
			.isEqualTo(ErrorCode.IMAGE_GENERATION_FAILED);
	}

	@Test
	@DisplayName("S3가 꺼져 있으면 IMAGE_GENERATION_FAILED로 알린다 (Cloudflare는 부르지 않는다)")
	void storageDisabled_doesNotCallCloudflare() {
		imageStorage.enabled = false;

		assertThatThrownBy(() -> service.generateRepresentativePhoto(7L, "a scene"))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode())
			.isEqualTo(ErrorCode.IMAGE_GENERATION_FAILED);
		assertThat(imageGenClient.callCount).isZero();
	}

	@Test
	@DisplayName("Cloudflare 호출 실패(ImageGenException)도 IMAGE_GENERATION_FAILED로 묶는다")
	void generationFailure_wrapsAsBusinessException() {
		imageGenClient.shouldFail = true;

		assertThatThrownBy(() -> service.generateRepresentativePhoto(7L, "a scene"))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode())
			.isEqualTo(ErrorCode.IMAGE_GENERATION_FAILED);
	}

	/** id만 있는 LegalCase를 리플렉션으로 만든다 (JPA 엔티티라 생성자가 없다) */
	private static LegalCase legalCaseWithId(Long id) {
		try {
			var constructor = LegalCase.class.getDeclaredConstructor();
			constructor.setAccessible(true);
			LegalCase legalCase = constructor.newInstance();
			Field idField = LegalCase.class.getDeclaredField("id");
			idField.setAccessible(true);
			idField.set(legalCase, id);
			return legalCase;
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	static class FakeImageGenClient implements CaseImageGenClient {

		boolean enabled = true;
		boolean shouldFail = false;
		int callCount = 0;
		String lastPrompt;

		@Override
		public boolean enabled() {
			return enabled;
		}

		@Override
		public String model() {
			return "@cf/test-model";
		}

		@Override
		public byte[] generateImage(String prompt) {
			callCount++;
			lastPrompt = prompt;
			if (shouldFail) {
				throw new ImageGenException("가짜 실패");
			}
			return new byte[] {1, 2, 3};
		}
	}

	static class FakeImageStorage implements CaseImageStorage {

		boolean enabled = true;
		final List<String> uploadedKeys = new ArrayList<>();

		@Override
		public boolean enabled() {
			return enabled;
		}

		@Override
		public UploadedImage upload(byte[] image, String keySuffix, String contentType) {
			uploadedKeys.add(keySuffix);
			String fixedSuffix = keySuffix.contains("representative") ? "case-7/representative.jpg" : keySuffix;
			return new UploadedImage("case-photos/" + fixedSuffix, "https://bucket.s3.test/case-photos/" + fixedSuffix);
		}
	}
}
