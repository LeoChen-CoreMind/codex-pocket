package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result

/**
 * Checks whether a [Result.Error] was caused by a specific HTTP status code.
 * Shared across the settings module (e.g. 2FA delegate, account deletion).
 */
internal fun Result.Error.isHttpStatus(statusCode: Int): Boolean =
    (exception as? ApiException)?.statusCode == statusCode
