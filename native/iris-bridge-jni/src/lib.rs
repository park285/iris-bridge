#![allow(unsafe_code, reason = "JNI export ABI 표면")]

mod dispatch;
mod exports;
mod handles;
mod marshal;
mod mux_handles;

use jni::sys::jint;

#[cfg(test)]
use crate::dispatch::*;
#[cfg(test)]
use crate::handles::{BridgeCoreContext, drop_handle, into_handle, with_context};

pub const ABI_VERSION: jint = 38;

#[cfg(test)]
#[path = "tests/lib.rs"]
mod tests;
