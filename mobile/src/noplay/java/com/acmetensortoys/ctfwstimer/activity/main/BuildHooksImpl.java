package com.acmetensortoys.ctfwstimer.activity.main;

import com.acmetensortoys.ctfwstimer.activity.main.Activity;
import com.acmetensortoys.ctfwstimer.activity.main.BuildHooks;
import com.acmetensortoys.ctfwstimer.service.MainService;

class BuildHooksImpl implements BuildHooks {
    @Override
    public void onRegisterObservers(Activity ma, MainService.LocalBinder b) {
        // NOP
    }
}
