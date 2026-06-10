use std::panic::{AssertUnwindSafe, catch_unwind};

use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::{jintArray, jsize};

use crate::dispatch::dispatch_allowed_peer_uids;
use crate::marshal::read_optional_string;

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniPolicy_nativeAllowedPeerUids<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    security_mode_raw: JString<'local>,
    extra_uids_raw: JString<'local>,
) -> jintArray {
    catch_unwind(AssertUnwindSafe(|| {
        let security_mode_raw = read_optional_string(&mut env, &security_mode_raw);
        let extra_uids_raw = read_optional_string(&mut env, &extra_uids_raw);
        let allowed =
            dispatch_allowed_peer_uids(security_mode_raw.as_deref(), extra_uids_raw.as_deref());
        let Ok(length) = jsize::try_from(allowed.len()) else {
            return std::ptr::null_mut();
        };
        let Ok(array) = env.new_int_array(length) else {
            return std::ptr::null_mut();
        };
        if env.set_int_array_region(&array, 0, &allowed).is_err() {
            return std::ptr::null_mut();
        }
        array.into_raw()
    }))
    .unwrap_or(std::ptr::null_mut())
}
