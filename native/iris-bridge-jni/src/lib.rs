#![allow(unsafe_code, reason = "JNI export ABI 표면")]

mod dispatch;
mod exports;
mod handles;
mod marshal;
mod mux_handles;

use jni::sys::jint;

#[cfg(feature = "bench")]
pub mod bench_api {
    pub use crate::dispatch::{
        dispatch_op, dispatch_request_requires_request_id, dispatch_validate_request_token_handle,
    };
    pub use crate::handles::{BridgeCoreContext, drop_handle, into_handle, with_context};
    pub use crate::mux_handles::{
        drop_mux_session_handle, into_mux_session_handle, with_mux_session,
    };
    pub use iris_bridge_core_lib::mux_session::MuxSessionCore;
}

#[cfg(test)]
use crate::dispatch::*;
#[cfg(test)]
use crate::handles::{BridgeCoreContext, drop_handle, into_handle, with_context};

pub const ABI_VERSION: jint = iris_bridge_core_lib::protocol::ABI_VERSION;

#[cfg(test)]
#[path = "tests/lib.rs"]
mod tests;
