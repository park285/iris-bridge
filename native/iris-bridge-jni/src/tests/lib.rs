use super::*;
use crate::handles::MAX_HANDSHAKE_SESSIONS;
use crate::mux_handles::{drop_mux_session_handle, into_mux_session_handle, with_mux_session};
use iris_bridge_core_lib::handshake::{HandshakeFrame, client_proof, server_proof};
use iris_bridge_core_lib::lease::{ImageLease, ImageLeasePayload};
use iris_bridge_core_lib::mux_session::MuxSessionCore;
use iris_bridge_core_lib::server::capabilities::{
    BridgeCapabilitiesInput, BridgeReadinessInput, TextBridgeRollout, TextCapability,
};
use iris_bridge_core_lib::server::reply_hook::ReplyHookVerifyInput;
use serde_json::{Value, json};
use std::fs;
use std::time::Duration;
use tempfile::TempDir;

const TOKEN: &str = "bridge-token";
const SHA256_EMPTY: &str = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
const GOLDEN_MENTIONS_JSON: &str = r#"{"mentions":[{"user_id":123,"at":[1],"len":3}]}"#;
const GOLDEN_MENTIONS_HASH: &str =
    "cbdee567897480fbbfc4dc21159c79d8b2488d5e4152e8525aa469f35f55f3fc";
const GOLDEN_SIGNATURE: &str = "79d5a2a35924c010f1984eb53ad492aea6421150afa648e015ca841f2f82431a";
const CREATED_AT: i64 = 1_700_000_000_000;

fn temp_dir(name: &str) -> TempDir {
    tempfile::Builder::new()
        .prefix(name)
        .tempdir()
        .expect("create temp dir")
}

fn context() -> BridgeCoreContext {
    BridgeCoreContext::new(Some("production"), TOKEN, Some("true"))
}

fn capabilities_input() -> BridgeCapabilitiesInput {
    BridgeCapabilitiesInput {
        readiness: BridgeReadinessInput {
            registry_available: true,
            registry_error: None,
            spec_ready: true,
        },
        notification_action_supported: true,
        text_send_capability: Some(TextCapability {
            supported: true,
            ready: true,
            reason: None,
        }),
        rollout: TextBridgeRollout {
            send_text_enabled: true,
            send_markdown_enabled: true,
        },
    }
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

#[test]
fn protocol_contract_dispatch_returns_core_contract_json() {
    let envelope = assert_ok(&dispatch_bridge_protocol_contract_json());
    let contract_json = envelope["contractJson"]
        .as_str()
        .expect("contractJson string");
    assert_eq!(
        contract_json,
        iris_bridge_core_lib::protocol::bridge_protocol_contract_json()
    );
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
        r#"{"action":"send_text","protocolVersion":2,"token":"bridge-token"}"#,
    ));
    assert_eq!(envelope.as_object().expect("object").len(), 1);
}

#[test]
fn validate_request_token_allows_token_exempt_health_without_token() {
    let envelope = assert_ok(&dispatch_validate_request_token(
        &context(),
        r#"{"action":"health","protocolVersion":2}"#,
    ));
    assert_eq!(envelope.as_object().expect("object").len(), 1);
}

#[test]
fn validate_request_token_serializes_core_rejection_without_rewriting() {
    assert_error(
        &dispatch_validate_request_token(
            &context(),
            r#"{"action":"send_text","protocolVersion":2,"token":"wrong"}"#,
        ),
        "UNAUTHORIZED",
        "unauthorized bridge token",
    );
}

#[test]
fn validate_request_token_requires_token_for_unknown_actions() {
    assert_error(
        &dispatch_validate_request_token(
            &context(),
            r#"{"action":"future_side_effect","protocolVersion":2}"#,
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
            r#"{"action":"health","protocolVersion":2,"token":"bridge-token"}"#,
        ),
        "INVALID_HANDLE",
        "invalid BridgeCoreContext handle",
    );
}

fn mux_request_frame(correlation_id: &str) -> String {
    json!({
        "type": "request",
        "muxVersion": 2,
        "correlationId": correlation_id,
        "request": {},
    })
    .to_string()
}

#[test]
fn mux_session_dispatch_exposes_commands_and_duplicate_fail_closed_rule() {
    let handle = assert_ok(&dispatch_mux_session_create(1))["handle"]
        .as_i64()
        .expect("mux session handle");

    let first = assert_ok(&dispatch_mux_session_on_frame(
        handle,
        &mux_request_frame("same"),
    ));
    assert_eq!(first["command"], "dispatch");
    assert_eq!(first["correlationId"], "same");

    let duplicate = assert_ok(&dispatch_mux_session_on_frame(
        handle,
        &mux_request_frame("same"),
    ));
    assert_eq!(duplicate["command"], "writeBadRequest");
    assert_eq!(duplicate["correlationId"], "same");
    assert_eq!(duplicate["message"], "duplicate mux correlation id");

    let busy = assert_ok(&dispatch_mux_session_on_frame(
        handle,
        &mux_request_frame("other"),
    ));
    assert_eq!(busy["command"], "writeBusy");
    assert_eq!(busy["correlationId"], "other");

    assert_ok(&dispatch_mux_session_on_request_completed(handle, "same"));

    let after_completion = assert_ok(&dispatch_mux_session_on_frame(
        handle,
        &mux_request_frame("other"),
    ));
    assert_eq!(after_completion["command"], "dispatch");
    assert_eq!(after_completion["correlationId"], "other");

    assert_ok(&dispatch_mux_session_destroy(handle));
}

#[test]
fn mux_session_dispatch_tracks_cancel_and_executor_rejection() {
    let handle = assert_ok(&dispatch_mux_session_create(2))["handle"]
        .as_i64()
        .expect("mux session handle");

    assert_eq!(
        assert_ok(&dispatch_mux_session_on_frame(
            handle,
            &mux_request_frame("c1")
        ))["command"],
        "dispatch"
    );
    let cancel = assert_ok(&dispatch_mux_session_on_frame(
        handle,
        r#"{"type":"cancel","muxVersion":2,"correlationId":"c1"}"#,
    ));
    assert_eq!(cancel["command"], "markCancelled");
    assert_eq!(
        assert_ok(&dispatch_mux_session_is_cancelled(handle, "c1"))["cancelled"],
        true
    );

    let rejected = assert_ok(&dispatch_mux_session_on_executor_rejected(handle, "c1"));
    assert_eq!(rejected["command"], "writeBusy");
    assert_eq!(rejected["correlationId"], "c1");
    assert_eq!(
        assert_ok(&dispatch_mux_session_is_cancelled(handle, "c1"))["cancelled"],
        false
    );

    assert_ok(&dispatch_mux_session_destroy(handle));
}

#[test]
fn mux_session_dispatch_rejects_invalid_inputs_and_handles() {
    assert_error(
        &dispatch_mux_session_create(-1),
        "BAD_REQUEST",
        "maxInFlight must be non-negative",
    );
    assert_error(
        &dispatch_mux_session_on_frame(0, &mux_request_frame("c1")),
        "INVALID_HANDLE",
        "invalid MuxSessionCore handle",
    );
    let handle = assert_ok(&dispatch_mux_session_create(1))["handle"]
        .as_i64()
        .expect("mux session handle");
    assert_error(
        &dispatch_mux_session_on_frame(handle, "not-json"),
        "BAD_REQUEST",
        "invalid mux frame json",
    );
    assert_ok(&dispatch_mux_session_destroy(handle));
}

#[test]
fn media_content_dispatch_normalizes_and_selects_message_kind() {
    let normalized = assert_ok(&dispatch_normalize_media_content_types(
        1,
        r#"[" Video/MP4 ; charset=utf-8 "]"#,
    ));
    assert_eq!(normalized["normalizedContentTypes"], json!(["video/mp4"]));

    let kind = assert_ok(&dispatch_media_message_kind(1, r#"["video/mp4"]"#));
    assert_eq!(kind["messageKind"], "video");
}

#[test]
fn media_content_dispatch_normalizes_lease_content_types_by_index() {
    let normalized = assert_ok(&dispatch_normalize_media_content_types_from_leases(
        3,
        r#"[
            {"imageIndex":2,"contentType":" IMAGE/JPEG "},
            {"imageIndex":0,"contentType":" Video/MP4 ; charset=utf-8 "}
        ]"#,
    ));

    assert_eq!(
        normalized["normalizedContentTypes"],
        json!(["video/mp4", "", "image/jpeg"])
    );
}

#[test]
fn media_content_dispatch_preserves_kotlin_rejection_messages() {
    assert_error(
        &dispatch_normalize_media_content_types(2, r#"["image/png"]"#),
        "BAD_REQUEST",
        "media content type count 1 does not match image count 2",
    );
    assert_error(
        &dispatch_media_message_kind(2, r#"["video/mp4","image/png"]"#),
        "BAD_REQUEST",
        "multiple video media send is not supported",
    );
    assert_error(
        &dispatch_validate_share_manager_image_media(r#"["video/mp4"]"#),
        "BAD_REQUEST",
        "video media send is not supported on ShareManager image path",
    );
}

#[test]
fn member_profile_user_ids_dispatch_filters_and_deduplicates_ids() {
    let outcome = assert_ok(&dispatch_member_profile_user_ids(
        r#"{
            "memberIds":[90001,0,90002,90001],
            "memberHints":[{"userId":7},{"userId":8}]
        }"#,
    ));

    assert_eq!(outcome["userIds"], json!([90001, 90002]));
}

#[test]
fn member_profile_user_ids_dispatch_uses_hints_when_ids_are_empty() {
    let outcome = assert_ok(&dispatch_member_profile_user_ids(
        r#"{
            "memberIds":[],
            "memberHints":[{"userId":7},{"userId":-1},{"userId":9},{"userId":7}]
        }"#,
    ));

    assert_eq!(outcome["userIds"], json!([7, 9]));
}

#[test]
fn member_profile_payload_dispatch_builds_sorted_payload_json() {
    let outcome = assert_ok(&dispatch_member_profile_payload(
        r#"{
            "profiles":[
                {"userId":90002,"nickname":"Member Beta","profileImageUrl":"https://example.test/p.png"},
                {"userId":90001,"nickname":"Member Alpha"}
            ]
        }"#,
    ));
    let payload: Value =
        serde_json::from_str(outcome["payloadJson"].as_str().expect("payloadJson"))
            .expect("payload JSON");

    assert_eq!(payload["members"][0]["userId"], json!(90001));
    assert_eq!(
        payload["members"][1]["profileImageUrl"],
        json!("https://example.test/p.png")
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
fn send_block_reason_snapshot_dispatch_matches_core_discovery_policy() {
    let hook_names = vec![
        "ChatMediaSender#sendMultiple".to_owned(),
        "ChatMediaSender#threadedEntry".to_owned(),
        "ChatMediaSender#threadedInject".to_owned(),
    ];
    let hook_installed = vec![true, true, false];

    assert_eq!(
        dispatch_send_block_reason_from_snapshot(false, &[], &[], 1, None, None),
        Some("bridge discovery hooks not installed".to_owned())
    );
    assert_eq!(
        dispatch_send_block_reason_from_snapshot(true, &hook_names, &hook_installed, 1, None, None),
        None
    );
    assert_eq!(
        dispatch_send_block_reason_from_snapshot(
            true,
            &hook_names,
            &hook_installed,
            1,
            Some(55),
            Some(2),
        ),
        Some("bridge discovery hook not ready: ChatMediaSender#threadedInject".to_owned())
    );
    assert_eq!(
        dispatch_send_block_reason_from_snapshot(
            true,
            &hook_names,
            &hook_installed[..2],
            1,
            None,
            None,
        ),
        Some("bridge discovery hook snapshot invalid".to_owned())
    );
}

#[test]
fn member_enrichment_dispatch_collects_missing_ids_and_merges_profiles() {
    let missing = dispatch_member_enrichment_missing_nicknames(
        &serde_json::json!({
            "hints": [{ "userId": 7, "nickname": "creatorUserId" }],
            "members": [{ "userId": 5, "nickname": "홍길동" }],
        })
        .to_string(),
    );
    let missing_value: Value = serde_json::from_str(&missing).expect("missing envelope");
    assert_eq!(missing_value["ok"], true);
    assert_eq!(missing_value["missingUserIds"], serde_json::json!([7]));

    let merged = dispatch_member_enrichment_merge(
        &serde_json::json!({
            "sourcePath": "$.members",
            "confidence": "LOW",
            "confidenceScore": 1,
            "members": [{ "userId": 7, "nickname": "creatorUserId" }],
            "upstreamProfiles": [{ "userId": 7, "nickname": "Alice", "profileImageUrl": null }],
        })
        .to_string(),
    );
    let merged_value: Value = serde_json::from_str(&merged).expect("merge envelope");
    assert_eq!(merged_value["ok"], true);
    assert_eq!(merged_value["confidence"], "MEDIUM");
    assert_eq!(merged_value["sourcePath"], "$.members+upstream:member");
    assert_eq!(merged_value["members"][0]["nickname"], "Alice");

    let invalid = dispatch_member_enrichment_merge("not-json");
    let invalid_value: Value = serde_json::from_str(&invalid).expect("error envelope");
    assert_eq!(invalid_value["ok"], false);
    assert_eq!(invalid_value["errorCode"], "BAD_REQUEST");
}

#[test]
fn member_extraction_dispatch_returns_snapshot_envelope() {
    let request = serde_json::json!({
        "expectedMembers": [{ "userId": 7, "nickname": "Alice" }],
        "containers": [{
            "path": "$.members",
            "containerType": "collection",
            "views": [{
                "className": "com.kakao.test.Member",
                "values": [["a", 7], ["b", "Alice"]],
            }],
        }],
    });

    let envelope = dispatch_member_extraction_evaluate(&request.to_string());
    let value: Value = serde_json::from_str(&envelope).expect("envelope JSON");
    assert_eq!(value["ok"], true);
    assert_eq!(value["found"], true);
    assert_eq!(value["snapshot"]["sourcePath"], "$.members");
    assert_eq!(value["snapshot"]["members"][0]["userId"], 7);

    let invalid = dispatch_member_extraction_evaluate("not-json");
    let invalid_value: Value = serde_json::from_str(&invalid).expect("error envelope JSON");
    assert_eq!(invalid_value["ok"], false);
    assert_eq!(invalid_value["errorCode"], "BAD_REQUEST");
}

#[test]
fn member_extraction_dispatch_uses_public_bad_request_message() {
    assert_error(
        &dispatch_member_extraction_evaluate("not-json"),
        "BAD_REQUEST",
        "member extraction request invalid",
    );
}

#[test]
fn dispatch_op_routes_context_and_request_operations() {
    let created = assert_ok(&dispatch_op(
        "context.create",
        r#"{"mode":"production","token":"bridge-token","requireHandshakeRaw":"true"}"#,
    ));
    let handle = created["handle"].as_i64().expect("context handle");
    assert_eq!(created["requireHandshake"], true);

    let request_json =
        json!({"action":"send_text","protocolVersion":2,"token":"bridge-token"}).to_string();
    assert_ok(&dispatch_op(
        "request.validateToken",
        &json!({
            "handle": handle,
            "requestJson": request_json,
        })
        .to_string(),
    ));

    assert_error(
        &dispatch_op(
            "request.validateToken",
            r#"{"handle":0,"requestJson":"{\"action\":\"health\",\"protocolVersion\":2}"}"#,
        ),
        "INVALID_HANDLE",
        "invalid BridgeCoreContext handle",
    );

    assert_ok(&dispatch_op(
        "context.destroy",
        &json!({ "handle": handle }).to_string(),
    ));
}

#[test]
fn dispatch_op_rejects_unknown_or_malformed_payloads() {
    let created = assert_ok(&dispatch_op(
        "context.create",
        r#"{"mode":"production","token":"bridge-token","requireHandshakeRaw":"true"}"#,
    ));
    let handle = created["handle"].as_i64().expect("context handle");

    assert_error(
        &dispatch_op("request.requiresRequestId", "not-json"),
        "BAD_REQUEST",
        "dispatch payload JSON invalid",
    );
    assert_error(
        &dispatch_op(
            "request.validateAdmission",
            &json!({ "handle": handle }).to_string(),
        ),
        "BAD_REQUEST",
        "dispatch payload invalid",
    );
    assert_error(
        &dispatch_op("future.op", "{}"),
        "BAD_REQUEST",
        "unknown bridge core dispatch op",
    );

    assert_ok(&dispatch_op(
        "context.destroy",
        &json!({ "handle": handle }).to_string(),
    ));
}

#[test]
fn reply_mention_attachment_dispatch_matches_core_merge_policy() {
    let attachment = dispatch_reply_mention_attachment_or_null(
        r#"{"callingPkg":"com.kakao.talk","mentions":[{"user_id":"text-user","at":[1],"len":3}]}"#,
    )
    .expect("valid mention attachment");
    let value: Value = serde_json::from_str(&attachment).expect("attachment JSON");
    assert_eq!(value["callingPkg"], "com.kakao.talk");
    assert_eq!(value["mentions"][0]["user_id"], "text-user");

    assert_eq!(
        dispatch_reply_mention_attachment_or_null(r#"{"mentions":[]}"#),
        None
    );
    assert_eq!(
        dispatch_merge_reply_mention_attachment(
            r#"{"a":1,"mentions":[{"user_id":"old"}],"b":2}"#,
            r#"{"mentions":[{"user_id":"new"}]}"#,
        ),
        Some(r#"{"a":1,"mentions":[{"user_id":"new"}],"b":2}"#.to_owned())
    );
    assert_eq!(
        dispatch_merge_reply_mention_attachment("not-json", r#"{"mentions":[1]}"#),
        None
    );
}

#[test]
fn reply_leverage_attachment_dispatch_matches_core_merge_policy() {
    let merged = dispatch_merge_reply_leverage_attachment(
        Some(
            r#"{"P":{"A":{"code":"signed"},"RF":"out-client"},"C":{"old":true},"K":{"ak":"app","av":"6.0.0","ti":"old","lv":"4.0"}}"#,
        ),
        r#"{"P":{"RF":"chat_ln"},"C":{"new":true},"K":{"ti":"121065","ai":"377386"}}"#,
    );
    let value: Value = serde_json::from_str(&merged).expect("merged JSON");
    assert_eq!(value["P"]["A"]["code"], "signed");
    assert_eq!(value["P"]["RF"], "out-client");
    assert_eq!(value["C"]["new"], true);
    assert_eq!(value["K"]["ak"], "app");
    assert_eq!(value["K"]["ti"], "121065");
    assert_eq!(value["K"]["ai"], "377386");
    assert_eq!(value["K"]["lv"], "4.0");

    assert_eq!(
        dispatch_merge_reply_leverage_attachment(None, r#"{ "K": { "ti": "121065" } }"#),
        r#"{"K":{"ti":"121065"}}"#
    );
    assert_eq!(
        dispatch_merge_reply_leverage_attachment(Some("not-json"), r#"{"K":{"ti":"raw"}}"#),
        r#"{"K":{"ti":"raw"}}"#
    );
}

#[test]
fn reply_attachment_text_dispatch_matches_core_policy() {
    assert!(dispatch_reply_attachment_text_looks_like(
        r#"{"callingPkg":"com.kakao.talk"}"#
    ));
    assert!(dispatch_reply_attachment_text_looks_like(
        r#"{"P":{},"C":{}}"#
    ));
    assert!(!dispatch_reply_attachment_text_looks_like(
        "mentions without JSON quotes"
    ));
    assert_eq!(
        dispatch_reply_attachment_session_id(r#"{"irisSessionId":"session-1"}"#).as_deref(),
        Some("session-1")
    );
    assert_eq!(
        dispatch_reply_attachment_session_id(r#"{"irisSessionId":12345}"#).as_deref(),
        Some("12345")
    );
    assert_eq!(
        dispatch_reply_attachment_session_id(r#"{"irisSessionId":"   "}"#),
        None
    );
    assert_eq!(dispatch_reply_attachment_session_id("not-json"), None);
}

#[test]
fn current_bridge_capabilities_dispatch_reports_rollout_and_readiness_reasons() {
    let mut input = capabilities_input();
    input.rollout.send_text_enabled = false;
    let envelope = assert_ok(&dispatch_current_bridge_capabilities(&input));

    assert_eq!(envelope["inspectChatRoomSupported"], true);
    assert_eq!(envelope["inspectChatRoomReady"], true);
    assert_eq!(envelope["markChatRoomReadSupported"], true);
    assert_eq!(envelope["markChatRoomReadReady"], true);
    assert_eq!(envelope["sendTextSupported"], true);
    assert_eq!(envelope["sendTextReady"], false);
    assert_eq!(envelope["sendTextReason"], "text bridge send_text disabled");
    assert_eq!(envelope["sendMarkdownReady"], true);
    assert_eq!(envelope.get("sendMarkdownReason"), None);

    let mut input = capabilities_input();
    input.readiness.registry_available = false;
    input.readiness.registry_error = Some("registry unavailable".to_owned());
    let unavailable = assert_ok(&dispatch_current_bridge_capabilities(&input));

    assert_eq!(unavailable["inspectChatRoomSupported"], false);
    assert_eq!(unavailable["inspectChatRoomReason"], "registry unavailable");
    assert_eq!(unavailable["sendTextReason"], "registry unavailable");

    let mut input = capabilities_input();
    input.notification_action_supported = false;
    let notification_unavailable = assert_ok(&dispatch_current_bridge_capabilities(&input));
    assert_eq!(notification_unavailable["markChatRoomReadSupported"], false);
    assert_eq!(notification_unavailable["markChatRoomReadReady"], false);
    assert_eq!(
        notification_unavailable["markChatRoomReadReason"],
        "Kakao notification action service unavailable"
    );
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

    let long_request_id = "x".repeat(257);
    assert_error(
        &dispatch_request_admission("send_text", Some(&long_request_id)),
        "BAD_REQUEST",
        "requestId too long",
    );
    assert_error(
        &dispatch_request_admission("health", Some(&long_request_id)),
        "BAD_REQUEST",
        "requestId too long",
    );
}

#[test]
fn request_admission_envelope_reports_request_id_policy() {
    let send_text = assert_ok(&dispatch_request_admission("send_text", Some("req-1")));
    assert_eq!(send_text["requiresRequestId"], true);
    assert_eq!(send_text["dedupeKey"], "send_text:req-1");

    let health = assert_ok(&dispatch_request_admission("health", Some("req-1")));
    assert_eq!(health["requiresRequestId"], false);
    assert!(health["dedupeKey"].is_null());
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
    let root_dir = temp_dir("iris-jni-image-path-root");
    let file = root_dir.path().join("image.png");
    fs::write(&file, b"x").expect("write image");
    let root = root_dir.path().canonicalize().expect("canonical root");
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
}

#[test]
fn image_lease_facts_dispatch_hashes_file_bytes_and_reports_missing_files() {
    let root = temp_dir("iris-jni-lease-facts-root");
    let file = root.path().join("image.png");
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

    let missing = root
        .path()
        .join("missing.png")
        .to_string_lossy()
        .into_owned();
    assert_error(
        &dispatch_image_lease_facts_json(&json!([missing]).to_string()),
        "PATH_VALIDATION_FAILED",
        &format!(
            "image file not found: {}",
            root.path().join("missing.png").to_string_lossy()
        ),
    );
    assert_error(
        &dispatch_image_lease_facts_json("not-json"),
        "BAD_REQUEST",
        "image lease paths JSON invalid",
    );
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
fn failure_metric_bucket_dispatch_matches_bridge_metrics_contract() {
    assert_eq!(
        dispatch_failure_metric_bucket("PATH_VALIDATION_FAILED"),
        "pathValidationFailure"
    );
    assert_eq!(
        dispatch_failure_metric_bucket("UNAUTHORIZED"),
        "unauthorizedClient"
    );
    assert_eq!(dispatch_failure_metric_bucket("TIMEOUT"), "timeout");
    assert_eq!(dispatch_failure_metric_bucket("SEND_FAILED"), "sendFailure");
    assert_eq!(dispatch_failure_metric_bucket("BAD_REQUEST"), "sendFailure");
}

#[test]
fn kakao_target_dispatch_matches_bridge_package_policy() {
    let envelope = assert_ok(&dispatch_resolve_kakao_target("com.kakao.talk.revanced"));
    assert_eq!(envelope["packageName"], "com.kakao.talk.revanced");
    assert_eq!(envelope["dexPackage"], "com.kakao.talk");

    assert_error(
        &dispatch_resolve_kakao_target("com.example.talk"),
        "BAD_REQUEST",
        "unsupported KakaoTalk package: com.example.talk",
    );
}

#[test]
fn kakao_link_attachment_dispatch_matches_titles_and_rejects_bad_json() {
    let expected = r#"{
        "template_id":"133218",
        "C":{"ITL":[{"TD":{"T":"첫 번째"}},{"TD":{"T":"두 번째"}}]},
        "K":{"ti":"133218"}
    }"#;
    let committed = r#"{
        "ti":"133218",
        "C":{"ITL":[{"TD":{"T":"첫 번째"}},{"TD":{"T":"두 번째"}}]}
    }"#;
    let stale = r#"{
        "ti":"133218",
        "C":{"ITL":[{"TD":{"T":"다른 방송"}}]}
    }"#;

    assert!(dispatch_kakao_link_attachments_match(expected, committed));
    assert!(!dispatch_kakao_link_attachments_match(expected, stale));
    assert!(!dispatch_kakao_link_attachments_match(
        "not-json", committed
    ));
}

#[test]
fn kakao_chat_log_attachment_crypto_dispatch_reports_bad_request_for_unknown_enc_type() {
    assert_error(
        &dispatch_kakao_chat_log_attachment_crypto(true, 30, "test", 438_562_408),
        "BAD_REQUEST",
        "kakao chat log attachment crypto request invalid",
    );
}

#[test]
fn kakao_link_leverage_encryption_type_dispatch_matches_bridge_policy() {
    assert_eq!(
        dispatch_kakao_link_leverage_encryption_type(r#"{"enc":31}"#),
        31
    );
    assert_eq!(
        dispatch_kakao_link_leverage_encryption_type(r#"{ "enc" : 42 }"#),
        42
    );
    assert_eq!(
        dispatch_kakao_link_leverage_encryption_type(r#"{"enc":"42"}"#),
        31
    );
    assert_eq!(
        dispatch_kakao_link_leverage_encryption_type(r#"{"enc":-1}"#),
        31
    );
    assert_eq!(dispatch_kakao_link_leverage_encryption_type("not-json"), 31);
}

#[test]
fn kakao_link_pending_cleanup_dispatch_checks_header_and_identity() {
    let expected = r#"{
        "template_id":"133266",
        "C":{"HD":{"TD":{"T":"동일한 알림"}}},
        "K":{"ti":"133266"},
        "template_args":{"alarm_title":"동일한 알림","web_url":"watch?v=expected01"}
    }"#;
    let pending = r#"{
        "ti":"133266",
        "C":{"HD":{"TD":{"T":"동일한 알림"}}},
        "ta":{"alarm_title":"동일한 알림","web_url":"watch?v=expected01"}
    }"#;
    let stale = r#"{
        "ti":"133266",
        "C":{"HD":{"TD":{"T":"동일한 알림"}}},
        "ta":{"alarm_title":"동일한 알림","web_url":"watch?v=stale00002"}
    }"#;

    assert!(dispatch_kakao_link_pending_cleanup_attachments_match(
        expected, pending,
    ));
    assert!(!dispatch_kakao_link_pending_cleanup_attachments_match(
        expected, stale,
    ));
}

#[test]
fn kakao_link_template_dispatch_preserves_route_flags_and_app_key_extraction() {
    assert!(dispatch_kakao_link_has_explicit_template_args(
        r#"{"templateArgs":{"item1_title":"첫 번째"}}"#
    ));
    assert!(!dispatch_kakao_link_has_explicit_template_args("not-json"));

    assert!(dispatch_kakao_link_has_resolved_iris_template(
        r#"{"P":{"SNM":"hololive-bot"},"C":{"ITL":[{"TD":{"T":"첫 번째"}}]}}"#
    ));
    assert!(!dispatch_kakao_link_has_resolved_iris_template(
        r#"{"P":{"SNM":"hololive-bot"},"C":{"ITL":[]}}"#
    ));

    assert_eq!(
        dispatch_kakao_link_extract_app_key(
            "https://example.test/path?app_key=46e8bda79095ab1dea785ef1adad5117",
        )
        .as_deref(),
        Some("46e8bda79095ab1dea785ef1adad5117")
    );

    let query = dispatch_kakao_link_build_v4_encoded_query(
        r#"{
            "app_key":"bfbfe8b641716d3f45e01a3b7a03f13d",
            "template_id":"133218",
            "P":{"VA":"6.0.0","SDID":"133218"},
            "C":{"HD":{"TD":{"T":"5분 전 알림"}}}
        }"#,
    )
    .expect("query");
    assert!(query.starts_with("linkver=4.0&appver=6.0.0&appkey=bfbfe8b641716d3f45e01a3b7a03f13d"));
    assert!(query.contains("%EB%B6%84+%EC%A0%84"));
    assert_eq!(dispatch_kakao_link_build_v4_encoded_query("not-json"), None);

    let send_attachment = dispatch_kakao_link_build_spec_send_attachment(
        r#"{
            "app_key":"bfbfe8b641716d3f45e01a3b7a03f13d",
            "template_id":"133220",
            "P":{"VA":"6.0.0","SID":"iris_133220","SDID":"133220","SNM":"hololive-bot"},
            "C":{"HD":{"TD":{"T":"5분 전 알림"}},"ITL":[{"TD":{"T":"단건 방송"}}]},
            "K":{"ti":"133220"},
            "template_args":{"visible_stream_count":"1"}
        }"#,
    );
    let send_attachment = serde_json::from_str::<Value>(&send_attachment).expect("attachment");
    assert_eq!(send_attachment["template_id"], "133220");
    assert_eq!(send_attachment["P"]["SDID"], "133220");
    assert_eq!(send_attachment["K"]["ti"], "133220");
    assert_eq!(
        send_attachment["template_args"]["visible_stream_count"],
        "1"
    );
    assert_eq!(
        dispatch_kakao_link_build_spec_send_attachment("not-json"),
        "not-json",
    );

    let patched = dispatch_kakao_link_patch_display_attachment(
        Some(
            r#"{
                "P":{"SDID":"133222","SST":{"L":{"LPC":"https://apps.kakao.com/talk/message/block?tid=133222"}}},
                "C":{"ITL":[]},
                "K":{"ti":"133222","lv":"4.0"}
            }"#,
        ),
        r#"{
            "template_id":"133218",
            "P":{"ME":"5분 전 알림"},
            "C":{"ITL":[{"TD":{"T":"첫 번째","D":"멤버 · LIVE"},"L":{"LPC":"https://youtu.be/one"}}],"BUL":[]},
            "K":{"ti":"133218"}
        }"#,
    );
    let patched = serde_json::from_str::<Value>(&patched).expect("patched");
    assert_eq!(patched["P"]["SDID"], "133218");
    assert_eq!(patched["P"]["ME"], "5분 전 알림");
    assert_eq!(patched["P"]["DID"], "https://youtu.be/one");
    assert_eq!(
        patched["P"]["SST"]["L"]["LPC"],
        "https://apps.kakao.com/talk/message/block?tid=133218"
    );
    assert_eq!(patched["K"]["ti"], "133218");
    assert_eq!(patched["C"]["ITL"][0]["TD"]["D"], "멤버");
    assert_eq!(
        dispatch_kakao_link_patch_display_attachment(None, "raw"),
        "raw",
    );
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
    let hello = r#"{"type":"hello","protocolVersion":2,"clientNonce":"client-1","socketName":"iris-image-bridge-mux","timestampMs":1}"#;
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
    let hello_1 = r#"{"type":"hello","protocolVersion":2,"clientNonce":"client-1","socketName":"@mux","timestampMs":1}"#;
    let hello_2 = r#"{"type":"hello","protocolVersion":2,"clientNonce":"client-2","socketName":"@mux","timestampMs":2}"#;

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
        &format!(r#"{{"type":"client_proof","protocolVersion":2,"proof":"{proof_2}"}}"#),
    ));

    let proof_1 = client_proof(
        TOKEN,
        "client-1",
        frame_1.server_nonce.as_deref().expect("server nonce 1"),
    );
    assert_ok(&dispatch_handshake_on_client_proof(
        &ctx,
        &format!(r#"{{"type":"client_proof","protocolVersion":2,"proof":"{proof_1}"}}"#),
    ));
}

#[test]
fn reply_hook_sign_and_verify_match_core_golden() {
    let signature =
        dispatch_reply_hook_sign(TOKEN, 42, "hello **world**", "req-7", CREATED_AT, None)
            .expect("signable");
    assert_eq!(signature, GOLDEN_SIGNATURE);
    assert!(dispatch_reply_hook_verify(&ReplyHookVerifyInput {
        bridge_token: TOKEN,
        room_id: 42,
        message_text: "hello **world**",
        session_id: Some("req-7"),
        created_at_epoch_ms: Some(CREATED_AT),
        mentions_hash: None,
        signature: Some(GOLDEN_SIGNATURE),
        now_epoch_ms: CREATED_AT + 120_000,
    }));
}

#[test]
fn reply_pending_context_dispatch_returns_verified_markdown_context() {
    let signature =
        dispatch_reply_hook_sign(TOKEN, 42, "markdown body", "req-md", CREATED_AT, None)
            .expect("signable");
    let envelope = assert_ok(&dispatch_reply_markdown_pending_context(
        &json!({
            "bridgeToken": TOKEN,
            "nowEpochMs": CREATED_AT + 1,
            "snapshot": {
                "sessionId": "req-md",
                "roomIdRaw": "42",
                "threadIdRaw": "777",
                "threadScope": 3,
                "createdAtEpochMs": CREATED_AT,
                "signature": signature,
                "messageText": "markdown body"
            }
        })
        .to_string(),
    ));

    assert_eq!(envelope["context"]["roomId"], 42);
    assert_eq!(envelope["context"]["threadId"], 777);
    assert_eq!(envelope["context"]["threadScope"], 3);
    assert_eq!(envelope["context"]["messageText"], "markdown body");
}

#[test]
fn reply_pending_context_dispatch_returns_null_context_for_unverified_request() {
    let attachment_text = json!({
        "mentions": [{"userId": 7}]
    })
    .to_string();
    let envelope = assert_ok(&dispatch_reply_mention_pending_context(
        &json!({
            "bridgeToken": TOKEN,
            "nowEpochMs": CREATED_AT + 1,
            "snapshot": {
                "sessionId": "req-mention",
                "roomIdRaw": "42",
                "createdAtEpochMs": CREATED_AT,
                "signature": "bad",
                "messageText": "hi @A",
                "attachmentText": attachment_text
            }
        })
        .to_string(),
    ));

    assert_eq!(envelope["context"], Value::Null);
    assert_error(
        &dispatch_reply_markdown_pending_context("not-json"),
        "BAD_REQUEST",
        "reply pending context JSON invalid",
    );
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
    let oldest_hello = r#"{"type":"hello","protocolVersion":2,"clientNonce":"client-oldest","socketName":"@mux","timestampMs":1}"#;
    let oldest = assert_ok(&dispatch_handshake_on_hello(&ctx, oldest_hello, 1, "@mux"));
    let oldest_frame: HandshakeFrame =
        serde_json::from_str(oldest["frameJson"].as_str().expect("frame json"))
            .expect("oldest server frame");

    for index in 0..MAX_HANDSHAKE_SESSIONS {
        let hello = format!(
            r#"{{"type":"hello","protocolVersion":2,"clientNonce":"client-{index}","socketName":"@mux","timestampMs":2}}"#
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
            &format!(r#"{{"type":"client_proof","protocolVersion":2,"proof":"{oldest_proof}"}}"#),
        ),
        "UNAUTHORIZED",
        "bridge authentication failed",
    );
}

#[test]
fn concurrent_with_context_dispatch_on_shared_handle_all_succeed() {
    let handle = into_handle(context());
    let threads = 8;
    let calls_per_thread = 256;
    let barrier = std::sync::Arc::new(std::sync::Barrier::new(threads));

    let workers: Vec<_> = (0..threads)
        .map(|thread_index| {
            let barrier = std::sync::Arc::clone(&barrier);
            std::thread::spawn(move || {
                barrier.wait();
                for call_index in 0..calls_per_thread {
                    let key = format!("send:req-{thread_index}-{call_index}");
                    let envelope =
                        with_context(handle, |ctx| dispatch_dedupe_admit(ctx, &key, call_index))
                            .expect("shared handle dispatches under read lock");
                    assert_ok(&envelope);
                }
            })
        })
        .collect();

    for worker in workers {
        worker.join().expect("worker thread");
    }

    drop_handle(handle);
}

#[test]
fn concurrent_lookup_many_handles_all_succeed() {
    let handles: Vec<_> = (0..64).map(|_| into_handle(context())).collect();
    let handles = std::sync::Arc::new(handles);
    let threads = 8;
    let calls_per_thread = 256;
    let barrier = std::sync::Arc::new(std::sync::Barrier::new(threads));

    let workers: Vec<_> = (0..threads)
        .map(|thread_index| {
            let barrier = std::sync::Arc::clone(&barrier);
            let handles = std::sync::Arc::clone(&handles);
            std::thread::spawn(move || {
                barrier.wait();
                for call_index in 0..calls_per_thread {
                    let handle = handles[(thread_index * 31 + call_index) % handles.len()];
                    let envelope = with_context(handle, |ctx| {
                        dispatch_dedupe_admit(
                            ctx,
                            &format!("send:req-{thread_index}-{call_index}"),
                            i64::try_from(call_index).expect("call index fits i64"),
                        )
                    })
                    .expect("many handle dispatches under read lock");
                    assert_ok(&envelope);
                }
            })
        })
        .collect();

    for worker in workers {
        worker.join().expect("worker thread");
    }
    for handle in handles.iter() {
        drop_handle(*handle);
    }
}

#[test]
fn drop_handle_during_in_flight_call_keeps_context_alive() {
    let handle = into_handle(context());

    let (entered_tx, entered_rx) = std::sync::mpsc::channel::<()>();
    let (release_tx, release_rx) = std::sync::mpsc::channel::<()>();

    let worker = std::thread::spawn(move || {
        with_context(handle, |ctx| {
            entered_tx.send(()).expect("signal entered body");
            release_rx.recv().expect("await release");
            dispatch_dedupe_admit(ctx, "send:in-flight", 1)
        })
        .expect("in-flight dispatch holds the cloned Arc")
    });

    entered_rx.recv().expect("await body entry");
    drop_handle(handle);
    release_tx.send(()).expect("release in-flight body");

    let envelope = worker.join().expect("in-flight worker");
    assert_ok(&envelope);

    let stale = match with_context(handle, |ctx| dispatch_dedupe_admit(ctx, "send:after", 2)) {
        Ok(envelope) => envelope,
        Err(rejection) => invalid_handle_envelope(&rejection),
    };
    assert_error(&stale, "INVALID_HANDLE", "invalid BridgeCoreContext handle");
}

#[test]
fn drop_handle_returns_before_in_flight_body_completes() {
    let handle = into_handle(context());

    let (entered_tx, entered_rx) = std::sync::mpsc::channel::<()>();
    let (release_tx, release_rx) = std::sync::mpsc::channel::<()>();
    let (done_tx, done_rx) = std::sync::mpsc::channel::<()>();

    let worker = std::thread::spawn(move || {
        with_context(handle, |_ctx| {
            entered_tx.send(()).expect("signal entered body");
            release_rx.recv().expect("await release");
        })
        .expect("in-flight dispatch holds cloned Arc");
    });

    entered_rx
        .recv_timeout(Duration::from_secs(1))
        .expect("await body entry");

    let dropper = std::thread::spawn(move || {
        drop_handle(handle);
        done_tx.send(()).expect("signal drop complete");
    });

    done_rx
        .recv_timeout(Duration::from_millis(100))
        .expect("drop_handle must not wait for in-flight body completion");

    release_tx.send(()).expect("release in-flight body");
    worker.join().expect("in-flight worker");
    dropper.join().expect("dropper thread");
}

#[test]
fn drop_mux_session_handle_returns_before_in_flight_body_completes() {
    let handle = into_mux_session_handle(MuxSessionCore::new(1));

    let (entered_tx, entered_rx) = std::sync::mpsc::channel::<()>();
    let (release_tx, release_rx) = std::sync::mpsc::channel::<()>();
    let (done_tx, done_rx) = std::sync::mpsc::channel::<()>();

    let worker = std::thread::spawn(move || {
        with_mux_session(handle, |_session| {
            entered_tx.send(()).expect("signal entered body");
            release_rx.recv().expect("await release");
        })
        .expect("in-flight mux dispatch holds cloned Arc");
    });

    entered_rx
        .recv_timeout(Duration::from_secs(1))
        .expect("await mux body entry");

    let dropper = std::thread::spawn(move || {
        drop_mux_session_handle(handle);
        done_tx.send(()).expect("signal mux drop complete");
    });

    done_rx
        .recv_timeout(Duration::from_millis(100))
        .expect("drop_mux_session_handle must not wait for in-flight body completion");

    release_tx.send(()).expect("release in-flight mux body");
    worker.join().expect("in-flight mux worker");
    dropper.join().expect("mux dropper thread");
}

#[test]
fn jni_abi_version_is_sourced_from_bridge_core_protocol() {
    assert_eq!(
        crate::ABI_VERSION,
        iris_bridge_core_lib::protocol::ABI_VERSION
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

#[test]
fn mux_dispatch_after_destroy_is_rejected_as_invalid_handle() {
    let handle = into_mux_session_handle(MuxSessionCore::new(1));
    assert_ok(&dispatch_mux_session_is_cancelled(handle, "missing"));

    drop_mux_session_handle(handle);

    assert_error(
        &dispatch_mux_session_is_cancelled(handle, "missing"),
        "INVALID_HANDLE",
        "invalid MuxSessionCore handle",
    );
}

#[test]
fn wrong_registry_handle_is_rejected() {
    let context_handle = into_handle(context());
    assert_error(
        &dispatch_mux_session_is_cancelled(context_handle, "missing"),
        "INVALID_HANDLE",
        "invalid MuxSessionCore handle",
    );

    let mux_handle = into_mux_session_handle(MuxSessionCore::new(1));
    let context_result = match with_context(mux_handle, |ctx| {
        dispatch_dedupe_admit(ctx, "send:wrong-registry", 1)
    }) {
        Ok(envelope) => envelope,
        Err(rejection) => invalid_handle_envelope(&rejection),
    };
    assert_error(
        &context_result,
        "INVALID_HANDLE",
        "invalid BridgeCoreContext handle",
    );

    drop_handle(context_handle);
    drop_mux_session_handle(mux_handle);
}

#[test]
fn marshalled_dispatch_reports_error_when_argument_read_fails() {
    let missing_op = crate::marshal::dispatch_marshalled(None, Some("{}".to_owned()), |_, _| {
        panic!("dispatch must not run when a JNI argument read fails")
    });
    assert_error(&missing_op, "MARSHAL", "failed to read JNI string argument");

    let missing_payload =
        crate::marshal::dispatch_marshalled(Some("context.create".to_owned()), None, |_, _| {
            panic!("dispatch must not run when a JNI argument read fails")
        });
    assert_error(&missing_payload, "MARSHAL", "failed to read JNI string argument");
}

#[test]
fn marshalled_dispatch_forwards_successfully_read_arguments() {
    let forwarded = crate::marshal::dispatch_marshalled(
        Some("op".to_owned()),
        Some("payload".to_owned()),
        |op, payload| format!("{op}|{payload}"),
    );
    assert_eq!(forwarded, "op|payload");
}
