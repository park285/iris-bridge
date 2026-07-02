use iris_bridge_core_lib::server::Rejection;
use serde::de::DeserializeOwned;

use super::envelope::bad_request;

pub(super) fn parse_json<T: DeserializeOwned>(
    raw: &str,
    invalid_message: &'static str,
) -> Result<T, Rejection> {
    serde_json::from_str(raw).map_err(|_| bad_request(invalid_message))
}
