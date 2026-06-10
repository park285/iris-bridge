use std::sync::Mutex;

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

    pub fn open_handshake_session(&self, frame_json: &str, now_ms: i64) -> Result<String, Rejection> {
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

#[must_use]
pub fn into_handle(context: BridgeCoreContext) -> i64 {
    Box::into_raw(Box::new(context)) as i64
}

pub fn with_context<R>(
    handle: i64,
    body: impl FnOnce(&BridgeCoreContext) -> R,
) -> Result<R, Rejection> {
    let context = context_ref(handle)?;
    Ok(body(context))
}

fn context_ref<'a>(handle: i64) -> Result<&'a BridgeCoreContext, Rejection> {
    if handle == 0 {
        return Err(Rejection::new(
            ERROR_INVALID_HANDLE,
            "invalid BridgeCoreContext handle",
        ));
    }
    // SAFETY: handle is a pointer produced by `into_handle` (`Box::into_raw`) and
    // not yet freed by `drop_handle`. Kotlin owns exactly one create/destroy pair
    // per mux server lifetime, so the box outlives every dispatch call.
    Ok(unsafe { &*(handle as *const BridgeCoreContext) })
}

pub fn drop_handle(handle: i64) {
    if handle == 0 {
        return;
    }
    // SAFETY: reclaims the box created by `into_handle`. Called once on mux server
    // shutdown; Kotlin must not dispatch on this handle afterwards.
    drop(unsafe { Box::from_raw(handle as *mut BridgeCoreContext) });
}
