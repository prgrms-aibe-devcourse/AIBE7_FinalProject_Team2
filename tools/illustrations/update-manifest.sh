#!/bin/sh
# 일러스트 목록 파일을 이미지 폴더에서 다시 만든다 (BE-142).
# 백엔드는 이미지를 갖고 있지 않아 목록 파일(backend/src/main/resources/illustration-images.txt)로 총 개수를 센다.
# 이미지를 더하거나 지운 뒤 이 스크립트를 실행해 목록을 갱신한다. 어긋나면 백엔드 테스트(IllustrationCatalogTest)가 실패한다.
# 저장소 기준이다: 가능한 한 DB 기준으로 저장소를 최신화한다. 관리자가 DB(S3)에 사진을 올릴 때 같은 파일을 저장소에도 올리고 이 스크립트로 목록을 갱신하면 DB와 어긋나지 않는다.
# DB 저장값(우선) · 공개 사건만 세는 방식으로 바꾸는 일은 필수가 아닌 후속 이슈(BE-143)다.
# 세는 범위: frontend/public/images/cases · characters 아래의 모든 .webp (루트의 대체 SVG · 배경은 뺀다)
set -eu
root="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$root/frontend/public/images"
find cases characters -type f -name '*.webp' | LC_ALL=C sort > "$root/backend/src/main/resources/illustration-images.txt"
echo "$(wc -l < "$root/backend/src/main/resources/illustration-images.txt" | tr -d ' ')장 기록: backend/src/main/resources/illustration-images.txt"
