# 예시 (판단 요소 작성 방식 참고용, BE-53)

아래는 **판단 요소(`factors`)를 쓰는 방식**만 보여 주는 가상 예시다. 지어낸 상해 사건이고, 실제 판결문이 아니다.
예시의 사건 · 숫자 · 요약어를 이 사건에 옮기지 않는다. 이 사건의 판결문에 있는 사실로만 쓴다.
실제 답에서는 위 형식의 **모든 칸**을 채우고, 판단 요소는 8 ~ 15개를 쓴다. 예시는 판단 요소 7개만 보인다.

## 예시 입력 (가상 판결문 발췌)

<judgment>
피고인은 2024. 3. 2. 23:10경 [주소]에 있는 주점에서 피해자 B와 술값 문제로 말다툼을 하다가 화가 나 테이블 위에 있던 맥주병으로 피해자의 머리를 1회 내리쳐 피해자에게 약 6주간의 치료가 필요한 두피 열상 등을 가하였다.
변호인은 피고인이 술에 취해 우발적으로 범행에 이르렀다고 주장한다.
양형의 이유
피고인이 위험한 물건인 맥주병으로 피해자의 머리를 내리쳐 상해를 가한 것으로 범행 방법이 위험하고, 피해자가 입은 상해의 정도도 가볍지 않다.
피고인은 동종 범행으로 벌금형을 받은 전력이 있다.
다만 피고인이 수사기관에서부터 범행을 모두 인정하고 있는 점, 피해자를 위하여 300만 원을 공탁한 점, 피해자가 먼저 피고인의 멱살을 잡은 사정이 범행에 이른 경위에 일부 작용한 것으로 보이는 점은 피고인에게 유리한 정상이다.
</judgment>

## 예시 출력 (`factors`만)

{
  "factors": [
    { "label": "술값 문제로 말다툼하다 맥주병으로 피해자의 머리를 내리쳤다", "preLabel": "말다툼 끝에 병으로 머리를 내리쳤다", "revealStage": "OVERVIEW", "summaryTag": "병으로 머리 가격", "valueAxis": "FAULT_STANDARD" },
    { "label": "피해자가 6주간 치료가 필요한 머리 상처를 입었다", "preLabel": "피해자가 6주 치료가 필요한 상처를 입었다", "revealStage": "OVERVIEW", "summaryTag": "6주 치료 상해", "valueAxis": "FAULT_STANDARD" },
    { "label": "피해자를 위해 300만 원을 공탁했다", "preLabel": null, "revealStage": "DETAIL", "summaryTag": "300만 원 공탁", "valueAxis": "PRINCIPLE_RELATION" },
    { "label": "같은 종류의 범행으로 벌금형을 받은 전력이 있다", "preLabel": null, "revealStage": "DETAIL", "summaryTag": "동종 벌금 전과", "valueAxis": "ORDER_OPPORTUNITY" },
    { "label": "수사 단계부터 범행을 모두 인정했다", "preLabel": null, "revealStage": "DETAIL", "summaryTag": "수사 초기 범행 인정", "valueAxis": "APOLOGY_SINCERITY" },
    { "label": "피해자가 먼저 A씨의 멱살을 잡았다", "preLabel": null, "revealStage": "DETAIL", "summaryTag": "피해자 선행 멱살잡이", "valueAxis": "PRINCIPLE_RELATION" },
    { "label": "술에 취해 우발적으로 범행했다는 주장이 있다", "preLabel": null, "revealStage": "ARGUMENT", "summaryTag": "우발적 범행 주장", "valueAxis": "FAULT_STANDARD" }
  ]
}

## 예시에서 볼 점

- `label`은 사실 한 문장이다. 재판부의 평가("범행 방법이 위험하고", "유리한 정상이다")는 빼고 사실만 쓴다. 숫자(6주 · 300만 원)는 양형에 쓰이므로 그대로 둔다.
- 재판부가 양형 이유에서 인정한 사정(피해자가 먼저 멱살을 잡음)은 사실로 쓰고, 한쪽 주장에만 나온 내용(우발적 범행)은 "~라는 주장이 있다"로 쓰고 `ARGUMENT`로 둔다.
- `summaryTag`는 그 요소의 내용이 드러나는 말이다. "피해 정도" · "양형 사정"처럼 여러 요소를 묶는 분류명으로 쓰지 않는다.
- `preLabel`은 `OVERVIEW` 요소에만 있고, 나머지는 null이다.
- 정확한 날짜 · 시각(2024. 3. 2. 23:10경)은 판단 요소에 쓰지 않는다(필요하면 "사건 당일 밤"처럼 상대 표현). 미리 가린 표시(`[주소]`)와 판결문 가명 B는 결과에 옮기지 않고, 피고인은 "A씨"로 쓴다.
