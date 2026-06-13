use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::{jint, jlong};

use crate::dispatch::{
    dispatch_mux_session_create, dispatch_mux_session_destroy, dispatch_mux_session_is_cancelled,
    dispatch_mux_session_on_executor_rejected, dispatch_mux_session_on_frame,
    dispatch_mux_session_on_request_completed,
};
use crate::marshal::{catch_jstring, read_string, return_string};

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniMuxSession_nativeCreateMuxSession<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    max_in_flight: jint,
) -> jni::sys::jstring {
    catch_jstring(&mut env, |env| {
        return_string(env, &dispatch_mux_session_create(max_in_flight))
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniMuxSession_nativeDestroyMuxSession<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
) -> jni::sys::jstring {
    catch_jstring(&mut env, |env| {
        return_string(env, &dispatch_mux_session_destroy(handle))
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniMuxSession_nativeMuxSessionOnFrame<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    summary_json: JString<'local>,
) -> jni::sys::jstring {
    catch_jstring(&mut env, |env| {
        let summary_json = read_string(env, &summary_json);
        return_string(env, &dispatch_mux_session_on_frame(handle, &summary_json))
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniMuxSession_nativeMuxSessionOnExecutorRejected<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    correlation_id: JString<'local>,
) -> jni::sys::jstring {
    catch_jstring(&mut env, |env| {
        let correlation_id = read_string(env, &correlation_id);
        return_string(
            env,
            &dispatch_mux_session_on_executor_rejected(handle, &correlation_id),
        )
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniMuxSession_nativeMuxSessionOnRequestCompleted<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    correlation_id: JString<'local>,
) -> jni::sys::jstring {
    catch_jstring(&mut env, |env| {
        let correlation_id = read_string(env, &correlation_id);
        return_string(
            env,
            &dispatch_mux_session_on_request_completed(handle, &correlation_id),
        )
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniMuxSession_nativeMuxSessionIsCancelled<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    correlation_id: JString<'local>,
) -> jni::sys::jstring {
    catch_jstring(&mut env, |env| {
        let correlation_id = read_string(env, &correlation_id);
        return_string(
            env,
            &dispatch_mux_session_is_cancelled(handle, &correlation_id),
        )
    })
}
