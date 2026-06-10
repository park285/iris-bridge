use std::panic::{AssertUnwindSafe, catch_unwind};

use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jint, jlong};

use crate::ABI_VERSION;
use crate::handles::{BridgeCoreContext, drop_handle, into_handle, with_context};
use crate::marshal::{read_optional_string, read_string};

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCore_nativeCreateContext<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    mode: JString<'local>,
    token: JString<'local>,
    require_handshake_raw: JString<'local>,
) -> jlong {
    let result = catch_unwind(AssertUnwindSafe(|| {
        let mode = read_optional_string(&mut env, &mode);
        let token = read_string(&mut env, &token);
        let require = read_optional_string(&mut env, &require_handshake_raw);
        into_handle(BridgeCoreContext::new(
            mode.as_deref(),
            &token,
            require.as_deref(),
        ))
    }));
    result.unwrap_or(0)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCore_nativeDestroyContext(
    _env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
) {
    let _ = catch_unwind(AssertUnwindSafe(|| drop_handle(handle)));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCore_nativeRequireHandshake(
    _env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
) -> jboolean {
    let outcome = catch_unwind(AssertUnwindSafe(|| {
        with_context(handle, |context| context.require_handshake).unwrap_or(true)
    }));
    jboolean::from(outcome.unwrap_or(true))
}

#[unsafe(no_mangle)]
pub const extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCore_nativeAbiVersion(
    _env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jint {
    ABI_VERSION
}
