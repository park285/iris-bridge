use jni::JNIEnv;
use jni::objects::{JClass, JString};

use crate::dispatch::{
    dispatch_merge_reply_mention_attachment, dispatch_reply_mention_attachment_or_null,
};
use crate::marshal::{catch_jstring, read_string, return_optional_string};

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniReply_nativeReplyMentionAttachmentOrNull<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    attachment_text: JString<'local>,
) -> jni::sys::jstring {
    catch_jstring(&mut env, |env| {
        let attachment_text = read_string(env, &attachment_text);
        return_optional_string(
            env,
            dispatch_reply_mention_attachment_or_null(&attachment_text),
        )
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniReply_nativeMergeReplyMentionAttachment<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    target_attachment_text: JString<'local>,
    mention_attachment_text: JString<'local>,
) -> jni::sys::jstring {
    catch_jstring(&mut env, |env| {
        let target = read_string(env, &target_attachment_text);
        let mention = read_string(env, &mention_attachment_text);
        return_optional_string(
            env,
            dispatch_merge_reply_mention_attachment(&target, &mention),
        )
    })
}
