# 숲속 보드 친구들

초등 저학년을 위한 오프라인 Android 보드게임 모음입니다. 바둑, 장기, 체스, 오목과 엄마용 보너스 `블록 퍼즐`을 제공합니다.

## 설치

1. [최신 릴리스](https://github.com/BrainyJongmin/forest-board-friends/releases/latest)에서 APK를 받습니다.
2. Android가 요청하면 다운로드에 사용한 브라우저 또는 파일 앱의 `출처를 알 수 없는 앱 설치`를 허용합니다.
3. APK를 열어 설치합니다. 업데이트도 같은 주소의 새 APK를 기존 앱 위에 설치하면 됩니다.

AI 코치 모델은 선택 사항입니다. 앱의 `AI 코치` 화면에서 Wi-Fi로 약 736MB를 한 번 받으면 이후 대화가 기기 안에서만 처리됩니다. 모델이 없어도 모든 게임과 기본 힌트는 작동합니다.

## 개발 빌드

JDK 17과 Android SDK 37이 필요합니다.

```powershell
./gradlew.bat test lint assembleDebug
```

릴리스 서명에는 `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` 환경 변수를 사용합니다.
처음 생성한 `signing` 폴더는 앱 업데이트에 계속 필요하므로 GitHub에 올리지 말고 안전한 개인 저장소에 백업하세요.

## 개인정보

이름, 게임 기록, 대화는 외부 서버로 전송하지 않습니다. 인터넷 권한은 사용자가 선택한 AI 모델 다운로드에만 사용합니다. 대화 기록은 저장하지 않습니다.

## 라이선스

코드는 Apache-2.0입니다. `chesslib`는 Apache-2.0, LiteRT-LM은 Apache-2.0이며 LFM 모델은 해당 모델 카드의 LFM Open License를 따릅니다. 생성된 숲속 캐릭터 에셋은 이 프로젝트를 위해 새로 제작했습니다.
