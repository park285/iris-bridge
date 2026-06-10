use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jint, jlong};

use crate::dispatch::dispatch_send_block_reason;
use crate::marshal::{catch_jstring, read_string, return_string};

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniPolicy_nativeSendBlockReason<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    install_attempted: jboolean,
    hooks_json: JString<'local>,
    image_count: jint,
    thread_id: jlong,
    has_thread_id: jboolean,
    thread_scope: jint,
    has_thread_scope: jboolean,
) -> jni::sys::jstring {
    catch_jstring(&mut env, |env| {
        let hooks_json = read_string(env, &hooks_json);
        let reason = dispatch_send_block_reason(
            install_attempted != 0,
            &hooks_json,
            image_count,
            (has_thread_id != 0).then_some(thread_id),
            (has_thread_scope != 0).then_some(thread_scope),
        )
        .unwrap_or_default();
        return_string(env, &reason)
    })
}
