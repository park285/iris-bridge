use jni::JNIEnv;
use jni::objects::{JClass, JString};

use crate::dispatch::dispatch_op;
use crate::marshal::{catch_jstring, dispatch_marshalled, read_string, return_string};

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniDispatcher_nativeDispatch<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    op: JString<'local>,
    payload: JString<'local>,
) -> jni::sys::jstring {
    catch_jstring(&mut env, |env| {
        let op = read_string(env, &op);
        let payload = read_string(env, &payload);
        return_string(env, &dispatch_marshalled(op, payload, dispatch_op))
    })
}
