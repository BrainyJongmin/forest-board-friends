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
- Android 12 이상 지원

## 검증

- 단위 테스트 통과
- Android Lint 오류 0건
- 로컬 디버그·릴리스 빌드 성공
- GitHub Actions 빌드 성공
- GitHub 배포 APK 서명 검증 완료
- 실제 Android 기기가 연결되지 않아 휴대폰 설치 테스트는 아직 하지 않음

## 중요 백업

`D:\workbench\game_for_kids\signing` 폴더에는 향후 앱 업데이트에 필요한 릴리스 키와 비밀번호가 있습니다. GitHub에는 올라가지 않으므로 안전한 개인 저장소에 별도로 백업해야 합니다. 이 키를 잃으면 같은 앱으로 업데이트할 수 없습니다.

## 캐릭터 이미지

내장 이미지 생성 도구로 아래 프롬프트를 사용해 제작했습니다.

> four cute woodland animals (fox/bear/rabbit/owl), transparent background, polished 2D children's game illustration

저장 위치: `app/src/main/res/drawable-nodpi/forest_friends.png`
