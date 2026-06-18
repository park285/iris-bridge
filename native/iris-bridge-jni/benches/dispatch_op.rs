use criterion::{
    BenchmarkGroup, Criterion, black_box, criterion_group, criterion_main, measurement::WallTime,
};
use iris_bridge_core::bench_api::{
    BridgeCoreContext, dispatch_op, dispatch_request_requires_request_id,
    dispatch_validate_request_token_handle, drop_handle, into_handle, with_context,
};
use serde_json::json;
use std::time::Duration;

const TOKEN: &str = "bridge-token";
const REQUEST_JSON: &str = r#"{"action":"send_text","protocolVersion":1,"token":"bridge-token"}"#;
type DispatchGroup<'a> = BenchmarkGroup<'a, WallTime>;

fn context_handle() -> i64 {
    into_handle(BridgeCoreContext::new(
        Some("production"),
        TOKEN,
        Some("true"),
    ))
}

fn bench_dispatch_op(c: &mut Criterion) {
    let mut group = c.benchmark_group("dispatch_op");
    bench_request_id(&mut group);
    bench_context_require_handshake(&mut group);
    bench_validate_token(&mut group);
    group.finish();
}

fn bench_request_id(group: &mut DispatchGroup<'_>) {
    let request_id_payload = json!({ "action": "send_text" }).to_string();
    group.bench_function("requires_request_id/direct_bool", |bench| {
        bench.iter(|| dispatch_request_requires_request_id(black_box("send_text")));
    });
    group.bench_function("requires_request_id/op_json", |bench| {
        bench.iter(|| {
            dispatch_op(
                black_box("request.requiresRequestId"),
                black_box(&request_id_payload),
            )
        });
    });
}

fn bench_context_require_handshake(group: &mut DispatchGroup<'_>) {
    let require_handle = context_handle();
    let handshake_payload = json!({ "handle": require_handle }).to_string();
    group.bench_function("context_require_handshake/direct_lookup_bool", |bench| {
        bench.iter(|| {
            with_context(black_box(require_handle), |context| {
                black_box(context.require_handshake)
            })
            .expect("valid context handle")
        });
    });
    group.bench_function("context_require_handshake/op_json", |bench| {
        bench.iter(|| {
            dispatch_op(
                black_box("context.requireHandshake"),
                black_box(&handshake_payload),
            )
        });
    });
    drop_handle(require_handle);
}

fn bench_validate_token(group: &mut DispatchGroup<'_>) {
    let validate_handle = context_handle();
    let validate_payload = json!({
        "handle": validate_handle,
        "requestJson": REQUEST_JSON,
    })
    .to_string();
    group.bench_function("validate_token/direct_envelope", |bench| {
        bench.iter(|| {
            dispatch_validate_request_token_handle(
                black_box(validate_handle),
                black_box(REQUEST_JSON),
            )
        });
    });
    group.bench_function("validate_token/op_json", |bench| {
        bench.iter(|| {
            dispatch_op(
                black_box("request.validateToken"),
                black_box(&validate_payload),
            )
        });
    });
    drop_handle(validate_handle);
}

criterion_group! {
    name = benches;
    config = Criterion::default()
        .sample_size(20)
        .warm_up_time(Duration::from_millis(500))
        .measurement_time(Duration::from_secs(2));
    targets = bench_dispatch_op
}
criterion_main!(benches);
