# 활동지 OCR

활동지 사진 한 장에서 한글을 읽는 Android 앱입니다. 카메라로 찍거나 기기의 사진을 선택하면 원본 미리보기와 편집 가능한 인식 결과가 표시됩니다. 확인한 답안은 복사할 수 있습니다.

## 실행

- Android 9 이상 기기에서 실행할 수 있습니다.
- Android Studio로 이 폴더를 열고 Gradle JDK 17과 Android SDK 34를 사용합니다. 빌드 후 기기에서 Run을 실행합니다.
- 명령줄에서는 JAVA_HOME과 ANDROID_HOME을 설정한 뒤 `gradlew.bat :app:assembleDebug`를 실행합니다. 설치 파일은 `app/build/outputs/apk/debug/app-debug.apk`에 생성됩니다.

한글 인식에는 앱에 포함된 Google ML Kit 한국어 모델을 사용합니다. 첫 앱 빌드에는 의존성을 받기 위한 인터넷 연결이 필요합니다. 앱에서는 OCR 요청에 별도 서버나 API 키가 필요하지 않습니다.

## 기기에서 확인할 항목

1. 비행기 모드를 켠 뒤 갤러리에서 글자가 있는 사진을 선택합니다. 원본 미리보기, 인식 결과, 처리 시간이 나오는지 확인합니다.
2. 답안에 들어간 인식 오류를 수정한 후 복사해 다른 앱에 붙여넣습니다.
3. 카메라로 세로 사진과 가로 사진을 찍어 사진 방향과 OCR 결과를 확인합니다. 촬영이나 사진 선택을 취소했을 때 직전 결과가 유지되는지도 확인합니다.
4. 종이에 여러 사람이 쓴 한글 답안으로, 문항마다 고쳐야 할 글자나 빠진 줄이 얼마나 되는지 확인합니다. 사진 전체에서 글자를 읽으며, 이번 버전에서는 문항별 영역을 지정하지 않습니다.

## 손글씨 인식

전체 사진은 ML Kit로 읽습니다. 미리보기에서 손글씨 한 줄을 드래그한 뒤 **손글씨 읽기**를 누르면 한국어 손글씨로 추가 학습된 PP-OCRv5 모델이 해당 줄을 읽습니다. 두 모델 모두 기기에서 실행됩니다. 줄이 여러 개면 한 줄씩 선택합니다.

손글씨 모델과 글자 사전은 [korean-handwriting-rec](https://github.com/VerifiedIdiot/korean-handwriting-rec)의 [공개 모델 리비전](https://huggingface.co/Rebeccaaaaaaaa/korean-handwriting-rec/tree/7ebe489f4a8880af35ae873258ab3ab993b3ab1f)을 사용합니다. 라이선스는 앱의 모델 파일과 함께 포함합니다.
