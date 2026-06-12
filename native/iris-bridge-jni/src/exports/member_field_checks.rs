use std::panic::{AssertUnwindSafe, catch_unwind};

use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jlong};

use crate::dispatch::{
    dispatch_member_extraction_evaluate, dispatch_member_nickname_is_trusted_for_display,
};
use crate::marshal::{catch_jstring, read_string, return_string};

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
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniMemberField_nativeMemberExtractionEvaluate<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    request_json: JString<'local>,
) -> jni::sys::jstring {
    catch_jstring(&mut env, |env| {
        let request_json = read_string(env, &request_json);
        return_string(env, &dispatch_member_extraction_evaluate(&request_json))
    })
}
