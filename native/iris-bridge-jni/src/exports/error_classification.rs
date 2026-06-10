use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::jboolean;

use crate::dispatch::{dispatch_classify_error_code, dispatch_failure_metric_bucket};
use crate::marshal::{catch_jstring, read_string, return_string};

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniRequest_nativeClassifyErrorCode<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    message: JString<'local>,
    is_illegal_argument: jboolean,
) -> jni::sys::jstring {
    catch_jstring(&mut env, |env| {
        let message = read_string(env, &message);
        let envelope = dispatch_classify_error_code(&message, is_illegal_argument != 0);
        return_string(env, &envelope)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniRequest_nativeFailureMetricBucket<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    error_code: JString<'local>,
) -> jni::sys::jstring {
    catch_jstring(&mut env, |env| {
        let error_code = read_string(env, &error_code);
        return_string(env, dispatch_failure_metric_bucket(&error_code))
    })
}
