use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jlong};

use crate::dispatch::{dispatch_validate_text_request, invalid_handle_envelope};
use crate::handles::with_context;
use crate::marshal::{catch_jstring, read_optional_string, return_string};

#[allow(
    clippy::too_many_arguments,
    reason = "Kotlin BridgeCoreJniRequest.nativeValidateTextRequest external fun과 동결된 인자 계약"
)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniRequest_nativeValidateTextRequest<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    has_room_id: jboolean,
    room_id: jlong,
    message: JString<'local>,
    markdown: jboolean,
    attachment_json: JString<'local>,
    mentions_json: JString<'local>,
) -> jni::sys::jstring {
    catch_jstring(&mut env, |env| {
        let room_id = (has_room_id != 0).then_some(room_id);
        let message = read_optional_string(env, &message);
        let attachment = read_optional_string(env, &attachment_json);
        let mentions = read_optional_string(env, &mentions_json);
        let envelope = match with_context(handle, |_| {
            dispatch_validate_text_request(
                room_id,
                message.as_deref(),
                markdown != 0,
                attachment.as_deref(),
                mentions.as_deref(),
            )
        }) {
            Ok(envelope) => envelope,
            Err(rejection) => invalid_handle_envelope(&rejection),
        };
        return_string(env, &envelope)
    })
}
