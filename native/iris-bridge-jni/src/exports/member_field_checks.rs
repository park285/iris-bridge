mod scoring;

use std::panic::{AssertUnwindSafe, catch_unwind};

use jni::JNIEnv;
use jni::objects::{JClass, JObjectArray, JString};
use jni::sys::{jboolean, jint, jlong};

use crate::dispatch::{
    dispatch_member_looks_like_mention_user_id_value, dispatch_member_looks_like_nickname,
    dispatch_member_looks_like_profile_url, dispatch_member_nickname_is_trusted_for_display,
    dispatch_member_parse_role_code_from_long, dispatch_member_parse_role_code_from_string,
    dispatch_member_path_hint_score, dispatch_member_primitive_long_value_from_string,
};
use crate::marshal::{
    catch_jstring, read_optional_string, read_string, read_string_array, return_optional_string,
};

const MISSING_INT: jint = -1;

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniMemberField_nativeMemberParseRoleCodeFromLong<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    value: jlong,
) -> jint {
    catch_unwind(AssertUnwindSafe(|| {
        dispatch_member_parse_role_code_from_long(value).unwrap_or(MISSING_INT)
    }))
    .unwrap_or(MISSING_INT)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniMemberField_nativeMemberParseRoleCodeFromString<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    value: JString<'local>,
) -> jint {
    catch_unwind(AssertUnwindSafe(|| {
        let value = read_string(&mut env, &value);
        dispatch_member_parse_role_code_from_string(&value).unwrap_or(MISSING_INT)
    }))
    .unwrap_or(MISSING_INT)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniMemberField_nativeMemberLooksLikeNickname<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    value: JString<'local>,
) -> jboolean {
    let outcome = catch_unwind(AssertUnwindSafe(|| {
        let value = read_string(&mut env, &value);
        dispatch_member_looks_like_nickname(&value)
    }));
    jboolean::from(outcome.unwrap_or(false))
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniMemberField_nativeMemberLooksLikeProfileUrl<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    value: JString<'local>,
) -> jboolean {
    let outcome = catch_unwind(AssertUnwindSafe(|| {
        let value = read_string(&mut env, &value);
        dispatch_member_looks_like_profile_url(&value)
    }));
    jboolean::from(outcome.unwrap_or(false))
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniMemberField_nativeMemberPrimitiveLongValueFromString<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    value: JString<'local>,
) -> jni::sys::jstring {
    catch_jstring(&mut env, |env| {
        let value = read_string(env, &value);
        return_optional_string(
            env,
            dispatch_member_primitive_long_value_from_string(&value),
        )
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniMemberField_nativeMemberPathHintScore<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    path: JString<'local>,
    preferred_tokens: JObjectArray<'local>,
    discouraged_tokens: JObjectArray<'local>,
) -> jint {
    catch_unwind(AssertUnwindSafe(|| {
        let path = read_string(&mut env, &path);
        let preferred_tokens = read_string_array(&mut env, &preferred_tokens).unwrap_or_default();
        let discouraged_tokens =
            read_string_array(&mut env, &discouraged_tokens).unwrap_or_default();
        dispatch_member_path_hint_score(&path, &preferred_tokens, &discouraged_tokens)
    }))
    .unwrap_or(0)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniMemberField_nativeMemberNicknameIsTrustedForDisplay<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    user_id: jlong,
    nickname: JString<'local>,
) -> jboolean {
    let outcome = catch_unwind(AssertUnwindSafe(|| {
        let nickname = read_string(&mut env, &nickname);
        dispatch_member_nickname_is_trusted_for_display(user_id, &nickname)
    }));
    jboolean::from(outcome.unwrap_or(false))
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniMemberField_nativeMemberLooksLikeMentionUserIdValue<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    value: JString<'local>,
    user_id: jlong,
    has_user_id: jboolean,
    nickname: JString<'local>,
) -> jboolean {
    let outcome = catch_unwind(AssertUnwindSafe(|| {
        let value = read_string(&mut env, &value);
        let nickname = read_optional_string(&mut env, &nickname);
        let user_id = (has_user_id != 0).then_some(user_id);
        dispatch_member_looks_like_mention_user_id_value(&value, user_id, nickname.as_deref())
    }));
    jboolean::from(outcome.unwrap_or(false))
}
