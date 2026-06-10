use std::panic::{AssertUnwindSafe, catch_unwind};

use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::jboolean;

use crate::dispatch::{
    dispatch_kakao_link_attachments_match, dispatch_kakao_link_leverage_encryption_type,
    dispatch_kakao_link_pending_cleanup_attachments_match,
};
use crate::marshal::read_string;

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
