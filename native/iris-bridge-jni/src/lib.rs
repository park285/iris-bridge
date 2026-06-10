#![allow(unsafe_code, reason = "JNI export ABI 표면")]

mod dispatch;
mod exports;
mod handles;
mod marshal;

use jni::sys::jint;

#[cfg(test)]
use crate::dispatch::*;
#[cfg(test)]
use crate::handles::BridgeCoreContext;

pub const ABI_VERSION: jint = 1;

#[cfg(test)]
#[path = "tests/lib.rs"]
mod tests;
