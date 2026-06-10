use jni::JNIEnv;
use jni::objects::{JClass, JString};

use crate::dispatch::dispatch_resolve_kakao_target;
use crate::marshal::{catch_jstring, read_string, return_string};

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniKakaoTarget_nativeResolveKakaoTarget<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    package_name: JString<'local>,
) -> jni::sys::jstring {
    catch_jstring(&mut env, |env| {
        let package_name = read_string(env, &package_name);
        let envelope = dispatch_resolve_kakao_target(&package_name);
        return_string(env, &envelope)
    })
}
