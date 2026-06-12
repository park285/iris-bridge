use iris_bridge_core::server::member_field_checks::nickname_is_trusted_for_display;

pub fn dispatch_member_nickname_is_trusted_for_display(user_id: i64, nickname: &str) -> bool {
    nickname_is_trusted_for_display(user_id, nickname)
}
