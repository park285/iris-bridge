# ── Iris Bridge (Xposed Module) R8 Rules ──────────────────────────────────

# 디버그 스택트레이스 보존
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── modern LSPosed 진입점 (META-INF/xposed/java_init.list에 명시) ─────────
-keep class party.qwer.iris.imagebridge.runtime.IrisBridgeModule { *; }

# ── Xposed API (compileOnly — 런타임은 프레임워크 제공) ────────────────────
-dontwarn io.github.libxposed.api.**
-keep class io.github.libxposed.api.** { *; }

# ── ImageBridge protocol (공유 모듈) ───────────────────────────────────────
-keep class party.qwer.iris.ImageBridgeProtocol { *; }
-keep class party.qwer.iris.ImageBridgeProtocol$* { *; }

# ── Kotlin ─────────────────────────────────────────────────────────────────
-keep class kotlin.Metadata { *; }
