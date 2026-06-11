package X20;

public final class ExplicitFactoryDataSource {
    public static final Companion f = new Companion();
    public static ExplicitFactoryDataSource h;

    private ExplicitFactoryDataSource() {
    }

    public static final class Companion {
        ExplicitFactoryDataSource a(Object cryptoDatabase, Object ioDispatcher) {
            if (cryptoDatabase == null || ioDispatcher == null) {
                return null;
            }
            if (h == null) {
                h = new ExplicitFactoryDataSource();
            }
            return h;
        }
    }
}
