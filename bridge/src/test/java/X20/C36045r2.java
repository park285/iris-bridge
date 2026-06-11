package X20;

import kotlin.coroutines.Continuation;
import kotlin.coroutines.jvm.internal.DebugMetadata;

public final class C36045r2 {
    public static final Companion INSTANCE = new Companion();
    public static C36045r2 f138770h;

    private C36045r2() {
    }

    public Object m113412t(long id, Continuation<?> continuation) {
        return null;
    }

    public static final class Companion {
        public static C36045r2 m113417b(
                Companion companion,
                Object cryptoDatabase,
                Object ioDispatcher,
                int mask,
                Object marker
        ) {
            if (companion != INSTANCE || mask != 3) {
                return null;
            }
            if (f138770h == null) {
                f138770h = new C36045r2();
            }
            return f138770h;
        }
    }

    @DebugMetadata(c = "com.kakao.talk.singleton.UserDatabaseDataSource", f = "UserDatabaseDataSource.kt", l = {41}, m = "getUserByIdV2-gIAlu-s")
    public static final class j {
        public j(Continuation<?> continuation) {
        }
    }
}
