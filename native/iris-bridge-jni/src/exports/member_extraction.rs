use jni::JNIEnv;
use jni::objects::{JClass, JString};

use crate::dispatch::{
    dispatch_member_enrichment_merge, dispatch_member_enrichment_missing_nicknames,
    dispatch_member_extraction_evaluate,
};
use crate::marshal::{catch_jstring, read_string, return_string};

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

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniMemberField_nativeMemberEnrichmentMissingNicknames<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    request_json: JString<'local>,
) -> jni::sys::jstring {
    catch_jstring(&mut env, |env| {
        let request_json = read_string(env, &request_json);
        return_string(
            env,
            &dispatch_member_enrichment_missing_nicknames(&request_json),
        )
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniMemberField_nativeMemberEnrichmentMerge<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    request_json: JString<'local>,
) -> jni::sys::jstring {
    catch_jstring(&mut env, |env| {
        let request_json = read_string(env, &request_json);
        return_string(env, &dispatch_member_enrichment_merge(&request_json))
    })
}
