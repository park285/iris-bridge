# iris-bridge

카카오톡 안드로이드 프로세스 내부에서 동작하는 LSPosed/Xposed 브릿지 모듈입니다.

Iris Rust 런타임([park285/Iris](file:///home/kapu/work/iris-stack/Iris))이 메시지 전송(텍스트, 마크다운, 이미지) 및 채팅방 열기(Open), 스냅샷 조회, 브릿지 상태 진단을 카카오톡 앱에 위임할 때 해당 요청을 처리하는 서버(서브 시스템) 역할을 담당합니다. 본래 코어 저장소와 단일 리포지토리로 구성되어 있었으나, LSPosed 분리 프로젝트의 일환으로 현재의 독립된 저장소로 분할되었습니다.

Iris 코어 런타임은 `IRIS_REPLY_TRANSPORT=bridge`로 설정된 경우에만 이 브릿지에 연결합니다. `intent` 또는 `none` 전송 방식을 사용할 경우 브릿지가 불필요하며, 코어 런타임 단독으로 동작합니다.

---

## 아키텍처 및 통신 구조 (Architecture)

```text
Iris Rust 런타임 (park285/Iris, native/iris-runtime/src/bridge/ = CLIENT)
        │
        │  @iris-image-bridge-mux 소켓 통신 (Abstract Namespace UDS)
        ▼
IrisBridge.apk  (본 저장소 산출물, 카카오톡 프로세스 내부 로드 = SERVER)
  ├─ :bridge                LSPosed 모듈(Kotlin). 카카오톡 API 훅, UDS 서버, 
  │                         마크다운 렌더링 훅, 채팅방 정보 조회(Introspection) 담당
  ├─ :imagebridge-protocol  Kotlin 버전 프로토콜 명세 (프레임 입출력, 임대 갱신 제어)
  └─ native/iris-bridge-jni Rust JNI 라이브러리 (`libiris_bridge_core.so`).
                            보안 판단(Token 검증, Lease 연장, 중복 제거) 수행
```

### 단일 진실원 원칙 (Protocol SSOT)

프로토콜에 대한 통제 검증(토큰 핸드쉐이크, 임대 계약 관리, 중복 제어 및 답장 훅 정의) 규칙은 **[park285/Iris](file:///home/kapu/work/iris-stack/Iris)의 `native/iris-bridge-core/` (Rust rlib) 한 곳**을 단일 진실원(SSOT)으로 고정하여 관리합니다.

* 본 저장소의 `native/iris-bridge-jni`는 해당 코어 크레이트를 **Git Dependency** 형태로 참조하여 빌드합니다.
* 클라이언트 측(`park285/Iris`의 `src/bridge/`) 역시 동일한 라이브러리를 사용합니다.
* 따라서 JSON 스키마 필드명, HMAC 서명 생성 규칙, 직렬화 방식, 에러 코드는 완벽히 동기화되어 있으며, **본 저장소에서 관련 프로토콜을 독자적으로 변경하거나 포크(Fork)해서는 안 됩니다.** 프로토콜 수정이 필요할 경우 반드시 `iris-bridge-core`를 거쳐 교차 반영해야 합니다.
* 현재 빌드에 고정된 커밋 번호는 `native/iris-bridge-jni/Cargo.toml` 파일의 `iris-bridge-core` 의존성 `rev` 필드에서 확인할 수 있습니다.

---

## 빌드 방법 (Build Guide)

* 빌드 도구 요구사항: JDK 17, Android SDK (compileSdk 35), Android NDK (`ANDROID_NDK_HOME` 환경 변수 설정 필요)

```bash
# Release APK 빌드
# (cargoBridgeCoreAndroid 태스크가 Rust JNI 라이브러리를 컴파일하여 패키징한 후 최종 APK를 릴리스함)
./gradlew :bridge:assembleRelease
# → 산출물 위치: output/IrisBridge.apk

# 단위 테스트 실행
./gradlew :bridge:testDebugUnitTest :imagebridge-protocol:test

# Rust JNI 코어 단독 빌드 검증
cargo build --manifest-path native/Cargo.toml -p iris-bridge-jni --release
```

* `cargoBridgeCoreAndroid` 태스크를 정상적으로 빌드하려면 `ANDROID_NDK_HOME` 설정 또는 `aarch64-linux-android` 대상의 컴파일러 경로 지정이 필수적입니다. 최종 빌드된 공유 라이브러리(`libiris_bridge_core.so`)는 Android Bionic 타깃용으로 빌드됩니다.

---

## 배포 및 적용 방법 (Deployment)

1. 빌드된 `IrisBridge.apk`를 LSPosed가 구성된 Android 타깃 환경(예: Redroid 호스트)에 설치합니다.
2. LSPosed Manager 앱을 실행하여 IrisBridge 모듈을 활성화합니다.
3. 모듈의 타깃 스코프(Scope)를 **`com.kakao.talk` (카카오톡)** 앱으로만 명확하게 한정하여 지정합니다.
4. 카카오톡 프로세스를 강제 종료(Force Stop)한 후 다시 시작하여 훅이 정상적으로 동작하도록 합니다.

* 새로운 버전의 설치 및 배포 롤아웃은 Iris 쪽 `iris-deploy-rollout` / `iris_control upgrade --install-bridge` 흐름을 따릅니다.

---

## 서브 모듈 구조 (Modules)

| 모듈명 | 내용 및 설명 |
|---|---|
| `bridge/` | Kotlin 기반 LSPosed 모듈. 카카오톡 API 훅 설치, 추상 네임스페이스 UDS 소켓 서버 기동, JNI 로딩을 수행합니다. |
| `imagebridge-protocol/` | Kotlin 프로토콜 프레임 구조체 명세 및 데이터 스트림 직렬화 유틸리티를 정의합니다. |
| `native/iris-bridge-jni/` | Rust JNI 브릿지 구현체 (`cdylib`). `iris-bridge-core` 크레이트를 링크하여 네이티브 단의 보안 규칙을 실행합니다. |

---

## 관련 저장소 링크 (Related Repositories)

* [**park285/Iris**](file:///home/kapu/work/iris-stack/Iris) - Iris 코어 및 프로토콜 정의(`iris-bridge-core`)가 포함되어 있습니다.
* 두 저장소는 `iris-stack` 메타 리포지토리에서 서브모듈의 특정 Commit SHA를 통해 동기화 관리됩니다.
