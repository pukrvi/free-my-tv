package io.freemytv;

import android.view.KeyEvent;
import android.view.View;

/** Lifecycle shared by the selectable home designs; system launcher recovery remains in HomeMode. */
interface HomeSurface {
    default View asView() { return (View) this; }
    void resume();
    void pause();
    void destroy();
    void reset(boolean animated);
    void showAllApps();
    boolean onKey(KeyEvent event);
}
