use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::jboolean;

use crate::dispatch::dispatch_classify_error_code;
use crate::marshal::{catch_jstring, read_string, return_string};

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCore_nativeClassifyErrorCode<
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
