use super::*;
use crate::handles::MAX_HANDSHAKE_SESSIONS;
use iris_bridge_core::handshake::{HandshakeFrame, client_proof, server_proof};
use iris_bridge_core::lease::{ImageLease, ImageLeasePayload};
use serde_json::{Value, json};
use std::fs;
use std::path::PathBuf;
use std::time::{Duration, SystemTime, UNIX_EPOCH};

const TOKEN: &str = "bridge-token";
const SHA256_EMPTY: &str = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
const GOLDEN_MENTIONS_JSON: &str = r#"{"mentions":[{"user_id":123,"at":[1],"len":3}]}"#;
const GOLDEN_MENTIONS_HASH: &str =
    "cbdee567897480fbbfc4dc21159c79d8b2488d5e4152e8525aa469f35f55f3fc";
const GOLDEN_SIGNATURE: &str = "79d5a2a35924c010f1984eb53ad492aea6421150afa648e015ca841f2f82431a";
const CREATED_AT: i64 = 1_700_000_000_000;

fn temp_dir(name: &str) -> PathBuf {
    let id = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .expect("system clock after epoch")
        .as_nanos();
    let dir = std::env::temp_dir().join(format!("{name}-{}-{id}", std::process::id()));
    fs::create_dir(&dir).expect("create temp dir");
    dir
}

fn context() -> BridgeCoreContext {
    BridgeCoreContext::new(Some("production"), TOKEN, Some("true"))
}

fn parse_envelope(raw: &str) -> Value {
    serde_json::from_str(raw).expect("dispatch returned JSON")
}

fn assert_ok(raw: &str) -> Value {
    let envelope = parse_envelope(raw);
    assert_eq!(envelope["ok"], true);
    envelope
}

fn assert_error(raw: &str, error_code: &str, error: &str) {
    let envelope = parse_envelope(raw);
    assert_eq!(envelope["ok"], false);
    assert_eq!(envelope["errorCode"], error_code);
    assert_eq!(envelope["error"], error);
}

fn lease_payload(expires_at_epoch_ms: i64) -> ImageLeasePayload {
    ImageLeasePayload {
        version: 1,
        request_id: "req-1".to_owned(),
        room_id: 42,
        image_index: 0,
        canonical_path: "/data/iris-tmp/reply-images/req-1/image-0".to_owned(),
        sha256_hex: SHA256_EMPTY.to_owned(),
        byte_length: 1024,
        content_type: "image/png".to_owned(),
        last_modified_epoch_ms: 100,
        expires_at_epoch_ms,
        nonce: "nonce-abc".to_owned(),
    }
}

fn lease_fixture() -> (String, String) {
    let payload = lease_payload(10_000);
    let lease = ImageLease::issue(TOKEN, payload.clone());
    let leases_json = serde_json::to_string(&vec![lease]).expect("serialize lease fixture");
    let facts_json = json!([{
        "canonical_path": payload.canonical_path,
        "sha256_hex": payload.sha256_hex,
        "byte_length": payload.byte_length,
        "last_modified_epoch_ms": payload.last_modified_epoch_ms,
    }])
    .to_string();
    (leases_json, facts_json)
}

#[test]
fn validate_request_token_returns_success_envelope() {
    let envelope = assert_ok(&dispatch_validate_request_token(
        &context(),
        r#"{"action":"health","protocolVersion":1,"token":"bridge-token"}"#,
    ));
    assert_eq!(envelope.as_object().expect("object").len(), 1);
}

#[test]
fn validate_request_token_serializes_core_rejection_without_rewriting() {
    assert_error(
        &dispatch_validate_request_token(
            &context(),
            r#"{"action":"health","protocolVersion":1,"token":"wrong"}"#,
        ),
        "UNAUTHORIZED",
        "unauthorized bridge token",
    );
}

#[test]
fn validate_request_token_reports_malformed_json() {
    assert_error(
        &dispatch_validate_request_token(&context(), "not-json"),
        "BAD_REQUEST",
        "request JSON invalid",
    );
}

#[test]
fn handle_dispatch_rejects_zero_handle() {
    assert_error(
        &dispatch_validate_request_token_handle(
            0,
            r#"{"action":"health","protocolVersion":1,"token":"bridge-token"}"#,
        ),
        "INVALID_HANDLE",
        "invalid BridgeCoreContext handle",
    );
}

#[test]
fn verify_leases_accepts_valid_payload() {
    let (leases_json, facts_json) = lease_fixture();
    assert_ok(&dispatch_verify_leases(
        &context(),
        42,
        "req-1",
        &leases_json,
        &facts_json,
        1_000,
    ));
}

#[test]
fn verify_leases_serializes_replay_rejection() {
    let ctx = context();
    let (leases_json, facts_json) = lease_fixture();
    assert_ok(&dispatch_verify_leases(
        &ctx,
        42,
        "req-1",
        &leases_json,
        &facts_json,
        1_000,
    ));
    assert_error(
        &dispatch_verify_leases(&ctx, 42, "req-1", &leases_json, &facts_json, 1_001),
        "BAD_REQUEST",
        "image lease replay detected",
    );
}

#[test]
fn verify_leases_reports_malformed_facts_json() {
    let (leases_json, _) = lease_fixture();
    assert_error(
        &dispatch_verify_leases(&context(), 42, "req-1", &leases_json, "not-json", 1_000),
        "BAD_REQUEST",
        "path facts JSON invalid",
    );
}

#[test]
fn image_lease_rejection_kind_matches_core_exception_policy() {
    assert!(dispatch_image_lease_rejection_is_state_error(
        "image lease required"
    ));
    assert!(dispatch_image_lease_rejection_is_state_error(
        "image lease verification failed: EXPIRED"
    ));
    assert!(!dispatch_image_lease_rejection_is_state_error(
        "image lease last modified mismatch: /tmp/a expected=1 actual=2"
    ));
    assert!(!dispatch_image_lease_rejection_is_state_error(
        "image file not found: /tmp/a"
    ));
}

#[test]
fn send_block_reason_matches_core_discovery_policy() {
    let hooks = r#"[
        {"name":"ChatMediaSender#sendMultiple","installed":true},
        {"name":"ChatMediaSender#threadedEntry","installed":true},
        {"name":"ChatMediaSender#threadedInject","installed":false}
    ]"#;

    assert_eq!(
        dispatch_send_block_reason(false, "[]", 1, None, None),
        Some("bridge discovery hooks not installed".to_owned())
    );
    assert_eq!(
        dispatch_send_block_reason(false, "not-json", 1, None, None),
        Some("bridge discovery hooks not installed".to_owned())
    );
    assert_eq!(dispatch_send_block_reason(true, hooks, 1, None, None), None);
    assert_eq!(
        dispatch_send_block_reason(true, hooks, 1, Some(55), Some(2)),
        Some("bridge discovery hook not ready: ChatMediaSender#threadedInject".to_owned())
    );
    assert_eq!(
        dispatch_send_block_reason(true, "not-json", 1, None, None),
        Some("bridge discovery hook snapshot invalid".to_owned())
    );
}

#[test]
fn current_bridge_capabilities_dispatch_reports_rollout_and_readiness_reasons() {
    let envelope = assert_ok(&dispatch_current_bridge_capabilities(
        true, None, true, true, true, None, false, true,
    ));

    assert_eq!(envelope["inspectChatRoomSupported"], true);
    assert_eq!(envelope["inspectChatRoomReady"], true);
    assert_eq!(envelope["sendTextSupported"], true);
    assert_eq!(envelope["sendTextReady"], false);
    assert_eq!(envelope["sendTextReason"], "text bridge send_text disabled");
    assert_eq!(envelope["sendMarkdownReady"], true);
    assert_eq!(envelope.get("sendMarkdownReason"), None);

    let unavailable = assert_ok(&dispatch_current_bridge_capabilities(
        false,
        Some("registry unavailable"),
        true,
        true,
        true,
        None,
        true,
        true,
    ));

    assert_eq!(unavailable["inspectChatRoomSupported"], false);
    assert_eq!(unavailable["inspectChatRoomReason"], "registry unavailable");
    assert_eq!(unavailable["sendTextReason"], "registry unavailable");
}

#[test]
fn dedupe_admit_reports_fresh_in_flight_and_cached_states() {
    let ctx = context();
    let fresh = assert_ok(&dispatch_dedupe_admit(&ctx, "send_text:req-1", 0));
    assert_eq!(fresh["state"], "fresh");

    let in_flight = assert_ok(&dispatch_dedupe_admit(&ctx, "send_text:req-1", 1));
    assert_eq!(in_flight["state"], "inFlight");

    dispatch_dedupe_complete(&ctx, "send_text:req-1", r#"{"status":"sent"}"#, 2);
    let cached = assert_ok(&dispatch_dedupe_admit(&ctx, "send_text:req-1", 3));
    assert_eq!(cached["state"], "cached");
    assert_eq!(cached["responseJson"], r#"{"status":"sent"}"#);
}

#[test]
fn request_admission_reports_missing_request_id_for_side_effects() {
    assert_error(
        &dispatch_request_admission("send_text", None),
        "MISSING_REQUEST_ID",
        "requestId missing",
    );
    assert_ok(&dispatch_request_admission("send_text", Some("req-1")));
    assert_ok(&dispatch_request_admission("health", None));
}

#[test]
fn request_requires_request_id_matches_core_action_set() {
    assert!(dispatch_request_requires_request_id("send_text"));
    assert!(dispatch_request_requires_request_id("send_image"));
    assert!(dispatch_request_requires_request_id("send_markdown"));
    assert!(dispatch_request_requires_request_id("open_chatroom"));
    assert!(!dispatch_request_requires_request_id("health"));
    assert!(!dispatch_request_requires_request_id("inspect_chatroom"));
    assert!(!dispatch_request_requires_request_id(
        "snapshot_chatroom_members"
    ));
}

#[test]
fn request_dedupe_key_matches_core_admission_policy() {
    assert_eq!(
        dispatch_request_dedupe_key("send_text", Some("req-1")).as_deref(),
        Some("send_text:req-1")
    );
    assert_eq!(
        dispatch_request_dedupe_key("send_text", Some(" req-1 ")).as_deref(),
        Some("send_text: req-1 ")
    );
    assert_eq!(dispatch_request_dedupe_key("send_text", Some("  ")), None);
    assert_eq!(dispatch_request_dedupe_key("send_text", None), None);
    assert_eq!(dispatch_request_dedupe_key("health", Some("req-1")), None);
}

#[test]
fn validate_text_request_reports_core_verdict() {
    let valid = assert_ok(&dispatch_validate_text_request(
        Some(123),
        Some("hello"),
        false,
        Some("  {\"P\":{\"TP\":\"List\"}}  "),
        None,
    ));
    assert_eq!(valid["attachmentJson"], "{\"P\":{\"TP\":\"List\"}}");

    assert_ok(&dispatch_validate_text_request(
        Some(123),
        Some("hello"),
        true,
        Some("   "),
        None,
    ));

    assert_error(
        &dispatch_validate_text_request(
            Some(123),
            Some("hello"),
            true,
            Some("{\"P\":{\"TP\":\"List\"}}"),
            None,
        ),
        "BAD_REQUEST",
        "attachmentJson is only supported for send_text",
    );
}

#[test]
fn validate_image_paths_reports_static_path_policy() {
    assert_ok(&dispatch_validate_image_paths(
        r#"["/data/iris-tmp/reply-images/req-1/image-0.png"]"#,
        8,
        4096,
    ));

    assert_error(
        &dispatch_validate_image_paths(r#"["   "]"#, 8, 4096),
        "PATH_VALIDATION_FAILED",
        "blank image path",
    );

    assert_error(
        &dispatch_validate_image_paths("not-json", 8, 4096),
        "BAD_REQUEST",
        "image paths JSON invalid",
    );
}

#[test]
fn materialize_image_path_dispatch_returns_snapshot_and_revalidates_changes() {
    let root = temp_dir("iris-jni-image-path-root");
    let file = root.join("image.png");
    fs::write(&file, b"x").expect("write image");
    let root = root.canonicalize().expect("canonical root");
    let allowed_roots_json = json!([root.to_string_lossy()]).to_string();
    let path = file.to_string_lossy();

    let envelope = assert_ok(&dispatch_materialize_image_path(&path, &allowed_roots_json));
    let canonical = file
        .canonicalize()
        .expect("canonical file")
        .to_string_lossy()
        .into_owned();
    assert_eq!(envelope["canonicalPath"], canonical);
    assert_eq!(envelope["sizeBytes"], 1);
    let last_modified = envelope["lastModifiedEpochMs"]
        .as_i64()
        .expect("lastModifiedEpochMs");

    std::thread::sleep(Duration::from_millis(10));
    fs::write(&file, b"changed").expect("change image");

    assert_error(
        &dispatch_revalidate_image_path_snapshot(&canonical, &allowed_roots_json, 1, last_modified),
        "PATH_VALIDATION_FAILED",
        &format!("image file changed before send: {canonical}"),
    );

    fs::remove_dir_all(&root).expect("cleanup temp dir");
}

#[test]
fn image_lease_facts_dispatch_hashes_file_bytes_and_reports_missing_files() {
    let root = temp_dir("iris-jni-lease-facts-root");
    let file = root.join("image.png");
    fs::write(&file, b"abc").expect("write image");
    let path = file.to_string_lossy().into_owned();
    let paths_json = json!([path]).to_string();

    let envelope = assert_ok(&dispatch_image_lease_facts_json(&paths_json));
    let facts_json = envelope["factsJson"].as_str().expect("factsJson");
    let facts: Value = serde_json::from_str(facts_json).expect("facts JSON");

    assert_eq!(
        facts[0]["canonical_path"].as_str().expect("canonical_path"),
        file.to_string_lossy()
    );
    assert_eq!(
        facts[0]["sha256_hex"],
        "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
    );
    assert_eq!(facts[0]["byte_length"], 3);
    assert!(facts[0]["last_modified_epoch_ms"].as_i64().expect("mtime") > 0);

    let missing = root.join("missing.png").to_string_lossy().into_owned();
    assert_error(
        &dispatch_image_lease_facts_json(&json!([missing]).to_string()),
        "PATH_VALIDATION_FAILED",
        &format!(
            "image file not found: {}",
            root.join("missing.png").to_string_lossy()
        ),
    );
    assert_error(
        &dispatch_image_lease_facts_json("not-json"),
        "BAD_REQUEST",
        "image lease paths JSON invalid",
    );
    fs::remove_dir_all(&root).expect("cleanup temp dir");
}

#[test]
fn image_path_under_allowed_root_matches_core_separator_boundary() {
    let roots = r#"["/data/iris-tmp/reply-images"]"#;

    assert!(dispatch_image_path_under_allowed_root(
        "/data/iris-tmp/reply-images/req-1/image-0.png",
        roots,
    ));
    assert!(!dispatch_image_path_under_allowed_root(
        "/data/iris-tmp/reply-images-sibling/req-1/image-0.png",
        roots,
    ));
    assert!(!dispatch_image_path_under_allowed_root(
        "/data/iris-tmp/reply-images/req-1/image-0.png",
        "not-json",
    ));
    assert!(!dispatch_image_path_under_allowed_root(
        "/data/iris-tmp/reply-images/req-1/image-0.png",
        r#"[""]"#,
    ));
}

#[test]
fn classify_error_code_reports_native_bridge_failure_classification() {
    let path = assert_ok(&dispatch_classify_error_code(
        "image path validation timed out",
        true,
    ));
    assert_eq!(path["classifiedErrorCode"], "PATH_VALIDATION_FAILED");

    let timeout = assert_ok(&dispatch_classify_error_code(
        "CHATROOM OPEN DISPATCH TIMED OUT",
        false,
    ));
    assert_eq!(timeout["classifiedErrorCode"], "TIMEOUT");

    let bad_request = assert_ok(&dispatch_classify_error_code(
        "bad request from caller",
        true,
    ));
    assert_eq!(bad_request["classifiedErrorCode"], "BAD_REQUEST");

    let unauthorized = assert_ok(&dispatch_classify_error_code(
        "unauthorized bridge token",
        false,
    ));
    assert_eq!(unauthorized["classifiedErrorCode"], "UNAUTHORIZED");

    let send_failed = assert_ok(&dispatch_classify_error_code("send failed", false));
    assert_eq!(send_failed["classifiedErrorCode"], "SEND_FAILED");
}

#[test]
fn bridge_flag_truthiness_matches_core_policy() {
    for raw in ["true", "TRUE", " True ", "1", "on", "yes"] {
        assert!(dispatch_is_truthy_flag(raw), "{raw:?} must be truthy");
    }
    for raw in ["", "false", "0", "off", "no", "enabled", "yes please"] {
        assert!(!dispatch_is_truthy_flag(raw), "{raw:?} must be false");
    }
}

#[test]
fn restart_delay_dispatch_matches_bridge_server_backoff_policy() {
    assert_eq!(dispatch_restart_delay_ms(0), 1_000);
    assert_eq!(dispatch_restart_delay_ms(-3), 1_000);
    assert_eq!(dispatch_restart_delay_ms(1), 1_000);
    assert_eq!(dispatch_restart_delay_ms(2), 2_000);
    assert_eq!(dispatch_restart_delay_ms(3), 4_000);
    assert_eq!(dispatch_restart_delay_ms(6), 30_000);
    assert_eq!(dispatch_restart_delay_ms(99), 30_000);
}

#[test]
fn normalize_security_mode_raw_matches_core_policy() {
    assert_eq!(
        dispatch_normalize_security_mode(None),
        "production",
        "missing env defaults to production",
    );
    assert_eq!(
        dispatch_normalize_security_mode(Some("unknown")),
        "production",
        "unknown env defaults to production",
    );
    assert_eq!(
        dispatch_normalize_security_mode(Some(" Development ")),
        "development",
        "development env is trimmed and case-insensitive",
    );
    assert_eq!(
        dispatch_normalize_security_mode(Some("dev")),
        "development",
        "dev alias remains supported",
    );
}

#[test]
fn allowed_peer_uids_match_core_security_mode_policy() {
    assert_eq!(
        dispatch_allowed_peer_uids(Some("production"), None),
        vec![0],
    );
    assert_eq!(
        dispatch_allowed_peer_uids(Some("development"), None),
        vec![0, 2000],
    );
    assert_eq!(
        dispatch_allowed_peer_uids(Some("dev"), Some("2000, 3000,invalid, 0")),
        vec![0, 2000, 3000],
    );
}

#[test]
fn handshake_server_proof_binds_caller_socket_name() {
    let ctx = context();
    let hello = r#"{"type":"hello","protocolVersion":1,"clientNonce":"client-1","socketName":"iris-image-bridge-mux","timestampMs":1}"#;
    let response = assert_ok(&dispatch_handshake_on_hello(
        &ctx,
        hello,
        1,
        "iris-image-bridge-mux",
    ));
    let frame: HandshakeFrame =
        serde_json::from_str(response["frameJson"].as_str().expect("frame json"))
            .expect("server frame");
    let expected = server_proof(
        TOKEN,
        "client-1",
        frame.server_nonce.as_deref().expect("server nonce"),
        "iris-image-bridge-mux",
    );
    assert_eq!(frame.proof.as_deref(), Some(expected.as_str()));
}

#[test]
fn handshake_registry_keeps_concurrent_sessions_independent() {
    let ctx = context();
    let hello_1 = r#"{"type":"hello","protocolVersion":1,"clientNonce":"client-1","socketName":"@mux","timestampMs":1}"#;
    let hello_2 = r#"{"type":"hello","protocolVersion":1,"clientNonce":"client-2","socketName":"@mux","timestampMs":2}"#;

    let response_1 = assert_ok(&dispatch_handshake_on_hello(&ctx, hello_1, 1, "@mux"));
    let frame_1: HandshakeFrame =
        serde_json::from_str(response_1["frameJson"].as_str().expect("frame json"))
            .expect("server frame 1");
    let response_2 = assert_ok(&dispatch_handshake_on_hello(&ctx, hello_2, 2, "@mux"));
    let frame_2: HandshakeFrame =
        serde_json::from_str(response_2["frameJson"].as_str().expect("frame json"))
            .expect("server frame 2");

    let proof_2 = client_proof(
        TOKEN,
        "client-2",
        frame_2.server_nonce.as_deref().expect("server nonce 2"),
    );
    assert_ok(&dispatch_handshake_on_client_proof(
        &ctx,
        &format!(r#"{{"type":"client_proof","protocolVersion":1,"proof":"{proof_2}"}}"#),
    ));

    let proof_1 = client_proof(
        TOKEN,
        "client-1",
        frame_1.server_nonce.as_deref().expect("server nonce 1"),
    );
    assert_ok(&dispatch_handshake_on_client_proof(
        &ctx,
        &format!(r#"{{"type":"client_proof","protocolVersion":1,"proof":"{proof_1}"}}"#),
    ));
}

#[test]
fn reply_hook_sign_and_verify_match_core_golden() {
    let signature =
        dispatch_reply_hook_sign(TOKEN, 42, "hello **world**", "req-7", CREATED_AT, None)
            .expect("signable");
    assert_eq!(signature, GOLDEN_SIGNATURE);
    assert!(dispatch_reply_hook_verify(
        TOKEN,
        42,
        "hello **world**",
        Some("req-7"),
        Some(CREATED_AT),
        None,
        Some(GOLDEN_SIGNATURE),
        CREATED_AT + 120_000,
    ));
}

#[test]
fn mentions_hash_exports_match_core_golden() {
    assert_eq!(
        dispatch_mentions_hash_from_json(Some(GOLDEN_MENTIONS_JSON)).as_deref(),
        Some(GOLDEN_MENTIONS_HASH),
    );
    assert_eq!(
        dispatch_mentions_hash_from_attachment(Some(
            r#"{"callingPkg":"com.kakao.talk","mentions":[{"user_id":123,"at":[1],"len":3}]}"#,
        ))
        .as_deref(),
        Some(GOLDEN_MENTIONS_HASH),
    );
}

#[test]
fn panic_is_absorbed_into_error_envelope() {
    assert_error(
        &json_catch_unwind(|| -> DispatchResult { panic!("boom") }),
        "PANIC",
        "panic across JNI boundary",
    );
}

#[test]
fn require_handshake_reflects_resolved_policy() {
    assert!(BridgeCoreContext::new(Some("production"), TOKEN, Some("true")).require_handshake);
    assert!(!BridgeCoreContext::new(Some("development"), TOKEN, None).require_handshake);
}

#[test]
fn handshake_registry_evicts_oldest_session_beyond_capacity() {
    let ctx = context();
    let oldest_hello = r#"{"type":"hello","protocolVersion":1,"clientNonce":"client-oldest","socketName":"@mux","timestampMs":1}"#;
    let oldest = assert_ok(&dispatch_handshake_on_hello(&ctx, oldest_hello, 1, "@mux"));
    let oldest_frame: HandshakeFrame =
        serde_json::from_str(oldest["frameJson"].as_str().expect("frame json"))
            .expect("oldest server frame");

    for index in 0..MAX_HANDSHAKE_SESSIONS {
        let hello = format!(
            r#"{{"type":"hello","protocolVersion":1,"clientNonce":"client-{index}","socketName":"@mux","timestampMs":2}}"#
        );
        assert_ok(&dispatch_handshake_on_hello(&ctx, &hello, 2, "@mux"));
    }

    let oldest_proof = client_proof(
        TOKEN,
        "client-oldest",
        oldest_frame.server_nonce.as_deref().expect("server nonce"),
    );
    assert_error(
        &dispatch_handshake_on_client_proof(
            &ctx,
            &format!(r#"{{"type":"client_proof","protocolVersion":1,"proof":"{oldest_proof}"}}"#),
        ),
        "UNAUTHORIZED",
        "bridge authentication failed",
    );
}

#[test]
fn dispatch_after_destroy_is_rejected_as_invalid_handle() {
    let handle = into_handle(context());
    let live = with_context(handle, |ctx| dispatch_dedupe_admit(ctx, "send:req-live", 1))
        .expect("live handle dispatches");
    assert_ok(&live);

    drop_handle(handle);

    let stale = match with_context(handle, |ctx| dispatch_dedupe_admit(ctx, "send:req-live", 2)) {
        Ok(envelope) => envelope,
        Err(rejection) => invalid_handle_envelope(&rejection),
    };
    assert_error(&stale, "INVALID_HANDLE", "invalid BridgeCoreContext handle");
}
