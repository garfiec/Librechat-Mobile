package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.text.input.PlatformImeOptions
import platform.UIKit.UITextContentTypeNewPassword
import platform.UIKit.UITextContentTypeOneTimeCode
import platform.UIKit.UITextContentTypeUsername

@OptIn(ExperimentalComposeUiApi::class)
actual fun KeyboardOptions.withIosTextContentType(type: IosTextContentType): KeyboardOptions {
    val contentType = when (type) {
        IosTextContentType.Username -> UITextContentTypeUsername
        IosTextContentType.NewPassword -> UITextContentTypeNewPassword
        IosTextContentType.OneTimeCode -> UITextContentTypeOneTimeCode
    }
    return copy(platformImeOptions = PlatformImeOptions { textContentType(contentType) })
}
