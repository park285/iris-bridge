use std::panic::{AssertUnwindSafe, catch_unwind};

use jni::objects::JClass;
use jni::sys::{jint, jlong};

use crate::dispatch::dispatch_restart_delay_ms;

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniPolicy_nativeServerRestartDelayMs(
    _env: jni::JNIEnv<'_>,
    _class: JClass<'_>,
    failure_count: jint,
) -> jlong {
    catch_unwind(AssertUnwindSafe(|| {
        dispatch_restart_delay_ms(failure_count)
    }))
    .unwrap_or(1_000)
}
