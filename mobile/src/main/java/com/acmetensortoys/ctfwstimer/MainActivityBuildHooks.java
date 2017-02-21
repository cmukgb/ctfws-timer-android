package com.acmetensortoys.ctfwstimer;

// The MainActivity expects a "MainActivityBuildHooksImpl" class that ascribes to this interface
// per build flavor.  This will be used when, for example, we kick on Google Play for Wear
// interaction and want to push messages out to the wearable data network.
interface MainActivityBuildHooks {
    void onStart(MainActivity ma, MainService.LocalBinder b);
}
