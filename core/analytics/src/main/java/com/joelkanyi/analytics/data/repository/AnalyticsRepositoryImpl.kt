/*
 * Copyright 2023 Joel Kanyi.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.joelkanyi.analytics.data.repository

import com.joelkanyi.analytics.BuildConfig
import com.joelkanyi.analytics.domain.repository.AnalyticsRepository
import com.mixpanel.android.mpmetrics.MixpanelAPI
import org.json.JSONObject

class AnalyticsRepositoryImpl(
    private val mixpanelAPI: MixpanelAPI
) : AnalyticsRepository {
    override fun trackUserEvent(eventName: String, eventProperties: JSONObject?) {
        if (BuildConfig.BUILD_TYPE != "release") return
        eventProperties
            ?.let { mixpanelAPI.track(eventName, eventProperties) }
            ?: mixpanelAPI.track(eventName)
    }

    override fun setUserProfile(userID: String, userProperties: JSONObject?) {
        userProperties
            ?.let {
                mixpanelAPI.identify(userID)
                mixpanelAPI.people.set(it)
            } ?: mixpanelAPI.identify(userID)
    }
}
