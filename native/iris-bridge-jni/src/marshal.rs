use std::panic::{AssertUnwindSafe, catch_unwind};

use jni::JNIEnv;
use jni::objects::{JBooleanArray, JObjectArray, JString};
use jni::sys::jboolean;

pub fn read_string(env: &mut JNIEnv<'_>, value: &JString<'_>) -> String {
    env.get_string(value)
        .map_or_else(|_| String::new(), Into::into)
}

pub fn read_optional_string(env: &mut JNIEnv<'_>, value: &JString<'_>) -> Option<String> {
    if value.is_null() {
        return None;
    }
    env.get_string(value).ok().map(Into::into)
}

pub fn read_string_array(env: &mut JNIEnv<'_>, values: &JObjectArray<'_>) -> Option<Vec<String>> {
    if values.as_raw().is_null() {
        return None;
    }
    let length = env.get_array_length(values).ok()?;
    let mut result = Vec::with_capacity(usize::try_from(length).ok()?);
    for index in 0..length {
        let value = env.get_object_array_element(values, index).ok()?;
        if value.as_raw().is_null() {
            return None;
        }
        result.push(env.get_string(&JString::from(value)).ok()?.into());
    }
    Some(result)
}

pub fn read_boolean_array(env: &JNIEnv<'_>, values: &JBooleanArray<'_>) -> Option<Vec<bool>> {
    if values.as_raw().is_null() {
        return None;
    }
    let length = env.get_array_length(values).ok()?;
    let mut raw = vec![0 as jboolean; usize::try_from(length).ok()?];
    env.get_boolean_array_region(values, 0, &mut raw).ok()?;
    Some(raw.into_iter().map(|value| value != 0).collect())
}

pub fn return_string(env: &JNIEnv<'_>, value: &str) -> jni::sys::jstring {
    env.new_string(value).map_or(std::ptr::null_mut(), |java| {
        jni::objects::JObject::from(java).into_raw()
    })
}

pub fn return_optional_string(env: &JNIEnv<'_>, value: Option<String>) -> jni::sys::jstring {
    value.map_or(std::ptr::null_mut(), |text| return_string(env, &text))
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
