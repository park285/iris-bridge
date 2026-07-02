use std::collections::HashMap;
use std::sync::atomic::{AtomicI64, Ordering};
use std::sync::Arc;

use foldhash::fast::RandomState as FastHashBuilder;
use iris_bridge_core_lib::server::Rejection;
use parking_lot::RwLock;

pub const ERROR_INVALID_HANDLE: &str = "INVALID_HANDLE";

pub type Handle = i64;

type Entries<T> = HashMap<Handle, Arc<T>, FastHashBuilder>;

static NEXT_HANDLE: AtomicI64 = AtomicI64::new(1);

fn allocate_handle() -> Handle {
    NEXT_HANDLE.fetch_add(1, Ordering::Relaxed)
}

// 핸들은 raw pointer가 아니라 레지스트리 키다. remove 이후의 조회는
// "not found" 거부가 되고, in-flight 호출은 Arc가 수명을 보장하므로
// use-after-free가 구조적으로 불가능하다 (unsafe-closeout 게이트 준수).
pub struct HandleRegistry<T> {
    entries: RwLock<Entries<T>>,
}

impl<T> Default for HandleRegistry<T> {
    fn default() -> Self {
        Self::new()
    }
}

impl<T> HandleRegistry<T> {
    #[must_use]
    pub fn new() -> Self {
        Self {
            entries: RwLock::new(Entries::with_hasher(FastHashBuilder::default())),
        }
    }

    #[must_use]
    pub fn insert(&self, value: T) -> Handle {
        let handle = allocate_handle();
        self.entries.write().insert(handle, Arc::new(value));
        handle
    }

    pub fn with<R>(
        &self,
        handle: Handle,
        invalid_message: &'static str,
        body: impl FnOnce(&T) -> R,
    ) -> Result<R, Rejection> {
        let entry = self
            .entries
            .read()
            .get(&handle)
            .cloned()
            .ok_or_else(|| Rejection::new(ERROR_INVALID_HANDLE, invalid_message))?;
        Ok(body(&entry))
    }

    pub fn remove(&self, handle: Handle) {
        self.entries.write().remove(&handle);
    }
}
