use std::sync::OnceLock;

use iris_bridge_core_lib::handshake::should_require_bridge_handshake;
use iris_bridge_core_lib::server::{
    DedupeLedger, HandshakeServer, LeaseLedger, Rejection, SecurityMode,
};
use parking_lot::Mutex;

use crate::handle_registry::{Handle, HandleRegistry};

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
        socket_name: &str,
    ) -> Result<String, Rejection> {
        let mut session =
            HandshakeServer::new(self.bridge_token.clone(), socket_name, self.nonce_provider);
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
                iris_bridge_core_lib::server::ERROR_UNAUTHORIZED,
                iris_bridge_core_lib::handshake::AUTHENTICATION_FAILED,
            ))
        }
    }

    fn lock_sessions(&self) -> parking_lot::MutexGuard<'_, Vec<HandshakeServer<NonceProvider>>> {
        self.handshake_sessions.lock()
    }
}

fn registry() -> &'static HandleRegistry<BridgeCoreContext> {
    static REGISTRY: OnceLock<HandleRegistry<BridgeCoreContext>> = OnceLock::new();
    REGISTRY.get_or_init(HandleRegistry::new)
}

#[must_use]
pub fn into_handle(context: BridgeCoreContext) -> Handle {
    registry().insert(context)
}

pub fn with_context<R>(
    handle: Handle,
    body: impl FnOnce(&BridgeCoreContext) -> R,
) -> Result<R, Rejection> {
    registry().with(handle, "invalid BridgeCoreContext handle", body)
}

pub fn drop_handle(handle: Handle) {
    registry().remove(handle);
}
