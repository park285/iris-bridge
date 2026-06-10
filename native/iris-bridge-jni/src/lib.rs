#![allow(unsafe_code, reason = "JNI export ABI 표면")]

use jni::JNIEnv;
use jni::objects::JClass;
use jni::sys::jint;

pub const ABI_VERSION: jint = 1;

#[unsafe(no_mangle)]
pub const extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCore_nativeAbiVersion(
    _env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jint {
    ABI_VERSION
}
