use jni::JNIEnv;
use jni::objects::JClass;

use crate::dispatch::dispatch_bridge_protocol_contract_json;
use crate::marshal::{catch_jstring, return_string};

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniProtocol_nativeProtocolContractJson<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jni::sys::jstring {
    catch_jstring(&mut env, |env| {
        return_string(env, &dispatch_bridge_protocol_contract_json())
    })
}
