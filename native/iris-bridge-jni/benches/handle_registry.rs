use criterion::{BenchmarkId, Criterion, black_box, criterion_group, criterion_main};
use iris_bridge_core::bench_api::{
    BridgeCoreContext, MuxSessionCore, drop_handle, drop_mux_session_handle, into_handle,
    into_mux_session_handle, with_context, with_mux_session,
};
use std::thread;
use std::time::Duration;

const TOKEN: &str = "bridge-token";
const HANDLE_COUNT: usize = 256;
const THREADS: usize = 8;
const LOOKUPS_PER_THREAD: usize = 4_096;

fn new_context_handle() -> i64 {
    into_handle(BridgeCoreContext::new(
        Some("production"),
        TOKEN,
        Some("true"),
    ))
}

fn new_context_handles(count: usize) -> Vec<i64> {
    (0..count).map(|_| new_context_handle()).collect()
}

fn drop_context_handles(handles: &[i64]) {
    for handle in handles {
        drop_handle(*handle);
    }
}

fn lookup_context(handle: i64) {
    with_context(handle, |context| black_box(context.require_handshake))
        .expect("valid context handle");
}

fn same_handle_worker(handle: i64) {
    for _ in 0..LOOKUPS_PER_THREAD {
        lookup_context(handle);
    }
}

fn many_handles_worker(thread_index: usize, handles: &[i64]) {
    for call_index in 0..LOOKUPS_PER_THREAD {
        let index = (thread_index * 31 + call_index) % handles.len();
        lookup_context(handles[index]);
    }
}

fn mixed_handles_worker(thread_index: usize, handles: &[i64]) {
    for call_index in 0..LOOKUPS_PER_THREAD {
        if call_index % 20 == 0 {
            let handle = new_context_handle();
            lookup_context(handle);
            drop_handle(handle);
        } else {
            let index = (thread_index * 31 + call_index) % handles.len();
            lookup_context(handles[index]);
        }
    }
}

fn parallel_same_handle(handle: i64) {
    thread::scope(|scope| {
        for _ in 0..THREADS {
            scope.spawn(move || same_handle_worker(handle));
        }
    });
}

fn parallel_many_handles(handles: &[i64]) {
    thread::scope(|scope| {
        for thread_index in 0..THREADS {
            scope.spawn(move || many_handles_worker(thread_index, handles));
        }
    });
}

fn mixed_many_handles(handles: &[i64]) {
    thread::scope(|scope| {
        for thread_index in 0..THREADS {
            scope.spawn(move || mixed_handles_worker(thread_index, handles));
        }
    });
}

fn bench_handle_registry(c: &mut Criterion) {
    let mut group = c.benchmark_group("handle_registry");

    let one_handle = new_context_handle();
    group.bench_function("lookup_uncontended_one_handle", |bench| {
        bench.iter(|| lookup_context(black_box(one_handle)));
    });
    drop_handle(one_handle);

    let handles = new_context_handles(HANDLE_COUNT);
    group.bench_with_input(
        BenchmarkId::new("lookup_many_handles_single_thread", HANDLE_COUNT),
        &handles,
        |bench, handles| {
            bench.iter(|| {
                for handle in handles {
                    lookup_context(black_box(*handle));
                }
            });
        },
    );
    group.bench_function("lookup_same_handle_read_only_threads", |bench| {
        let handle = handles[0];
        bench.iter(|| parallel_same_handle(black_box(handle)));
    });
    group.bench_function("lookup_many_handles_read_only_threads", |bench| {
        bench.iter(|| parallel_many_handles(black_box(&handles)));
    });
    group.bench_function("mixed_many_handles_95r_5w", |bench| {
        bench.iter(|| mixed_many_handles(black_box(&handles)));
    });
    drop_context_handles(&handles);

    group.bench_function("churn_create_drop_lookup", |bench| {
        bench.iter(|| {
            let handle = new_context_handle();
            lookup_context(handle);
            drop_handle(handle);
        });
    });

    let mux_handle = into_mux_session_handle(MuxSessionCore::new(1_024));
    group.bench_function("mux_same_session_mutation", |bench| {
        bench.iter(|| {
            with_mux_session(black_box(mux_handle), |session| {
                session.on_request_completed(black_box("missing-correlation-id"));
                black_box(session.active_count());
            })
            .expect("valid mux session handle");
        });
    });
    drop_mux_session_handle(mux_handle);

    group.finish();
}

criterion_group! {
    name = benches;
    config = Criterion::default()
        .sample_size(20)
        .warm_up_time(Duration::from_millis(500))
        .measurement_time(Duration::from_secs(2));
    targets = bench_handle_registry
}
criterion_main!(benches);
