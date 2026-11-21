package com.garfiec.librechat.feature.agents.viewmodel

/** A failure as one text: the screen's [summary] (translated), then the server's [detail] when it sent one. */
fun failureText(summary: String, detail: String?): String =
    if (detail.isNullOrBlank()) summary else "$summary\n$detail"
