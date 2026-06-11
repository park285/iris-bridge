package X20;

import kotlin.coroutines.Continuation;

public final class r2 {
    public static final a f = new a();
    public static r2 h;

    private r2() {
    }

    public Object t(long id, Continuation<?> continuation) {
        return null;
    }

    public static final class a {
        static r2 b(a companion, Object cryptoDatabase, Object ioDispatcher, int mask, Object marker) {
            if (companion != f || mask != 3) {
                return null;
            }
            if (h == null) {
                h = new r2();
            }
            return h;
        }
    }
}
