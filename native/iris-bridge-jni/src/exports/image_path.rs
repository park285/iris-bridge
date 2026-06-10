use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::{jint, jlong};

use crate::dispatch::{dispatch_validate_image_paths, invalid_handle_envelope};
use crate::handles::with_context;
use crate::marshal::{catch_jstring, read_string, return_string};

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCore_nativeValidateImagePaths<
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
