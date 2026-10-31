package com.team2.project.personality.service;

import com.team2.project.collection.service.CollectionService;
import com.team2.project.common.owner.Owner;
import com.team2.project.common.web.Requester;
import com.team2.project.experience.repository.ExperienceRepository;
import com.team2.project.experience.service.AnonymousUserService;
import com.team2.project.legalcase.domain.ValueAxis;
import com.team2.project.personality.domain.AxisCount;
import com.team2.project.personality.domain.ExperiencePersonality;
import com.team2.project.personality.domain.PersonalityResult;
import com.team2.project.personality.domain.PersonalityScoring;
import com.team2.project.personality.domain.PersonalityScoring.Score;
import com.team2.project.personality.domain.PersonalityType;
import com.team2.project.personality.dto.MyPageResponse;
import com.team2.project.personality.dto.MyPageResponse.BaselineRetake;
import com.team2.project.personality.dto.MyPageResponse.InitialJudgment;
import com.team2.project.personality.dto.MyPageResponse.InitialPersonality;
import com.team2.project.personality.dto.MyPageResponse.InitialTest;
import com.team2.project.personality.dto.MyPageResponse.ExperienceItem;
import com.team2.project.personality.dto.MyPageResponse.HistoryItem;
import com.team2.project.personality.dto.MyPageResponse.JudgmentPersonality;
import com.team2.project.personality.dto.MyPageResponse.Profile;
import com.team2.project.personality.dto.PersonalityResultResponse;
import com.team2.project.personality.dto.PersonalityTypeResponse;
import com.team2.project.personality.repository.ExperiencePersonalityRepository;
import com.team2.project.personality.repository.PersonalityResultRepository;
import com.team2.project.personality.repository.PersonalityTypeRepository;
import com.team2.project.level.service.LevelService;
import com.team2.project.profile.domain.AchievementJudge;
import com.team2.project.profile.domain.ProfileStats;
import com.team2.project.profile.domain.UnlockMark;
import com.team2.project.profile.service.AchievementUnlockRecorder;
import com.team2.project.profile.service.ProfileDecoService;
import com.team2.project.profile.service.ProfileDecoService.ProfileDecoView;
import com.team2.project.profile.service.ProfileSettingsService;
import com.team2.project.profile.service.ProfileStatsReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * P-5 마이페이지 (API 명세 9장). 종합 · 흐름은 각 사건의 첫 회차 체험 기여(experience_personality)로 계산한다(성향 산식 4장).
 * 종합(현재 판결 성향)은 최근 3건(RECENT_COUNT)만 평균한다(API 명세 v0.44, BE-99). 프론트 목(personalityMockApi.js judgmentOf)과 같은 규칙이다.
 * 흐름의 사건별 유형(history[].type)은 그 체험 하나만으로 낸 종합의 유형이다(같은 산식, API 명세 v0.49, BE-100).
 * 처음 성향(초심)은 가장 오래된 응시 행, 테스트 전이면 처음 3건(FIRST_COUNT) 평균이다. 재심은 사용자당 1건(API 명세 v0.50, BE-104)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MyPageService {

	/** 현재 판결 성향에 쓰는 최근 체험 수 */
	private static final int RECENT_COUNT = 3;

	/** 테스트 전 처음 성향(첫 판결)에 쓰는 처음 체험 수 (성향 산식 5장 v0.9) */
	private static final int FIRST_COUNT = 3;

	private final AnonymousUserService anonymousUserService;
	private final PersonalityResultRepository resultRepository;
	private final PersonalityTypeRepository typeRepository;
	private final ExperiencePersonalityRepository contributionRepository;
	private final ExperienceRepository experienceRepository;
	private final ProfileDecoService profileDecoService;
	private final ProfileSettingsService profileSettingsService;
	private final LevelService levelService;
	private final ProfileStatsReader statsReader;
	private final AchievementUnlockRecorder unlockRecorder;
	private final CollectionService collectionService;
	private final PlatformTransactionManager transactionManager;

	/**
	 * 메서드 전체에 트랜잭션을 걸지 않고 단계마다 짧게 연다 (PR #335 리뷰).
	 * 조회 트랜잭션이 연결을 잡은 채 해금 기록(별도 트랜잭션)이 두 번째 연결을 기다리면, 풀(운영 5개)이 모자랄 때
	 * 요청들이 서로 연결을 기다리며 대기 시간만큼 멈춘다. 순서대로 열고 닫아 연결을 한 번에 하나만 쓴다:
	 * 사용자 찾기(접속 시각 갱신) → 통계 읽기 → 해금 기록(있을 때만) → 읽기 전용으로 조회 조립
	 */
	public MyPageResponse get(Requester requester) {
		Optional<UUID> userId = anonymousUserService.findAndTouch(requester.anonymousId()).map(user -> user.getId());
		if (userId.isEmpty()) {
			return MyPageResponse.empty();	// 쿠키가 없거나 모르는 ID면 빈 마이페이지 (404가 아니다)
		}
		UUID id = userId.get();
		// 체험 · 성향은 주인(회원이면 회원) 기준이다(BE-140). 꾸미기 · 통계 · 레벨 등은 다음 단계(3b)에서 회원 키로 옮길 때까지 익명 ID 기준이다
		Owner owner = requester.owner().orElseThrow();
		// 꾸미기 · 도전과제와 레벨이 같은 통계를 쓰므로 한 번만 읽는다
		ProfileStats stats = recordNewUnlocks(id, readOnly(() -> statsReader.read(id)));
		return readOnly(() -> build(id, owner, stats));
	}

	/** 읽기 전용 트랜잭션 하나로 실행한다 (open-in-view가 꺼져 있어 지연 로딩에 트랜잭션이 필요하다) */
	private <T> T readOnly(Supplier<T> work) {
		TransactionTemplate template = new TransactionTemplate(transactionManager);
		template.setReadOnly(true);
		return template.execute(status -> work.get());
	}

	private MyPageResponse build(UUID userId, Owner owner, ProfileStats stats) {
		PersonalityResultResponse personality = resultRepository.findCurrent(owner)
			.map(PersonalityResultResponse::from).orElse(null);
		Optional<PersonalityResult> firstTest = resultRepository.findFirst(owner);
		Optional<PersonalityResultResponse> retake = resultRepository.findBaselineRetake(owner)
			.map(PersonalityResultResponse::from);

		List<ExperiencePersonality> contributions = contributionRepository.findFirstAttempts(owner);
		// 현재 판결 성향은 완료 순서(쿼리의 completedAt, id 순)로 최근 3건만 평균한다. 흐름(history)은 전체를 내려준다
		List<ExperiencePersonality> recent = contributions.subList(
			Math.max(0, contributions.size() - RECENT_COUNT), contributions.size());
		List<Map<ValueAxis, AxisCount>> counts = recent.stream().map(ExperiencePersonality::getAxisCounts).toList();
		Optional<Score> overall = PersonalityScoring.overall(counts);
		// 사건별 유형: 체험 하나만 넣은 종합과 같다(네 축 모두 값이 있을 때만 코드, 글자는 정확한 분수의 부호 · 동점은 관대). BE-100
		List<String> historyCodes = contributions.stream()
			.map(contribution -> PersonalityScoring.overall(List.of(contribution.getAxisCounts()))
				.map(Score::typeCode).orElse(null))
			.toList();
		// 처음 성향: 테스트를 했으면 첫 테스트, 아니면 첫 판결(완료 순서로 처음 3건 평균). 첫 판결 계산은 테스트 전에만 한다
		List<ExperiencePersonality> first = contributions.subList(0, Math.min(FIRST_COUNT, contributions.size()));
		Optional<Score> firstJudgment = firstTest.isPresent() ? Optional.empty()
			: PersonalityScoring.overall(first.stream().map(ExperiencePersonality::getAxisCounts).toList());
		Map<String, PersonalityTypeResponse> types = typesOf(historyCodes, overall.map(Score::typeCode).orElse(null),
			firstJudgment.map(Score::typeCode).orElse(null));
		List<HistoryItem> history = IntStream.range(0, contributions.size())
			.mapToObj(index -> {
				ExperiencePersonality contribution = contributions.get(index);
				return HistoryItem.of(contribution.getExperience(), contribution.getCreatedAt(),
					PersonalityScoring.experienceScores(contribution.getAxisCounts()), typeOf(types, historyCodes.get(index)));
			})
			.toList();
		JudgmentPersonality judgment = overall
			.map(score -> new JudgmentPersonality(score.axisScores(), typeOf(types, score.typeCode()),
				recent.size()))	// 평균에 쓴 체험 수(최대 3). 축 자료가 모두 빈 체험도 센다. API 명세 P-5 v0.44, #128 리뷰
			.orElse(null);

		InitialPersonality initial = firstTest
			.<InitialPersonality>map(result -> InitialTest.from(PersonalityResultResponse.from(result)))
			.orElseGet(() -> firstJudgment
				.filter(score -> score.typeCode() != null)	// 첫 판결은 네 축이 모두 있어 유형이 있을 때만 (P-5 v0.50)
				.map(score -> new InitialJudgment(typeOf(types, score.typeCode()), score.axisScores(), first.size()))
				.orElse(null));
		BaselineRetake baselineRetake = new BaselineRetake(firstTest.isPresent() && retake.isEmpty(), retake.orElse(null));

		List<ExperienceItem> experiences = experienceRepository.findLatestPerCase(owner).stream()
			.map(ExperienceItem::from).toList();
		ProfileDecoView deco = profileDecoService.view(userId, stats);
		return new MyPageResponse(personality, initial, baselineRetake, judgment, history, experiences,
			new Profile(stats.attendanceDays()), deco.profileDeco(), deco.achievements(), deco.unlocked(),
			collectionService.view(userId), profileSettingsService.view(userId), levelService.view(userId, stats));
	}

	/**
	 * 막 달성한 도전과제의 해금을 남기고(BE-83) 같은 응답에 해금 시각 · "새로 열림"으로 내려 준다.
	 * 조회 트랜잭션 밖에서 호출되므로 기록 트랜잭션이 이때 쓰는 유일한 연결이다(get 참고). 새로 남길 것이 있을 때만 연다(대부분의 조회는 없다).
	 * 기록에 실패해도 조회는 그대로 내려 준다 — 판정은 계산값이라 화면은 같고, 다음 조회 때 다시 기록한다
	 */
	private ProfileStats recordNewUnlocks(UUID userId, ProfileStats stats) {
		if (AchievementJudge.unrecordedClears(stats).isEmpty()) {
			return stats;
		}
		try {
			Map<String, UnlockMark> added = unlockRecorder.recordSeparately(userId, stats);
			return added.isEmpty() ? stats : stats.withUnlocks(added);
		} catch (DataAccessException | TransactionException e) {
			// TransactionException: 커넥션 풀이 모자라 새 트랜잭션을 못 열거나(CannotCreateTransactionException) 커밋에 실패한 경우
			log.warn("도전과제 해금 기록 실패 — 다음 조회 때 다시 기록한다: {}", e.getMessage());
			return stats;
		}
	}

	/** 흐름 · 종합 · 첫 판결에 나온 유형 코드를 한 번에 조회한다(사건마다 조회하지 않는다). 코드는 응답에 넣지 않는다(#119) */
	private Map<String, PersonalityTypeResponse> typesOf(Collection<String> historyCodes, String... otherCodes) {
		List<String> all = new ArrayList<>(historyCodes);
		all.addAll(Arrays.asList(otherCodes));	// List.of는 null을 받지 않으므로 asList (코드가 없으면 null)
		Set<String> codes = all.stream().filter(Objects::nonNull).collect(Collectors.toSet());
		if (codes.isEmpty()) {
			return Map.of();
		}
		return typeRepository.findAllById(codes).stream()
			.collect(Collectors.toMap(PersonalityType::getCode, PersonalityTypeResponse::from));
	}

	private static PersonalityTypeResponse typeOf(Map<String, PersonalityTypeResponse> types, String code) {
		return code == null ? null : types.get(code);
	}
}
