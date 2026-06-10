use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::jboolean;
use std::panic::{AssertUnwindSafe, catch_unwind};

use crate::dispatch::dispatch_is_truthy_flag;
use crate::marshal::read_string;

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniPolicy_nativeIsTruthyFlag<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    raw: JString<'local>,
) -> jboolean {
    let outcome = catch_unwind(AssertUnwindSafe(|| {
        let raw = read_string(&mut env, &raw);
        dispatch_is_truthy_flag(&raw)
    }));
    jboolean::from(outcome.unwrap_or(false))
}
