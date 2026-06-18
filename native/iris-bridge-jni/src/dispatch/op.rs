mod context;
mod image_path;
mod kakao_link;
mod lease;
mod media;
mod member;
mod mux;
mod payload;
mod policy;
mod reply;
mod request;

use payload::{parse_payload, unknown_op};

use super::envelope::json_catch_unwind;

#[must_use]
pub fn dispatch_op(op: &str, payload: &str) -> String {
    let payload = match parse_payload(payload) {
        Ok(payload) => payload,
        Err(rejection) => return json_catch_unwind(|| Err(rejection)),
    };

    match op.split_once('.') {
        Some(("context", _)) => context::dispatch(op, &payload),
        Some(("protocol", _)) => context::dispatch_protocol(op),
        Some(("request", _)) => request::dispatch(op, &payload),
        Some(("lease", _)) => lease::dispatch(op, &payload),
        Some(("mux", _)) => mux::dispatch(op, &payload),
        Some(("policy", _)) => policy::dispatch(op, &payload),
        Some(("kakaoTarget", _)) => kakao_link::dispatch_target(op, &payload),
        Some(("kakaoLink", _)) => kakao_link::dispatch(op, &payload),
        Some(("member", _)) => member::dispatch(op, &payload),
        Some(("reply", _)) => reply::dispatch(op, &payload),
        Some(("media", _)) => media::dispatch(op, &payload),
        Some(("imagePath", _)) => image_path::dispatch(op, &payload),
        _ => unknown_op(),
    }
}
