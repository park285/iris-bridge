use std::panic::{AssertUnwindSafe, catch_unwind};

use jni::JNIEnv;
use jni::objects::JString;

const MARSHAL_ARGUMENT_ENVELOPE: &str =
    r#"{"ok":false,"errorCode":"MARSHAL","error":"failed to read JNI string argument"}"#;

pub fn read_string(env: &mut JNIEnv<'_>, value: &JString<'_>) -> Option<String> {
    env.get_string(value).ok().map(Into::into)
}

pub fn dispatch_marshalled(
    op: Option<String>,
    payload: Option<String>,
    dispatch: impl FnOnce(&str, &str) -> String,
) -> String {
    match op.zip(payload) {
        Some((op, payload)) => dispatch(&op, &payload),
        None => MARSHAL_ARGUMENT_ENVELOPE.to_owned(),
    }
}

pub fn return_string(env: &JNIEnv<'_>, value: &str) -> jni::sys::jstring {
    env.new_string(value).map_or(std::ptr::null_mut(), |java| {
        jni::objects::JObject::from(java).into_raw()
    })
}

pub fn catch_jstring(
    env: &mut JNIEnv<'_>,
    body: impl FnOnce(&mut JNIEnv<'_>) -> jni::sys::jstring,
) -> jni::sys::jstring {
    catch_unwind(AssertUnwindSafe(|| body(env))).unwrap_or_else(|_| {
        return_string(
            env,
            r#"{"ok":false,"errorCode":"PANIC","error":"panic across JNI boundary"}"#,
        )
    })
}
