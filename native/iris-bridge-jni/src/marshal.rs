use std::panic::{AssertUnwindSafe, catch_unwind};

use jni::JNIEnv;
use jni::objects::JString;

pub fn read_string(env: &mut JNIEnv<'_>, value: &JString<'_>) -> String {
    env.get_string(value)
        .map_or_else(|_| String::new(), Into::into)
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
