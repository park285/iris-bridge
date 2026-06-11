use iris_bridge_core::server::member_field_checks::{
    generic_label_penalty, looks_like_mention_user_id_value, looks_like_nickname,
    looks_like_profile_url, nickname_is_trusted_for_display, nickname_quality_score,
    parse_role_code_from_long, parse_role_code_from_string, path_hint_score,
    primitive_long_value_from_string,
};

pub fn dispatch_member_parse_role_code_from_long(value: i64) -> Option<i32> {
    parse_role_code_from_long(value)
}

pub fn dispatch_member_parse_role_code_from_string(value: &str) -> Option<i32> {
    parse_role_code_from_string(value)
}

pub fn dispatch_member_looks_like_nickname(value: &str) -> bool {
    looks_like_nickname(value)
}

pub fn dispatch_member_looks_like_profile_url(value: &str) -> bool {
    looks_like_profile_url(value)
}

pub fn dispatch_member_primitive_long_value_from_string(value: &str) -> Option<String> {
    primitive_long_value_from_string(value).map(|parsed| parsed.to_string())
}

pub fn dispatch_member_path_hint_score(
    path: &str,
    preferred_tokens: &[String],
    discouraged_tokens: &[String],
) -> i32 {
    path_hint_score(path, preferred_tokens, discouraged_tokens)
}

pub fn dispatch_member_looks_like_mention_user_id_value(
    value: &str,
    user_id: Option<i64>,
    nickname: Option<&str>,
) -> bool {
    looks_like_mention_user_id_value(value, user_id, nickname)
}

pub fn dispatch_member_nickname_quality_score(value: &str) -> i32 {
    nickname_quality_score(value)
}

pub fn dispatch_member_generic_label_penalty(value: &str) -> i32 {
    generic_label_penalty(value)
}

pub fn dispatch_member_nickname_is_trusted_for_display(user_id: i64, nickname: &str) -> bool {
    nickname_is_trusted_for_display(user_id, nickname)
}
