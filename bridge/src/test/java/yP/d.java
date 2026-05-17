package yP;

public final class d {
    public static final d a = new d();

    public int refreshCalls = 0;
    public String lastCaller = "";

    private d() {
    }

    public void u(String caller) {
        refreshCalls += 1;
        lastCaller = caller;
    }

    public String e() {
        return "access-token";
    }
}
