use std::panic::{AssertUnwindSafe, catch_unwind};

use iris_bridge_core::server::{ERROR_BAD_REQUEST, Rejection};
use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::jboolean;
use serde_json::Value;

use crate::dispatch::{
    dispatch_kakao_chat_log_attachment_crypto, dispatch_kakao_link_attachments_match,
    dispatch_kakao_link_leverage_encryption_type,
    dispatch_kakao_link_pending_cleanup_attachments_match, json_catch_unwind,
};
use crate::marshal::{catch_jstring, read_string, return_string};

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniKakaoLink_nativeKakaoChatLogAttachmentCrypto<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    encrypt: jboolean,
    enc_type: jni::sys::jint,
    payload: JString<'local>,
    user_id: jni::sys::jlong,
) -> jni::sys::jstring {
    catch_jstring(&mut env, |env| {
        let Some(encrypt) = read_crypto_direction(encrypt) else {
            return return_bad_request(env, "invalid Kakao chat log crypto direction");
        };
        let Some(payload) = read_crypto_payload(env, &payload) else {
            return return_bad_request(env, "Kakao chat log crypto payload is required");
        };
        return_string(
            env,
            &dispatch_kakao_chat_log_attachment_crypto(encrypt, enc_type, &payload, user_id),
        )
    })
}

fn read_crypto_direction(value: jboolean) -> Option<bool> {
    match value {
        0 => Some(false),
        1 => Some(true),
        _ => None,
    }
}

fn read_crypto_payload(env: &mut JNIEnv<'_>, value: &JString<'_>) -> Option<String> {
    if !crypto_payload_is_present(value) {
        return None;
    }
    env.get_string(value).ok().map(Into::into)
}

fn crypto_payload_is_present(value: &JString<'_>) -> bool {
    !value.is_null()
}

fn return_bad_request(env: &JNIEnv<'_>, message: &'static str) -> jni::sys::jstring {
    let envelope =
        json_catch_unwind(|| Err::<Value, _>(Rejection::new(ERROR_BAD_REQUEST, message)));
    return_string(env, &envelope)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniKakaoLink_nativeKakaoLinkAttachmentsMatch<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    expected_raw_attachment: JString<'local>,
    committed_raw_attachment: JString<'local>,
) -> jboolean {
    let outcome = catch_unwind(AssertUnwindSafe(|| {
        let expected = read_string(&mut env, &expected_raw_attachment);
        let committed = read_string(&mut env, &committed_raw_attachment);
        dispatch_kakao_link_attachments_match(&expected, &committed)
    }));
    jboolean::from(outcome.unwrap_or(false))
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniKakaoLink_nativeKakaoLinkPendingCleanupAttachmentsMatch<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    expected_raw_attachment: JString<'local>,
    pending_raw_attachment: JString<'local>,
) -> jboolean {
    let outcome = catch_unwind(AssertUnwindSafe(|| {
        let expected = read_string(&mut env, &expected_raw_attachment);
        let pending = read_string(&mut env, &pending_raw_attachment);
        dispatch_kakao_link_pending_cleanup_attachments_match(&expected, &pending)
    }));
    jboolean::from(outcome.unwrap_or(false))
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniKakaoLink_nativeKakaoLinkLeverageEncryptionType<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    value: JString<'local>,
) -> jni::sys::jint {
    let outcome = catch_unwind(AssertUnwindSafe(|| {
        let value = read_string(&mut env, &value);
        dispatch_kakao_link_leverage_encryption_type(&value)
    }));
    outcome.unwrap_or(31)
}

#[cfg(test)]
mod tests {
    use super::{crypto_payload_is_present, read_crypto_direction};
    use jni::objects::{JObject, JString};

    #[test]
    fn kakao_chat_log_crypto_direction_accepts_only_jni_boolean_values() {
        assert_eq!(read_crypto_direction(0), Some(false));
        assert_eq!(read_crypto_direction(1), Some(true));
        assert_eq!(read_crypto_direction(2), None);
        assert_eq!(read_crypto_direction(u8::MAX), None);
    }

    #[test]
    fn kakao_chat_log_crypto_payload_rejects_null_jstring() {
        let payload = JString::from(JObject::null());
        assert!(!crypto_payload_is_present(&payload));
    }
}
