# iris-bridge

카카오톡 프로세스 안에서 동작하는 **LSPosed/Xposed 브리지**입니다. Iris Rust 런타임(`park285/Iris`)이 reply 전송(텍스트·마크다운·이미지), 채팅방 open/snapshot, 브리지 health를 카카오톡 앱에 위임할 때 그 **서버 쪽**을 담당합니다. 원래 `park285/Iris`에 포함돼 있었으나 LSPosed decouple 과정에서 이 레포로 분리됐습니다.

런타임은 `IRIS_REPLY_TRANSPORT=bridge`일 때만 이 브리지에 연결합니다. `intent`/`none` transport에서는 코어가 브리지 없이 동작하므로 이 모듈이 필요 없습니다.

## 아키텍처

```
Iris Rust 런타임 (park285/Iris, native/iris-runtime/src/bridge/ = CLIENT)
        │  @iris-image-bridge-mux  (abstract UDS, mux 프로토콜)
        ▼
IrisBridge.apk  (이 레포, 카카오톡 프로세스 내부 = SERVER)
  ├─ :bridge                LSPosed 모듈(Kotlin). 카카오톡 훅, mux UDS 서버,
  │                         reply-markdown 훅, 채팅방 introspection
  ├─ :imagebridge-protocol  Kotlin wire 모델(capability/threat/lease/frame I/O)
  └─ native/iris-bridge-jni Rust cdylib `libiris_bridge_core.so` — JNI로 로드.
                            프로토콜 verdict(token·handshake·lease·dedupe)를 수행
```

### iris-bridge-core 가 단일 진실원(SSOT)

프로토콜 verdict(토큰, handshake, lease, dedupe, reply-hook)의 정의는 **`park285/Iris`의 `native/iris-bridge-core/` rlib 한 곳**에 고정돼 있습니다. 이 레포의 `native/iris-bridge-jni`는 그 크레이트를 **git-dep**으로 가져와 소비하고, Iris 런타임의 `src/bridge/` 클라이언트도 같은 rlib을 소비합니다. 따라서 JSON 필드명·HMAC 도메인·canonical form·error-code 문자열은 양쪽이 동일합니다 — **이 레포에서 JSON 형태를 새로 만들거나 fork하지 마십시오.** 프로토콜 변경은 `iris-bridge-core`를 통해 cross-repo로 조율합니다.

현재 고정 rev: `native/iris-bridge-jni/Cargo.toml`의 `iris-bridge-core` git-dep `rev`로 확인합니다.

## 빌드

JDK 17 + Android SDK(compileSdk 35) + Android NDK(`ANDROID_NDK_HOME`)가 필요합니다.

```bash
# 릴리스 APK 빌드 (cargoBridgeCoreAndroid 가 NDK aarch64로
# libiris_bridge_core.so 를 빌드해 jniLibs 로 패키징한 뒤 APK 생성)
./gradlew :bridge:assembleRelease
# → output/IrisBridge.apk

# 단위 테스트
./gradlew :bridge:testDebugUnitTest :imagebridge-protocol:test

# Rust 코어(JNI cdylib) 단독 빌드/체크
cargo build --manifest-path native/Cargo.toml -p iris-bridge-jni --release
```

`cargoBridgeCoreAndroid` task는 `ANDROID_NDK_HOME`(또는 `CC/CXX/AR_aarch64_linux_android`)가 없으면 실패합니다. 산출 `libiris_bridge_core.so`는 `aarch64-linux-android`(NDK Bionic) 타깃입니다.

## 배포

`IrisBridge.apk`는 LSPosed 프레임워크가 설치된 환경(Redroid 등)에 설치하고, LSPosed 관리자에서 카카오톡 프로세스를 scope로 활성화합니다. Iris 런타임에서 `IRIS_REPLY_TRANSPORT=bridge`로 reply 경로를 사용할 때 필요합니다. 설치·롤아웃은 Iris 쪽 `iris-deploy-rollout` / `iris_control upgrade --install-bridge` 흐름을 따릅니다.

## 모듈

| 경로 | 내용 |
|---|---|
| `bridge/` | LSPosed 모듈(Kotlin, `party.qwer.iris.bridge`). 카카오톡 훅 + `@iris-image-bridge-mux` UDS 서버 + reply-markdown 훅 + 채팅방 introspection. `native/iris-bridge-jni`를 JNI로 로드 |
| `imagebridge-protocol/` | Kotlin wire 모델: `BridgeCapabilities`, `BridgeThreat`, `ImageLease`, frame I/O, runtime config |
| `native/iris-bridge-jni/` | Rust 크레이트 (`cdylib`+`rlib`, lib 이름 `iris_bridge_core`). `iris-bridge-core`(git-dep, park285/Iris) verdict를 JNI 경계로 노출 |

## 관련 레포

- **`park285/Iris`** — Iris 코어(순수 Rust 런타임) + `iris-bridge-core` 프로토콜 SSOT. 이 브리지의 클라이언트(`native/iris-runtime/src/bridge/`)와 contract 원본이 거기 있습니다.
- 두 레포는 `iris-stack` 메타 레포에서 submodule SHA로 함께 스냅샷됩니다.
