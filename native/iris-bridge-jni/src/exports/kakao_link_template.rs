use std::panic::{AssertUnwindSafe, catch_unwind};

use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::jboolean;

use crate::dispatch::{
    dispatch_kakao_link_build_spec_send_attachment, dispatch_kakao_link_build_v4_encoded_query,
    dispatch_kakao_link_extract_app_key, dispatch_kakao_link_has_explicit_template_args,
    dispatch_kakao_link_has_resolved_iris_template, dispatch_kakao_link_patch_display_attachment,
};
use crate::marshal::{
    catch_jstring, read_optional_string, read_string, return_optional_string, return_string,
};

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniKakaoLink_nativeKakaoLinkHasExplicitTemplateArgs<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    raw_attachment: JString<'local>,
) -> jboolean {
    let outcome = catch_unwind(AssertUnwindSafe(|| {
        let raw = read_string(&mut env, &raw_attachment);
        dispatch_kakao_link_has_explicit_template_args(&raw)
    }));
    jboolean::from(outcome.unwrap_or(false))
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniKakaoLink_nativeKakaoLinkHasResolvedIrisTemplate<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    raw_attachment: JString<'local>,
) -> jboolean {
    let outcome = catch_unwind(AssertUnwindSafe(|| {
        let raw = read_string(&mut env, &raw_attachment);
        dispatch_kakao_link_has_resolved_iris_template(&raw)
    }));
    jboolean::from(outcome.unwrap_or(false))
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniKakaoLink_nativeKakaoLinkExtractAppKey<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    raw_attachment: JString<'local>,
) -> jni::sys::jstring {
    catch_jstring(&mut env, |env| {
        let raw = read_string(env, &raw_attachment);
        return_optional_string(env, dispatch_kakao_link_extract_app_key(&raw))
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniKakaoLink_nativeBuildKakaoLinkV4EncodedQuery<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    raw_attachment: JString<'local>,
) -> jni::sys::jstring {
    catch_jstring(&mut env, |env| {
        let raw = read_string(env, &raw_attachment);
        return_optional_string(env, dispatch_kakao_link_build_v4_encoded_query(&raw))
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniKakaoLink_nativeBuildKakaoLinkSpecSendAttachment<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    raw_attachment: JString<'local>,
) -> jni::sys::jstring {
    catch_jstring(&mut env, |env| {
        let raw = read_string(env, &raw_attachment);
        return_string(env, &dispatch_kakao_link_build_spec_send_attachment(&raw))
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniKakaoLink_nativePatchKakaoLinkDisplayAttachment<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    committed_attachment: JString<'local>,
    raw_attachment: JString<'local>,
) -> jni::sys::jstring {
    catch_jstring(&mut env, |env| {
        let committed = read_optional_string(env, &committed_attachment);
        let raw = read_string(env, &raw_attachment);
        return_string(
            env,
            &dispatch_kakao_link_patch_display_attachment(committed.as_deref(), &raw),
        )
    })
}
