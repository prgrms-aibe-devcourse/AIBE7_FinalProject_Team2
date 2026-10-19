package com.team2.project.personality.service;

import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import com.team2.project.experience.domain.AnonymousUser;
import com.team2.project.experience.repository.AnonymousUserRepository;
import com.team2.project.experience.service.AnonymousUserService;
import com.team2.project.experience.service.AnonymousUserService.IssuedAnonymousUser;
import com.team2.project.personality.domain.PersonalityAnswer;
import com.team2.project.personality.domain.PersonalityChoice;
import com.team2.project.personality.domain.PersonalityQuestion;
import com.team2.project.personality.domain.PersonalityResult;
import com.team2.project.personality.domain.PersonalityScoring;
import com.team2.project.personality.domain.PersonalityScoring.Pick;
import com.team2.project.personality.domain.PersonalityScoring.Score;
import com.team2.project.personality.domain.PersonalityType;
import com.team2.project.personality.dto.PersonalityQuestionsResponse;
import com.team2.project.personality.dto.PersonalityResultResponse;
import com.team2.project.personality.dto.PersonalitySubmitRequest;
import com.team2.project.personality.dto.PersonalityTypeResponse;
import com.team2.project.personality.repository.PersonalityChoiceRepository;
import com.team2.project.personality.repository.PersonalityQuestionRepository;
import com.team2.project.personality.repository.PersonalityResultRepository;
import com.team2.project.personality.repository.PersonalityTypeRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판결 성향 테스트 (API 명세 9장 P-1 ~ P-4, WBS 7.6.4). 채점은 PersonalityScoring, 저장 형식은 ERD 8장(V15)
 */
@Service
@RequiredArgsConstructor
public class PersonalityService {

	/** share_id 형식 (V15 chk_personality_type_share_id). 형식이 아니면 조회하지 않고 404 (P-4) */
	private static final Pattern SHARE_ID = Pattern.compile("[A-Za-z0-9]{6,16}");

	private final AnonymousUserService anonymousUserService;
	private final AnonymousUserRepository anonymousUserRepository;
	private final PersonalityQuestionRepository questionRepository;
	private final PersonalityChoiceRepository choiceRepository;
	private final PersonalityTypeRepository typeRepository;
	private final PersonalityResultRepository resultRepository;

	/** P-1 문항 목록 (활성 문항, 출제 순서) */
	@Transactional(readOnly = true)
	public PersonalityQuestionsResponse getQuestions() {
		return new PersonalityQuestionsResponse(questionRepository.findAllByActiveTrueOrderByDisplayOrderAsc().stream()
			.map(PersonalityQuestionsResponse.Question::from).toList());
	}

	/**
	 * P-2 응답 제출. 쿠키가 없으면 익명 ID를 발급하고, 재응시면 이전 현재 결과를 이력으로 내린다.
	 * 같은 사용자의 제출은 사용자 행을 잠가 차례로 처리한다 (#124 리뷰)
	 */
	@Transactional
	public SubmitResult submit(Optional<UUID> anonymousId, PersonalitySubmitRequest request) {
		List<PersonalityQuestion> questions = questionRepository.findAllByActiveTrueOrderByDisplayOrderAsc();
		Map<Long, Integer> values = validateAnswers(request, questions);
		Map<Long, Map<Integer, PersonalityChoice>> choices = choiceRepository
			.findAllByQuestion_IdInOrderByQuestion_IdAscDisplayOrderAsc(values.keySet()).stream()
			.collect(Collectors.groupingBy(choice -> choice.getQuestion().getId(),
				Collectors.toMap(PersonalityChoice::getDisplayOrder, choice -> choice)));

		List<Pick> picks = new ArrayList<>(questions.size());
		List<PersonalityAnswer> answers = new ArrayList<>(questions.size());
		for (PersonalityQuestion question : questions) {
			PersonalityChoice choice = choices.getOrDefault(question.getId(), Map.of()).get(values.get(question.getId()) + 1);
			if (choice == null) {
				// 시드가 문항마다 5칸을 넣는다(PersonalityContentSeedTest). 여기 오면 콘텐츠 데이터가 깨진 것이다
				throw new IllegalStateException("선택지가 없는 문항: " + question.getId());
			}
			picks.add(Pick.of(choice));
			answers.add(new PersonalityAnswer(question.getId(), choice.getId()));
		}
		Score score = PersonalityScoring.scoreTest(picks);

		IssuedAnonymousUser issued = anonymousUserService.getOrIssue(anonymousId);
		UUID userId = issued.user().getId();
		anonymousUserRepository.findByIdForUpdate(userId).orElseThrow();
		// clearCurrent는 영속성 컨텍스트를 비운다. 앞에서 읽은 엔티티는 detach되므로 아래에서 다시 잡는다 (#124 리뷰)
		resultRepository.clearCurrent(userId);
		AnonymousUser user = anonymousUserRepository.getReferenceById(userId);
		PersonalityType type = typeRepository.findById(score.typeCode()).orElseThrow(
			() -> new IllegalStateException("유형 시드가 없음: " + score.typeCode()));
		PersonalityResult saved = resultRepository.saveAndFlush(
			PersonalityResult.create(user, type, score.axisScores(), answers));
		return new SubmitResult(PersonalityResultResponse.from(saved), issued.issued() ? userId : null);
	}

	/** P-3 내 현재 결과. 테스트한 적 없으면(쿠키 없음 포함) 404 */
	@Transactional(readOnly = true)
	public PersonalityResultResponse getMine(Optional<UUID> anonymousId) {
		return anonymousUserService.findAndTouch(anonymousId)
			.flatMap(user -> resultRepository.findByAnonymousUser_IdAndCurrentTrue(user.getId()))
			.map(PersonalityResultResponse::from)
			.orElseThrow(() -> new BusinessException(ErrorCode.PERSONALITY_RESULT_NOT_FOUND));
	}

	/** P-4 공유용 유형 설명 (공개). 개인 응답 · 점수는 넣지 않는다 */
	@Transactional(readOnly = true)
	public PersonalityTypeResponse getType(String shareId) {
		return Optional.ofNullable(shareId)
			.filter(value -> SHARE_ID.matcher(value).matches())
			.flatMap(typeRepository::findByShareId)
			.map(PersonalityTypeResponse::from)
			.orElseThrow(() -> new BusinessException(ErrorCode.PERSONALITY_TYPE_NOT_FOUND));
	}

	/** 활성 문항마다 정확히 한 번 답했는지. 빠지거나 겹치거나 모르는 문항이면 400 (성향 산식 2-5) */
	private static Map<Long, Integer> validateAnswers(PersonalitySubmitRequest request,
		List<PersonalityQuestion> questions) {
		Map<Long, Integer> values = new HashMap<>();
		for (PersonalitySubmitRequest.Answer answer : request.answers()) {
			if (values.put(answer.questionId(), answer.value()) != null) {
				throw BusinessException.ofField(ErrorCode.VALIDATION_ERROR, "answers");
			}
		}
		Set<Long> activeIds = questions.stream().map(PersonalityQuestion::getId).collect(Collectors.toSet());
		if (activeIds.isEmpty()) {
			// 사용자 잘못이 아니라 콘텐츠 시드 문제다 → 400이 아니라 500 (#128 리뷰)
			throw new IllegalStateException("활성 성향 문항이 없다");
		}
		if (!values.keySet().equals(activeIds)) {
			throw BusinessException.ofField(ErrorCode.VALIDATION_ERROR, "answers");
		}
		return values;
	}

	/** @param issuedCookieId 익명 ID를 새로 발급했을 때만 값이 있다 (Set-Cookie 대상) */
	public record SubmitResult(PersonalityResultResponse result, UUID issuedCookieId) {
	}
}
