use iris_bridge_core_lib::server::restart_policy::restart_delay_ms;

#[must_use]
pub fn dispatch_restart_delay_ms(failure_count: i32) -> i64 {
    restart_delay_ms(failure_count)
}
