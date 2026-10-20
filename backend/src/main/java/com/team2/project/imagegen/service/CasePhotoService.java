package com.team2.project.imagegen.service;

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
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사건 사진 한글 프롬프트 추천 · 업로드 · DB 기록 (BE-91 재설계, COMMON-41).
 *
 * Cloudflare Workers AI 직접 생성은 그림 품질이 기대에 못 미쳐 포기했다(결정: docs/decisions/BE-91.md).
 * 사진은 사람이 만들어 업로드하고, 여기서는 ① 사건 내용을 바탕으로 한글 프롬프트를 추천하고 ② 업로드된
 * 사진을 S3에 저장 · DB에 기록하는 일만 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CasePhotoService {

	private static final int MAX_CAPTION_LENGTH = 200;

	/** 모든 프롬프트 추천에 공통으로 붙이는 그림체 · 등장인물 · 수위 규칙 (COMMON-41 결정) */
	private static final String STYLE_GUIDE = "등장인물은 눈 · 코 · 입 등 이목구비가 없는 무표정한 회색 사람(엑스트라)으로"
		+ " 그려 달라고 해줘. 내로(고양이 마스코트) · 남불(불꽃 마스코트) 같은 특정 캐릭터나 동물 마스코트는 절대"
		+ " 넣지 말라고 해줘. 따뜻한 베이지 · 크림색 톤의 플랫 일러스트 스타일, 종이 질감 배경, 부드러운 조명,"
		+ " 동화책 같은 분위기로 그려 달라고 해줘. 글자 · 로고는 넣지 말라고 해줘."
		+ " 피 · 상처 같은 자극적인 묘사는 넣지 말고, 흉기 등 위험 요소를 꼭 그려야 하면 실루엣이나 모자이크 처리로"
		+ " 뭉뚱그려 표현해 직접적으로 드러나지 않게 해 달라고 해줘.";

	private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

	private final CasePhotoPromptClient promptClient;
	private final FileStorage fileStorage;
	private final CaseIncidentPhotoRepository incidentPhotoRepository;
	private final FactorRepository factorRepository;

	/** 신문 지면에 쓸 사건 대표사진 프롬프트 추천 */
	public String suggestRepresentativePrompt(LegalCase legalCase) {
		String instruction = "다음 사건을 신문 지면에 실을 대표사진으로 그리려고 해. 이 사건 내용을 참고해서,"
			+ " 그림을 그릴 사람에게 줄 한글 이미지 생성 프롬프트를 한 문단으로 작성해줘. 사건 당사자의 실명 ·"
			+ " 지역 등 구체적인 신상은 넣지 말고, 상황만 묘사해줘.\n\n"
			+ "사건 요약: " + legalCase.getOverview() + "\n"
			+ "죄명: " + legalCase.getChargeName() + "\n\n"
			+ STYLE_GUIDE;
		return suggest(instruction);
	}

	/** 사건 발단(사건이 시작된 순간) 사진 프롬프트 추천. hint로 특정 장면을 더 구체적으로 짚을 수 있다 */
	public String suggestIncidentPrompt(LegalCase legalCase, String hint) {
		String detail = factorRepository.findAllByCaseId(legalCase.getId()).stream()
			.map(Factor::getLabel)
			.collect(Collectors.joining(" / "));
		StringBuilder instruction = new StringBuilder()
			.append("다음 사건이 시작된 발단 장면을 그리려고 해. 이 사건 내용을 참고해서, 그림을 그릴 사람에게 줄")
			.append(" 한글 이미지 생성 프롬프트를 한 문단으로 작성해줘. 사건 당사자의 실명 · 지역 등 구체적인 신상은")
			.append(" 넣지 말고, 상황만 묘사해줘.\n\n")
			.append("사건 요약: ").append(legalCase.getOverview()).append('\n');
		if (!detail.isBlank()) {
			instruction.append("세부 사정: ").append(detail).append('\n');
		}
		if (hint != null && !hint.isBlank()) {
			instruction.append("참고할 점: ").append(hint).append('\n');
		}
		instruction.append('\n').append(STYLE_GUIDE);
		return suggest(instruction.toString());
	}

	/** 사건 대표사진 업로드. legal_case.thumbnail_url에 넣을 URL만 돌려준다(반영은 호출하는 쪽의 몫) */
	public String uploadRepresentativePhoto(Long caseId, byte[] image, String contentType) {
		validateImage(image, contentType);
		String key = "case-photos/case-" + caseId + "/representative-" + System.currentTimeMillis()
			+ extensionFor(contentType);
		return upload(image, key, contentType).url();
	}

	/** 사건 발단 사진 업로드 · DB 기록. 사건마다 순서대로 쌓인다 */
	@Transactional
	public CaseIncidentPhoto uploadIncidentPhoto(LegalCase legalCase, byte[] image, String contentType, String caption,
			String promptUsed) {
		validateImage(image, contentType);
		validateCaption(caption);
		int nextOrder = incidentPhotoRepository.countByLegalCaseId(legalCase.getId());
		String key = "case-photos/case-" + legalCase.getId() + "/incident-" + nextOrder + "-"
			+ System.currentTimeMillis() + extensionFor(contentType);
		FileStorage.UploadedFile uploaded = upload(image, key, contentType);
		CaseIncidentPhoto photo = CaseIncidentPhoto.of(legalCase, uploaded.url(), uploaded.key(), caption, promptUsed,
			nextOrder);
		return incidentPhotoRepository.save(photo);
	}

	public List<CaseIncidentPhoto> listIncidentPhotos(Long caseId) {
		return incidentPhotoRepository.findByLegalCaseIdOrderByDisplayOrder(caseId);
	}

	private String suggest(String instruction) {
		if (!promptClient.enabled()) {
			throw new BusinessException(ErrorCode.PROMPT_GENERATION_FAILED, "프롬프트 추천이 꺼져 있습니다 (GEMINI_API_KEY 없음)");
		}
		try {
			return promptClient.generateText(instruction);
		} catch (ImageGenException e) {
			log.warn("사건 사진 프롬프트 추천 실패", e);
			throw new BusinessException(ErrorCode.PROMPT_GENERATION_FAILED, ErrorCode.PROMPT_GENERATION_FAILED.getMessage());
		}
	}

	private FileStorage.UploadedFile upload(byte[] image, String key, String contentType) {
		if (!fileStorage.enabled()) {
			throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "사진 저장이 꺼져 있습니다 (S3 설정 없음)");
		}
		try {
			return fileStorage.upload(image, key, contentType);
		} catch (FileStorageException e) {
			log.warn("사건 사진 S3 업로드 실패 key={}", key, e);
			throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, ErrorCode.FILE_UPLOAD_FAILED.getMessage());
		}
	}

	private static void validateImage(byte[] image, String contentType) {
		if (image == null || image.length == 0) {
			throw BusinessException.ofField(ErrorCode.INVALID_IMAGE_FILE, "file");
		}
		if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(java.util.Locale.ROOT))) {
			throw BusinessException.ofField(ErrorCode.INVALID_IMAGE_FILE, "file");
		}
		if (!matchesSignature(image, contentType.toLowerCase(java.util.Locale.ROOT))) {
			throw BusinessException.ofField(ErrorCode.INVALID_IMAGE_FILE, "file");
		}
	}

	/** Content-Type은 클라이언트가 임의로 선언할 수 있으므로, 실제 파일 시그니처(매직 바이트)도 확인한다 */
	private static boolean matchesSignature(byte[] image, String contentType) {
		return switch (contentType) {
			case "image/jpeg" -> startsWith(image, (byte) 0xFF, (byte) 0xD8, (byte) 0xFF);
			case "image/png" -> startsWith(image, (byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47, (byte) 0x0D,
				(byte) 0x0A, (byte) 0x1A, (byte) 0x0A);
			case "image/webp" -> startsWith(image, (byte) 'R', (byte) 'I', (byte) 'F', (byte) 'F')
				&& image.length >= 12 && startsWith(image, 8, (byte) 'W', (byte) 'E', (byte) 'B', (byte) 'P');
			default -> false;
		};
	}

	private static boolean startsWith(byte[] data, byte... prefix) {
		return startsWith(data, 0, prefix);
	}

	private static boolean startsWith(byte[] data, int offset, byte... prefix) {
		if (data.length < offset + prefix.length) {
			return false;
		}
		for (int i = 0; i < prefix.length; i++) {
			if (data[offset + i] != prefix[i]) {
				return false;
			}
		}
		return true;
	}

	private static void validateCaption(String caption) {
		if (caption != null && caption.length() > MAX_CAPTION_LENGTH) {
			throw BusinessException.ofField(ErrorCode.VALIDATION_ERROR, "caption");
		}
	}

	private static String extensionFor(String contentType) {
		return switch (contentType.toLowerCase(java.util.Locale.ROOT)) {
			case "image/png" -> ".png";
			case "image/webp" -> ".webp";
			default -> ".jpg";
		};
	}
}
