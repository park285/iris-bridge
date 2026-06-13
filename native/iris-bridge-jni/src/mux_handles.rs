use std::collections::HashMap;
use std::sync::{Arc, Mutex, OnceLock, RwLock};

use iris_bridge_core::mux_session::MuxSessionCore;
use iris_bridge_core::server::Rejection;

use crate::handles::{ERROR_INVALID_HANDLE, allocate_handle};

fn registry() -> &'static RwLock<HashMap<i64, Arc<Mutex<MuxSessionCore>>>> {
    static REGISTRY: OnceLock<RwLock<HashMap<i64, Arc<Mutex<MuxSessionCore>>>>> = OnceLock::new();
    REGISTRY.get_or_init(|| RwLock::new(HashMap::new()))
}

fn read_registry() -> std::sync::RwLockReadGuard<'static, HashMap<i64, Arc<Mutex<MuxSessionCore>>>>
{
    registry()
        .read()
        .unwrap_or_else(std::sync::PoisonError::into_inner)
}

fn write_registry() -> std::sync::RwLockWriteGuard<'static, HashMap<i64, Arc<Mutex<MuxSessionCore>>>>
{
    registry()
        .write()
        .unwrap_or_else(std::sync::PoisonError::into_inner)
}

#[must_use]
pub fn into_mux_session_handle(session: MuxSessionCore) -> i64 {
    let handle = allocate_handle();
    write_registry().insert(handle, Arc::new(Mutex::new(session)));
    handle
}

pub fn with_mux_session<R>(
    handle: i64,
    body: impl FnOnce(&mut MuxSessionCore) -> R,
) -> Result<R, Rejection> {
    let session = read_registry()
        .get(&handle)
        .cloned()
        .ok_or_else(|| Rejection::new(ERROR_INVALID_HANDLE, "invalid MuxSessionCore handle"))?;
    let mut session = session
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner);
    Ok(body(&mut session))
}

pub fn drop_mux_session_handle(handle: i64) {
    write_registry().remove(&handle);
}
