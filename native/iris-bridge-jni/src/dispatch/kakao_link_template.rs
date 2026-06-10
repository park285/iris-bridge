use iris_bridge_core::server::kakao_link_template::{
    build_spec_send_attachment, build_v4_encoded_query, extract_app_key,
    has_explicit_template_args, has_resolved_iris_template, patch_display_attachment,
};

pub fn dispatch_kakao_link_has_explicit_template_args(raw_attachment: &str) -> bool {
    has_explicit_template_args(raw_attachment)
}

pub fn dispatch_kakao_link_has_resolved_iris_template(raw_attachment: &str) -> bool {
    has_resolved_iris_template(raw_attachment)
}

pub fn dispatch_kakao_link_extract_app_key(raw_attachment: &str) -> Option<String> {
    extract_app_key(raw_attachment)
}

pub fn dispatch_kakao_link_build_v4_encoded_query(raw_attachment: &str) -> Option<String> {
    build_v4_encoded_query(raw_attachment).ok()
}

pub fn dispatch_kakao_link_build_spec_send_attachment(raw_attachment: &str) -> String {
    build_spec_send_attachment(raw_attachment)
}

pub fn dispatch_kakao_link_patch_display_attachment(
    committed_attachment: Option<&str>,
    raw_attachment: &str,
) -> String {
    patch_display_attachment(committed_attachment, raw_attachment)
}
