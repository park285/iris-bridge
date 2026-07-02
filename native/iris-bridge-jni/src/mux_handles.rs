use std::sync::OnceLock;

use iris_bridge_core_lib::mux_session::MuxSessionCore;
use iris_bridge_core_lib::server::Rejection;
use parking_lot::Mutex;

use crate::handle_registry::{Handle, HandleRegistry};

fn registry() -> &'static HandleRegistry<Mutex<MuxSessionCore>> {
    static REGISTRY: OnceLock<HandleRegistry<Mutex<MuxSessionCore>>> = OnceLock::new();
    REGISTRY.get_or_init(HandleRegistry::new)
}

#[must_use]
pub fn into_mux_session_handle(session: MuxSessionCore) -> Handle {
    registry().insert(Mutex::new(session))
}

pub fn with_mux_session<R>(
    handle: Handle,
    body: impl FnOnce(&mut MuxSessionCore) -> R,
) -> Result<R, Rejection> {
    registry().with(handle, "invalid MuxSessionCore handle", |session| {
        body(&mut session.lock())
    })
}

pub fn drop_mux_session_handle(handle: Handle) {
    registry().remove(handle);
}
