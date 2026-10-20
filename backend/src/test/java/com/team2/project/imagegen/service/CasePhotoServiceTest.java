package com.team2.project.imagegen.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import com.team2.project.common.storage.FileStorage;
import com.team2.project.common.storage.FileStorageException;
import com.team2.project.imagegen.CasePhotoPromptClient;
import com.team2.project.imagegen.ImageGenException;
import com.team2.project.legalcase.domain.CaseIncidentPhoto;
import com.team2.project.legalcase.domain.Factor;
import com.team2.project.legalcase.domain.LegalCase;
import com.team2.project.legalcase.repository.CaseIncidentPhotoRepository;
import com.team2.project.legalcase.repository.FactorRepository;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 사건 사진 한글 프롬프트 추천 · 업로드 (BE-91 재설계). Gemini · S3는 가짜/모의 객체로 대체한다
 */
class CasePhotoServiceTest {

	private static final byte[] JPEG_BYTES = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};
	private static final byte[] PNG_BYTES = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

	private FakePromptClient promptClient;
	private FileStorage fileStorage;
	private CaseIncidentPhotoRepository incidentPhotoRepository;
	private FactorRepository factorRepository;
	private CasePhotoService service;

	@BeforeEach
	void setUp() {
		promptClient = new FakePromptClient();
		fileStorage = mock(FileStorage.class);
		when(fileStorage.enabled()).thenReturn(true);
		incidentPhotoRepository = mock(CaseIncidentPhotoRepository.class);
		factorRepository = mock(FactorRepository.class);
		service = new CasePhotoService(promptClient, fileStorage, incidentPhotoRepository, factorRepository);
	}

	@Test
	@DisplayName("대표사진 프롬프트는 사건 개요 · 죄명 · 그림체 규칙을 지시문에 담아 묻는다")
	void suggestRepresentativePrompt_includesCaseContext() {
		LegalCase legalCase = legalCase(1L, "빌린 돈 문제로 다투다 벌어진 사건", "살인");

		String prompt = service.suggestRepresentativePrompt(legalCase);

		assertThat(prompt).isEqualTo("추천 프롬프트");
		assertThat(promptClient.lastInstruction).contains("빌린 돈 문제로 다투다 벌어진 사건").contains("살인")
			.contains("이목구비가 없는").contains("내로").contains("남불").contains("모자이크");
	}

	@Test
	@DisplayName("발단 사진 프롬프트는 판단 요소(factor)와 힌트를 함께 담는다")
	void suggestIncidentPrompt_includesFactorsAndHint() {
		LegalCase legalCase = legalCase(2L, "사건 개요", "상해");
		when(factorRepository.findAllByCaseId(2L))
			.thenReturn(List.of(factor("돈을 갚지 못해 다퉜다"), factor("흉기를 집어 들었다")));

		String prompt = service.suggestIncidentPrompt(legalCase, "다투는 장면을 강조해줘");

		assertThat(prompt).isEqualTo("추천 프롬프트");
		assertThat(promptClient.lastInstruction).contains("돈을 갚지 못해 다퉜다").contains("흉기를 집어 들었다")
			.contains("다투는 장면을 강조해줘");
	}

	@Test
	@DisplayName("프롬프트 추천이 꺼져 있으면 PROMPT_GENERATION_FAILED로 묶는다")
	void promptDisabled_wrapsAsBusinessException() {
		promptClient.enabled = false;
		LegalCase legalCase = legalCase(1L, "개요", "살인");

		assertThatThrownBy(() -> service.suggestRepresentativePrompt(legalCase)).isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.PROMPT_GENERATION_FAILED);
	}

	@Test
	@DisplayName("대표사진 업로드는 S3에 올리고 URL만 돌려준다")
	void uploadRepresentativePhoto_returnsUploadedUrl() {
		when(fileStorage.upload(any(), any(), any()))
			.thenReturn(new FileStorage.UploadedFile("case-photos/case-1/representative-1.jpg",
				"https://bucket.s3.test/case-photos/case-1/representative-1.jpg"));

		String url = service.uploadRepresentativePhoto(1L, JPEG_BYTES, "image/jpeg");

		assertThat(url).isEqualTo("https://bucket.s3.test/case-photos/case-1/representative-1.jpg");
		ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
		verify(fileStorage).upload(any(), keyCaptor.capture(), org.mockito.ArgumentMatchers.eq("image/jpeg"));
		assertThat(keyCaptor.getValue()).startsWith("case-photos/case-1/representative-").endsWith(".jpg");
	}

	@Test
	@DisplayName("발단 사진 업로드는 순번(0부터)을 매겨 저장한다")
	void uploadIncidentPhoto_assignsDisplayOrder() {
		LegalCase legalCase = legalCase(7L, "개요", "살인");
		when(incidentPhotoRepository.countByLegalCaseId(7L)).thenReturn(2);
		when(fileStorage.upload(any(), any(), any()))
			.thenReturn(new FileStorage.UploadedFile("k", "https://bucket.s3.test/k"));
		when(incidentPhotoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		CaseIncidentPhoto photo = service.uploadIncidentPhoto(legalCase, PNG_BYTES, "image/png", "캡션", "참고 프롬프트");

		ArgumentCaptor<CaseIncidentPhoto> captor = ArgumentCaptor.forClass(CaseIncidentPhoto.class);
		verify(incidentPhotoRepository).save(captor.capture());
		assertThat(captor.getValue().getDisplayOrder()).isEqualTo(2);
		assertThat(captor.getValue().getCaption()).isEqualTo("캡션");
		assertThat(captor.getValue().getPrompt()).isEqualTo("참고 프롬프트");
		assertThat(photo.getDisplayOrder()).isEqualTo(2);
	}

	@Test
	@DisplayName("빈 파일은 S3를 부르지 않고 바로 거절한다")
	void emptyFile_isRejectedBeforeUpload() {
		assertThatThrownBy(() -> service.uploadRepresentativePhoto(1L, new byte[0], "image/jpeg"))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_IMAGE_FILE);
		verifyNoInteractions(fileStorage);
	}

	@Test
	@DisplayName("이미지가 아닌 파일 형식은 거절한다")
	void wrongContentType_isRejected() {
		assertThatThrownBy(() -> service.uploadRepresentativePhoto(1L, new byte[] {1}, "application/pdf"))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_IMAGE_FILE);
		verifyNoInteractions(fileStorage);
	}

	@Test
	@DisplayName("Content-Type을 image/jpeg로 속여도 실제 바이트가 JPEG 시그니처가 아니면 거절한다")
	void contentTypeMismatchesActualBytes_isRejected() {
		assertThatThrownBy(() -> service.uploadRepresentativePhoto(1L, new byte[] {1, 2, 3}, "image/jpeg"))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_IMAGE_FILE);
		verifyNoInteractions(fileStorage);
	}

	@Test
	@DisplayName("S3가 꺼져 있으면 FILE_UPLOAD_FAILED로 알린다")
	void storageDisabled_wrapsAsBusinessException() {
		when(fileStorage.enabled()).thenReturn(false);

		assertThatThrownBy(() -> service.uploadRepresentativePhoto(1L, JPEG_BYTES, "image/jpeg"))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.FILE_UPLOAD_FAILED);
	}

	@Test
	@DisplayName("S3 업로드 실패(FileStorageException)도 FILE_UPLOAD_FAILED로 묶는다")
	void storageFailure_wrapsAsBusinessException() {
		when(fileStorage.upload(any(), any(), any())).thenThrow(new FileStorageException("업로드 실패"));

		assertThatThrownBy(() -> service.uploadRepresentativePhoto(1L, JPEG_BYTES, "image/jpeg"))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.FILE_UPLOAD_FAILED);
	}

	private static LegalCase legalCase(Long id, String overview, String chargeName) {
		try {
			Constructor<LegalCase> constructor = LegalCase.class.getDeclaredConstructor();
			constructor.setAccessible(true);
			LegalCase legalCase = constructor.newInstance();
			setField(legalCase, "id", id);
			setField(legalCase, "overview", overview);
			setField(legalCase, "chargeName", chargeName);
			return legalCase;
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static Factor factor(String label) {
		try {
			Constructor<Factor> constructor = Factor.class.getDeclaredConstructor();
			constructor.setAccessible(true);
			Factor factor = constructor.newInstance();
			setField(factor, "label", label);
			return factor;
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static void setField(Object target, String fieldName, Object value) throws ReflectiveOperationException {
		Field field = target.getClass().getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(target, value);
	}

	static class FakePromptClient implements CasePhotoPromptClient {

		boolean enabled = true;
		String lastInstruction;

		@Override
		public boolean enabled() {
			return enabled;
		}

		@Override
		public String generateText(String instruction) {
			if (!enabled) {
				throw new ImageGenException("꺼져 있습니다");
			}
			lastInstruction = instruction;
			return "추천 프롬프트";
		}
	}
}
