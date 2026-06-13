use std::panic::{AssertUnwindSafe, catch_unwind};

use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jint, jlong};

use crate::dispatch::{
    dispatch_image_path_under_allowed_root, dispatch_materialize_image_path,
    dispatch_media_message_kind, dispatch_normalize_media_content_types,
    dispatch_revalidate_image_path_snapshot, dispatch_validate_image_paths,
    dispatch_validate_share_manager_image_media, invalid_handle_envelope,
};
use crate::handles::with_context;
use crate::marshal::{catch_jstring, read_string, return_string};

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniRequest_nativeValidateImagePaths<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    image_paths_json: JString<'local>,
    max_path_count: jint,
    max_path_length: jint,
) -> jni::sys::jstring {
    catch_jstring(&mut env, |env| {
        let image_paths = read_string(env, &image_paths_json);
        let envelope = match with_context(handle, |_| {
            dispatch_validate_image_paths(
                &image_paths,
                usize::try_from(max_path_count).unwrap_or_default(),
                usize::try_from(max_path_length).unwrap_or_default(),
            )
        }) {
            Ok(envelope) => envelope,
            Err(rejection) => invalid_handle_envelope(&rejection),
        };
        return_string(env, &envelope)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniImagePath_nativeImagePathUnderAllowedRoot<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    path: JString<'local>,
    allowed_roots_json: JString<'local>,
) -> jboolean {
    let outcome = catch_unwind(AssertUnwindSafe(|| {
        let path = read_string(&mut env, &path);
        let allowed_roots_json = read_string(&mut env, &allowed_roots_json);
        dispatch_image_path_under_allowed_root(&path, &allowed_roots_json)
    }));
    jboolean::from(outcome.unwrap_or(false))
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniImagePath_nativeMaterializeImagePath<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    path: JString<'local>,
    allowed_roots_json: JString<'local>,
) -> jni::sys::jstring {
    catch_jstring(&mut env, |env| {
        let path = read_string(env, &path);
        let allowed_roots_json = read_string(env, &allowed_roots_json);
        let envelope = dispatch_materialize_image_path(&path, &allowed_roots_json);
        return_string(env, &envelope)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniImagePath_nativeRevalidateImagePathSnapshot<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    canonical_path: JString<'local>,
    allowed_roots_json: JString<'local>,
    size_bytes: jlong,
    last_modified_epoch_ms: jlong,
) -> jni::sys::jstring {
    catch_jstring(&mut env, |env| {
        let canonical_path = read_string(env, &canonical_path);
        let allowed_roots_json = read_string(env, &allowed_roots_json);
        let envelope = dispatch_revalidate_image_path_snapshot(
            &canonical_path,
            &allowed_roots_json,
            size_bytes,
            last_modified_epoch_ms,
        );
        return_string(env, &envelope)
    })
}

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
