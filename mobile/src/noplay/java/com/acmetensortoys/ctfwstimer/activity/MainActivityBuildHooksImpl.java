package com.acmetensortoys.ctfwstimer.activity;

import com.acmetensortoys.ctfwstimer.activity.MainActivity;
import com.acmetensortoys.ctfwstimer.activity.MainActivityBuildHooks;
import com.acmetensortoys.ctfwstimer.service.MainService;

class MainActivityBuildHooksImpl implements MainActivityBuildHooks {
    @Override
    public void onRegisterObservers(MainActivity ma, MainService.LocalBinder b) {
        // NOP
    }
}
