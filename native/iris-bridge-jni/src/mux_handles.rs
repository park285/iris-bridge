use std::collections::HashMap;
use std::sync::{Arc, OnceLock};

use foldhash::fast::RandomState as FastHashBuilder;
use iris_bridge_core_lib::mux_session::MuxSessionCore;
use iris_bridge_core_lib::server::Rejection;
use parking_lot::{Mutex, RwLock};

use crate::handles::{ERROR_INVALID_HANDLE, allocate_handle};

type Handle = i64;
type MuxRegistry = HashMap<Handle, Arc<Mutex<MuxSessionCore>>, FastHashBuilder>;

fn registry() -> &'static RwLock<MuxRegistry> {
    static REGISTRY: OnceLock<RwLock<MuxRegistry>> = OnceLock::new();
    REGISTRY.get_or_init(|| RwLock::new(MuxRegistry::with_hasher(FastHashBuilder::default())))
}

fn read_registry() -> parking_lot::RwLockReadGuard<'static, MuxRegistry> {
    registry().read()
}

fn write_registry() -> parking_lot::RwLockWriteGuard<'static, MuxRegistry> {
    registry().write()
}

#[must_use]
pub fn into_mux_session_handle(session: MuxSessionCore) -> Handle {
    let handle = allocate_handle();
    write_registry().insert(handle, Arc::new(Mutex::new(session)));
    handle
}

pub fn with_mux_session<R>(
    handle: Handle,
    body: impl FnOnce(&mut MuxSessionCore) -> R,
) -> Result<R, Rejection> {
    let session = read_registry()
        .get(&handle)
        .cloned()
        .ok_or_else(|| Rejection::new(ERROR_INVALID_HANDLE, "invalid MuxSessionCore handle"))?;
    let mut session = session.lock();
    Ok(body(&mut session))
}

pub fn drop_mux_session_handle(handle: Handle) {
    write_registry().remove(&handle);
}
