# 숲속 보드 친구들 인수인계

## 링크

- 소스 저장소: https://github.com/BrainyJongmin/forest-board-friends
- 가족용 APK: https://github.com/BrainyJongmin/forest-board-friends/releases/latest
- 릴리스 안내: https://github.com/BrainyJongmin/forest-board-friends/releases/latest

## 구현 내용

- 바둑, 장기, 체스, 오목
- 쉬움/보통 1인용 AI와 한 기기에서 번갈아 두는 2인용
- 게임 규칙과 추천 수 힌트
- 첫 실행 이름 입력, 기기 내 이름 저장 및 한국어 음성 호명
- 선택형 온디바이스 LFM AI 코치(최초 약 736MB 다운로드)
- 엄마용 레트로 무한 블록 퍼즐
- 체스 힌트 크래시 방지, 오목/바둑/장기/체스 1인용 판단 강화
- 체스·장기 기물 선택 시 합법적인 이동 위치 표시
- 장기 초 진영 초서체와 한 진영 해서체, 실제 한자 기물 표기
- 승패 애니메이션과 다시 하기/홈으로 선택
- 태블릿·가로 화면 좌우 분할 레이아웃 및 회전 중 게임 유지
- Android 12 이상 지원

## 검증

- 단위 테스트 통과
- Android Lint 오류 0건
- 로컬 디버그·릴리스 빌드 성공
- GitHub Actions 빌드 성공
- GitHub 배포 APK 서명 검증 완료
- 실제 Android 기기가 연결되지 않아 휴대폰 설치 테스트는 아직 하지 않음

## Chess LFM 검토

2026-09-16 기준 공개된 [MostLime/lcm-chess](https://huggingface.co/MostLime/lcm-chess)는 29.2M 크기로 가볍지만, 모델 카드가 단순 전술을 놓치는 한계를 밝히고 있고 실행 환경도 Python/PyTorch 기반입니다. 이번 APK에는 넣지 않고, 체스 규칙은 `chesslib`, 추천 수는 앱 내부 2수 탐색으로 보강했습니다. Android용 검증 런타임이나 공식 변환 모델이 나오면 다시 검토할 수 있습니다.

## 중요 백업

`D:\workbench\game_for_kids\signing` 폴더에는 향후 앱 업데이트에 필요한 릴리스 키와 비밀번호가 있습니다. GitHub에는 올라가지 않으므로 안전한 개인 저장소에 별도로 백업해야 합니다. 이 키를 잃으면 같은 앱으로 업데이트할 수 없습니다.

## 캐릭터 이미지

내장 이미지 생성 도구로 아래 프롬프트를 사용해 제작했습니다.

> four cute woodland animals (fox/bear/rabbit/owl), transparent background, polished 2D children's game illustration

저장 위치: `app/src/main/res/drawable-nodpi/forest_friends.png`
