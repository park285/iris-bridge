use jni::JNIEnv;
use jni::objects::{JBooleanArray, JClass, JObjectArray};
use jni::sys::{jboolean, jint, jlong};

use crate::dispatch::{
    DISCOVERY_HOOK_SNAPSHOT_INVALID_REASON, dispatch_send_block_reason_from_snapshot,
};
use crate::marshal::{catch_jstring, read_boolean_array, read_string_array, return_string};

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniPolicy_nativeSendBlockReason<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    install_attempted: jboolean,
    hook_names: JObjectArray<'local>,
    hook_installed: JBooleanArray<'local>,
    image_count: jint,
    thread_id: jlong,
    has_thread_id: jboolean,
    thread_scope: jint,
    has_thread_scope: jboolean,
) -> jni::sys::jstring {
    catch_jstring(&mut env, |env| {
        let reason = if install_attempted == 0 {
            dispatch_send_block_reason_from_snapshot(
                false,
                &[],
                &[],
                image_count,
                (has_thread_id != 0).then_some(thread_id),
                (has_thread_scope != 0).then_some(thread_scope),
            )
        } else {
            match (
                read_string_array(env, &hook_names),
                read_boolean_array(env, &hook_installed),
            ) {
                (Some(hook_names), Some(hook_installed)) => {
                    dispatch_send_block_reason_from_snapshot(
                        true,
                        &hook_names,
                        &hook_installed,
                        image_count,
                        (has_thread_id != 0).then_some(thread_id),
                        (has_thread_scope != 0).then_some(thread_scope),
                    )
                }
                _ => Some(DISCOVERY_HOOK_SNAPSHOT_INVALID_REASON.to_owned()),
            }
        }
        .unwrap_or_default();
        return_string(env, &reason)
    })
}
