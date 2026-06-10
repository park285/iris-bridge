use std::collections::HashMap;
use std::sync::atomic::{AtomicI64, Ordering};
use std::sync::{Arc, Mutex, OnceLock};

use iris_bridge_core::handshake::should_require_bridge_handshake;
use iris_bridge_core::server::{
    DedupeLedger, HandshakeServer, LeaseLedger, Rejection, SecurityMode,
};

const BRIDGE_MUX_SOCKET_NAME: &str = "@iris-image-bridge-mux";

pub const ERROR_INVALID_HANDLE: &str = "INVALID_HANDLE";

pub const MAX_HANDSHAKE_SESSIONS: usize = 64;

pub type NonceProvider = fn() -> String;

fn random_nonce() -> String {
    let mut bytes = [0_u8; 32];
    getrandom::fill(&mut bytes).expect("OS CSPRNG unavailable for bridge handshake nonce");
    hex::encode(bytes)
}

pub struct BridgeCoreContext {
    pub bridge_token: String,
    pub security_mode: SecurityMode,
    pub require_handshake: bool,
    pub lease_ledger: LeaseLedger,
    pub dedupe_ledger: DedupeLedger,
    handshake_sessions: Mutex<Vec<HandshakeServer<NonceProvider>>>,
    nonce_provider: NonceProvider,
}

impl BridgeCoreContext {
    #[must_use]
    pub fn new(
        security_mode_raw: Option<&str>,
        bridge_token: &str,
        require_handshake_raw: Option<&str>,
    ) -> Self {
        Self::with_nonce_provider(
            security_mode_raw,
            bridge_token,
            require_handshake_raw,
            random_nonce,
        )
    }

    #[must_use]
    pub fn with_nonce_provider(
        security_mode_raw: Option<&str>,
        bridge_token: &str,
        require_handshake_raw: Option<&str>,
        nonce_provider: NonceProvider,
    ) -> Self {
        Self {
            bridge_token: bridge_token.to_owned(),
            security_mode: SecurityMode::from_raw(security_mode_raw),
            require_handshake: should_require_bridge_handshake(
                security_mode_raw,
                require_handshake_raw,
            ),
            lease_ledger: LeaseLedger::new(bridge_token),
            dedupe_ledger: DedupeLedger::with_defaults(),
            handshake_sessions: Mutex::new(Vec::new()),
            nonce_provider,
        }
    }

    pub fn open_handshake_session(
        &self,
        frame_json: &str,
        now_ms: i64,
    ) -> Result<String, Rejection> {
        let mut session = HandshakeServer::new(
            self.bridge_token.clone(),
            BRIDGE_MUX_SOCKET_NAME,
            self.nonce_provider,
        );
        let frame = session.on_hello(frame_json, now_ms)?;
        {
            let mut sessions = self.lock_sessions();
            if sessions.len() >= MAX_HANDSHAKE_SESSIONS {
                sessions.remove(0);
            }
            sessions.push(session);
        }
        Ok(frame)
    }

    pub fn resolve_client_proof(&self, frame_json: &str) -> Result<(), Rejection> {
        let matched = {
            let mut sessions = self.lock_sessions();
            (0..sessions.len())
                .find(|&index| sessions[index].on_client_proof(frame_json).is_ok())
                .inspect(|&index| {
                    sessions.swap_remove(index);
                })
                .is_some()
        };
        if matched {
            Ok(())
        } else {
            Err(Rejection::new(
                iris_bridge_core::server::ERROR_UNAUTHORIZED,
                iris_bridge_core::handshake::AUTHENTICATION_FAILED,
            ))
        }
    }

    fn lock_sessions(&self) -> std::sync::MutexGuard<'_, Vec<HandshakeServer<NonceProvider>>> {
        self.handshake_sessions
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner)
    }
}

// 핸들은 raw pointer가 아니라 레지스트리 키다. destroy 이후의 dispatch는
// "not found" 거부가 되고, in-flight 호출은 Arc가 수명을 보장하므로
// use-after-free가 구조적으로 불가능하다 (unsafe-closeout 게이트 준수).
fn registry() -> &'static Mutex<HashMap<i64, Arc<BridgeCoreContext>>> {
    static REGISTRY: OnceLock<Mutex<HashMap<i64, Arc<BridgeCoreContext>>>> = OnceLock::new();
    REGISTRY.get_or_init(|| Mutex::new(HashMap::new()))
}

static NEXT_HANDLE: AtomicI64 = AtomicI64::new(1);

fn lock_registry() -> std::sync::MutexGuard<'static, HashMap<i64, Arc<BridgeCoreContext>>> {
    registry()
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner)
}

#[must_use]
pub fn into_handle(context: BridgeCoreContext) -> i64 {
    let handle = NEXT_HANDLE.fetch_add(1, Ordering::Relaxed);
    lock_registry().insert(handle, Arc::new(context));
    handle
}

pub fn with_context<R>(
    handle: i64,
    body: impl FnOnce(&BridgeCoreContext) -> R,
) -> Result<R, Rejection> {
    let context = lock_registry()
        .get(&handle)
        .cloned()
        .ok_or_else(|| Rejection::new(ERROR_INVALID_HANDLE, "invalid BridgeCoreContext handle"))?;
    Ok(body(&context))
}

pub fn drop_handle(handle: i64) {
    lock_registry().remove(&handle);
}
