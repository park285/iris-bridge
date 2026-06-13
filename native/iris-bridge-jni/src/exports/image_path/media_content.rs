use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::jint;

use crate::dispatch::{
    dispatch_media_message_kind, dispatch_normalize_media_content_types,
    dispatch_normalize_media_content_types_from_leases,
    dispatch_validate_share_manager_image_media,
};
use crate::marshal::{catch_jstring, read_string, return_string};

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniMedia_nativeNormalizeMediaContentTypes<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    image_count: jint,
    content_types_json: JString<'local>,
) -> jni::sys::jstring {
    catch_jstring(&mut env, |env| {
        let content_types_json = read_string(env, &content_types_json);
        return_string(
            env,
            &dispatch_normalize_media_content_types(image_count, &content_types_json),
        )
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniMedia_nativeNormalizeMediaContentTypesFromLeases<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    image_count: jint,
    leases_json: JString<'local>,
) -> jni::sys::jstring {
    catch_jstring(&mut env, |env| {
        let leases_json = read_string(env, &leases_json);
        return_string(
            env,
            &dispatch_normalize_media_content_types_from_leases(image_count, &leases_json),
        )
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniMedia_nativeMediaMessageKind<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    image_count: jint,
    content_types_json: JString<'local>,
) -> jni::sys::jstring {
    catch_jstring(&mut env, |env| {
        let content_types_json = read_string(env, &content_types_json);
        return_string(
            env,
            &dispatch_media_message_kind(image_count, &content_types_json),
        )
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniMedia_nativeValidateShareManagerImageMedia<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    content_types_json: JString<'local>,
) -> jni::sys::jstring {
    catch_jstring(&mut env, |env| {
        let content_types_json = read_string(env, &content_types_json);
        return_string(
            env,
            &dispatch_validate_share_manager_image_media(&content_types_json),
        )
    })
}
