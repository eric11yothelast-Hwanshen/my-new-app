package com.hmoney.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * 應用程式進入點：Hilt 全域元件由此建立。
 */
@HiltAndroidApp
class HmoneyApp : Application()
