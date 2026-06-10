use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::jboolean;

use crate::dispatch::dispatch_current_bridge_capabilities;
use crate::marshal::{catch_jstring, read_optional_string, return_string};

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniPolicy_nativeCurrentBridgeCapabilities<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    registry_available: jboolean,
    registry_error: JString<'local>,
    spec_ready: jboolean,
    text_supported: jboolean,
    text_ready: jboolean,
    text_reason: JString<'local>,
    send_text_enabled: jboolean,
    send_markdown_enabled: jboolean,
) -> jni::sys::jstring {
    catch_jstring(&mut env, |env| {
        let registry_error = read_optional_string(env, &registry_error);
        let text_reason = read_optional_string(env, &text_reason);
        let envelope = dispatch_current_bridge_capabilities(
            registry_available != 0,
            registry_error.as_deref(),
            spec_ready != 0,
            text_supported != 0,
            text_ready != 0,
            text_reason.as_deref(),
            send_text_enabled != 0,
            send_markdown_enabled != 0,
        );
        return_string(env, &envelope)
    })
}
