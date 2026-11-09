package com.garfiec.librechat.core.ui.platform

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** The Activity behind this context, unwrapping wrappers; null where there is none (a preview). */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
