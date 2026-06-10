use std::panic::{AssertUnwindSafe, catch_unwind};

use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jlong};

use crate::dispatch::{
    dispatch_mentions_hash_from_attachment, dispatch_mentions_hash_from_json,
    dispatch_reply_hook_sign, dispatch_reply_hook_verify,
};
use crate::marshal::{catch_jstring, read_optional_string, read_string, return_optional_string};

#[unsafe(no_mangle)]
#[allow(
    clippy::too_many_arguments,
    reason = "Kotlin BridgeCore.nativeReplyHookSign external fun과 동결된 인자 계약"
)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCore_nativeReplyHookSign<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    bridge_token: JString<'local>,
    room_id: jlong,
    message_text: JString<'local>,
    session_id: JString<'local>,
    created_at_epoch_ms: jlong,
    mentions_hash: JString<'local>,
) -> jni::sys::jstring {
    catch_jstring(&mut env, |env| {
        let token = read_string(env, &bridge_token);
        let text = read_string(env, &message_text);
        let session = read_string(env, &session_id);
        let mentions = read_optional_string(env, &mentions_hash);
        let signature = dispatch_reply_hook_sign(
            &token,
            room_id,
            &text,
            &session,
            created_at_epoch_ms,
            mentions.as_deref(),
        );
        return_optional_string(env, signature)
    })
}

#[unsafe(no_mangle)]
#[allow(
    clippy::too_many_arguments,
    reason = "Kotlin BridgeCore.nativeReplyHookVerify external fun과 동결된 인자 계약"
)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCore_nativeReplyHookVerify<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    bridge_token: JString<'local>,
    room_id: jlong,
    message_text: JString<'local>,
    session_id: JString<'local>,
    created_at_epoch_ms: jlong,
    mentions_hash: JString<'local>,
    signature: JString<'local>,
    now_epoch_ms: jlong,
) -> jboolean {
    let outcome = catch_unwind(AssertUnwindSafe(|| {
        let token = read_string(&mut env, &bridge_token);
        let text = read_string(&mut env, &message_text);
        let session = read_optional_string(&mut env, &session_id);
        let mentions = read_optional_string(&mut env, &mentions_hash);
        let signature = read_optional_string(&mut env, &signature);
        dispatch_reply_hook_verify(
            &token,
            room_id,
            &text,
            session.as_deref(),
            Some(created_at_epoch_ms),
            mentions.as_deref(),
            signature.as_deref(),
            now_epoch_ms,
        )
    }));
    jboolean::from(outcome.unwrap_or(false))
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCore_nativeMentionsHashFromJson<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    mentions_json: JString<'local>,
) -> jni::sys::jstring {
    catch_jstring(&mut env, |env| {
        let mentions = read_optional_string(env, &mentions_json);
        return_optional_string(env, dispatch_mentions_hash_from_json(mentions.as_deref()))
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCore_nativeMentionsHashFromAttachment<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    attachment_text: JString<'local>,
) -> jni::sys::jstring {
    catch_jstring(&mut env, |env| {
        let attachment = read_optional_string(env, &attachment_text);
        return_optional_string(
            env,
            dispatch_mentions_hash_from_attachment(attachment.as_deref()),
        )
    })
}
