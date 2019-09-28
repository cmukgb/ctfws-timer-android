package com.acmetensortoys.ctfwstimer.activity;

import com.acmetensortoys.ctfwstimer.service.MainService;

// The MainActivity expects a "MainActivityBuildHooksImpl" class that ascribes to this interface
// per build flavor.  This will be used when, for example, we kick on Google Play for Wear
// interaction and want to push messages out to the wearable data network.
public interface MainActivityBuildHooks {
    void onRegisterObservers(MainActivity ma, MainService.LocalBinder b);
}
