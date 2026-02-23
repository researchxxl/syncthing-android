package com.nutomic.syncthingandroid.util

import android.content.res.Configuration

val Configuration.isTelevision: Boolean
    get() = (uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
