use std::panic::{AssertUnwindSafe, catch_unwind};

use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::jint;

use crate::dispatch::{
    dispatch_member_generic_label_penalty, dispatch_member_nickname_quality_score,
};
use crate::marshal::read_string;

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniMemberField_nativeMemberNicknameQualityScore<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    value: JString<'local>,
) -> jint {
    catch_unwind(AssertUnwindSafe(|| {
        let value = read_string(&mut env, &value);
        dispatch_member_nickname_quality_score(&value)
    }))
    .unwrap_or(0)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniMemberField_nativeMemberGenericLabelPenalty<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    value: JString<'local>,
) -> jint {
    catch_unwind(AssertUnwindSafe(|| {
        let value = read_string(&mut env, &value);
        dispatch_member_generic_label_penalty(&value)
    }))
    .unwrap_or(0)
}
