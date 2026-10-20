package com.team2.project.imagegen.service;

import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import com.team2.project.imagegen.CaseImageGenClient;
import com.team2.project.imagegen.CaseImagePromptBuilder;
import com.team2.project.imagegen.CaseImageStorage;
import com.team2.project.imagegen.CaseImageStorage.UploadedImage;
import com.team2.project.imagegen.ImageGenException;
import com.team2.project.legalcase.domain.CaseIncidentPhoto;
import com.team2.project.legalcase.domain.LegalCase;
import com.team2.project.legalcase.repository.CaseIncidentPhotoRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cloudflare Workers AI 사건 사진 생성 → S3 업로드 → DB 기록을 묶는다 (BE-91, COMMON-41).
 *
 * 호출하는 쪽({@code AdminCasePhotoService})이 사건 행을 잠근(`SELECT ... FOR UPDATE`) 트랜잭션 안에서
 * 이 서비스를 부르므로, Cloudflare 호출 · S3 업로드(수십 초) 동안 그 잠금이 그대로 걸려 있다. 관리자 전용 · 저빈도
 * 호출이라 다른 관리자가 같은 사건을 동시에 고치려다 잠깐 기다리는 정도는 감수하기로 했다(결정: `docs/decisions/BE-91.md` 5절).
 * 저장이 실패하면 S3 객체가 고아로 남을 수 있는 것도 감수한다 — 재생성하면 새 키로 다시 올라가고, 고아 객체 정리는 범위 밖이다
 */
@Service
@RequiredArgsConstructor
public class CaseImageGenerationService {

	private static final String JPEG_CONTENT_TYPE = "image/jpeg";

	private final CaseImageGenClient imageGenClient;
	private final CaseImageStorage imageStorage;
	private final CaseImagePromptBuilder promptBuilder;
	private final CaseIncidentPhotoRepository incidentPhotoRepository;

	/** 사건 대표사진(신문용)을 생성해 S3에 올리고 URL만 돌려준다. {@code legalCase.changeThumbnailUrl(...)}은 호출하는 쪽이 한다 */
	public String generateRepresentativePhoto(Long caseId, String caseSummary) {
		String prompt = promptBuilder.buildRepresentativePhoto(caseSummary);
		UploadedImage uploaded = generateAndUpload(prompt, "case-" + caseId + "/representative-" + System.currentTimeMillis() + ".jpg");
		return uploaded.url();
	}

	/** 사건 발단 사진을 생성해 S3에 올리고 DB에 기록한다. 사건마다 여러 장을 순서대로 쌓는다 */
	@Transactional
	public CaseIncidentPhoto generateIncidentPhoto(LegalCase legalCase, String incidentDescription, String caption) {
		String prompt = promptBuilder.buildIncidentPhoto(incidentDescription);
		int nextOrder = incidentPhotoRepository.countByLegalCaseId(legalCase.getId());
		UploadedImage uploaded = generateAndUpload(prompt,
			"case-" + legalCase.getId() + "/incident-" + nextOrder + "-" + System.currentTimeMillis() + ".jpg");
		CaseIncidentPhoto photo = CaseIncidentPhoto.of(legalCase, uploaded.url(), uploaded.key(), caption, prompt,
			imageGenClient.model(), nextOrder);
		return incidentPhotoRepository.save(photo);
	}

	public List<CaseIncidentPhoto> listIncidentPhotos(Long caseId) {
		return incidentPhotoRepository.findByLegalCaseIdOrderByDisplayOrder(caseId);
	}

	private UploadedImage generateAndUpload(String prompt, String keySuffix) {
		if (!imageGenClient.enabled()) {
			throw new BusinessException(ErrorCode.IMAGE_GENERATION_FAILED,
				"사진 생성이 꺼져 있습니다 (Cloudflare 설정 없음)");
		}
		if (!imageStorage.enabled()) {
			throw new BusinessException(ErrorCode.IMAGE_GENERATION_FAILED, "사진 저장이 꺼져 있습니다 (S3 설정 없음)");
		}
		try {
			byte[] image = imageGenClient.generateImage(prompt);
			return imageStorage.upload(image, keySuffix, JPEG_CONTENT_TYPE);
		} catch (ImageGenException e) {
			throw new BusinessException(ErrorCode.IMAGE_GENERATION_FAILED, ErrorCode.IMAGE_GENERATION_FAILED.getMessage());
		}
	}
}
