use std::panic::{AssertUnwindSafe, catch_unwind};

use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::jboolean;

use crate::dispatch::{
    dispatch_reply_attachment_session_id, dispatch_reply_attachment_text_looks_like,
};
use crate::marshal::{catch_jstring, read_string, return_optional_string};

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniReply_nativeReplyAttachmentTextLooksLike<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    value: JString<'local>,
) -> jboolean {
    let outcome = catch_unwind(AssertUnwindSafe(|| {
        let value = read_string(&mut env, &value);
        dispatch_reply_attachment_text_looks_like(&value)
    }));
    jboolean::from(outcome.unwrap_or(false))
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniReply_nativeReplyAttachmentSessionId<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    attachment_text: JString<'local>,
) -> jni::sys::jstring {
    catch_jstring(&mut env, |env| {
        let attachment_text = read_string(env, &attachment_text);
        return_optional_string(env, dispatch_reply_attachment_session_id(&attachment_text))
    })
}
