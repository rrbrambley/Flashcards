package com.rrbrambley.flashcards.shared

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

actual fun nowMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()
