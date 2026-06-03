package com.rrbrambley.flashcards.core

import android.content.Context
import androidx.annotation.StringRes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Resolves string resources outside of a Composable scope (ViewModels, repositories), where
 * `stringResource()` isn't available. Keeps those layers testable: unit tests inject a fake
 * instead of needing a real [Context]. Bound to [AndroidStringProvider] in `di/DataModule`.
 */
interface StringProvider {
    fun getString(@StringRes resId: Int): String
    fun getString(@StringRes resId: Int, vararg formatArgs: Any): String
}

/** [StringProvider] backed by the application [Context]. */
class AndroidStringProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : StringProvider {
    override fun getString(resId: Int): String = context.getString(resId)

    override fun getString(resId: Int, vararg formatArgs: Any): String = context.getString(resId, *formatArgs)
}
