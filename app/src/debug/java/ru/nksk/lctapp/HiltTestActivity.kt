package ru.nksk.lctapp

import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint

/** Empty Hilt host for Compose instrumentation tests; excluded from release builds. */
@AndroidEntryPoint
class HiltTestActivity : ComponentActivity()
