package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.tchat.TchatModelCatalog
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.path

/**
 * Tchat's model catalog route, `GET /api/tchat/models/catalog` (Tchat
 * `api/server/routes/tchatModels.js`, JWT-authenticated). Not a LibreChat route: only a Tchat
 * server answers it.
 */
class TchatCatalogApi(
    private val client: HttpClient,
) {
    suspend fun getModelCatalog(): TchatModelCatalog =
        client.get {
            url { path("api/tchat/models/catalog") }
        }.body()
}
