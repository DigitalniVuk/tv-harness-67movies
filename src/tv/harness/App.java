package tv.harness;

import android.app.Application;
import android.content.Context;

public final class App extends Application {
    private static Context context;

    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
    }

    static Context context() {
        return context;
    }
}
