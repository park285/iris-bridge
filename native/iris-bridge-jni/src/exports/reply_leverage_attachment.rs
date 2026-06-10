use jni::JNIEnv;
use jni::objects::{JClass, JString};

use crate::dispatch::dispatch_merge_reply_leverage_attachment;
use crate::marshal::{catch_jstring, read_optional_string, read_string, return_string};

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniReply_nativeMergeReplyLeverageAttachment<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    generated_attachment: JString<'local>,
    raw_attachment: JString<'local>,
) -> jni::sys::jstring {
    catch_jstring(&mut env, |env| {
        let generated = read_optional_string(env, &generated_attachment);
        let raw = read_string(env, &raw_attachment);
        return_string(
            env,
            &dispatch_merge_reply_leverage_attachment(generated.as_deref(), &raw),
        )
    })
}
