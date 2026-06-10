use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::jlong;

use crate::dispatch::{
    dispatch_handshake_on_client_proof, dispatch_handshake_on_hello, invalid_handle_envelope,
};
use crate::handles::with_context;
use crate::marshal::{catch_jstring, read_string, return_string};

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCore_nativeHandshakeOnHello<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    frame_json: JString<'local>,
    now_ms: jlong,
    socket_name: JString<'local>,
) -> jni::sys::jstring {
    catch_jstring(&mut env, |env| {
        let frame = read_string(env, &frame_json);
        let socket = read_string(env, &socket_name);
        let envelope = match with_context(handle, |context| {
            dispatch_handshake_on_hello(context, &frame, now_ms, &socket)
        }) {
            Ok(envelope) => envelope,
            Err(rejection) => invalid_handle_envelope(&rejection),
        };
        return_string(env, &envelope)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCore_nativeHandshakeOnClientProof<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    frame_json: JString<'local>,
) -> jni::sys::jstring {
    catch_jstring(&mut env, |env| {
        let frame = read_string(env, &frame_json);
        let envelope = match with_context(handle, |context| {
            dispatch_handshake_on_client_proof(context, &frame)
        }) {
            Ok(envelope) => envelope,
            Err(rejection) => invalid_handle_envelope(&rejection),
        };
        return_string(env, &envelope)
    })
}
