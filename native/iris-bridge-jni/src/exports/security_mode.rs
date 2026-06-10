use jni::JNIEnv;
use jni::objects::{JClass, JString};

use crate::dispatch::dispatch_normalize_security_mode;
use crate::marshal::{catch_jstring, read_optional_string, return_string};

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniPolicy_nativeNormalizeSecurityMode<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    raw: JString<'local>,
) -> jni::sys::jstring {
    catch_jstring(&mut env, |env| {
        let raw = read_optional_string(env, &raw);
        return_string(env, dispatch_normalize_security_mode(raw.as_deref()))
    })
}
