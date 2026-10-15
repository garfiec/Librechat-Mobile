package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.User
import com.garfiec.librechat.core.model.request.OtpVerificationRequest
import com.garfiec.librechat.core.model.request.ResendVerificationRequest
import com.garfiec.librechat.core.model.request.VerifyEmailRequest
import com.garfiec.librechat.core.model.response.AvatarUploadResponse
import com.garfiec.librechat.core.model.response.TermsAcceptResponse
import com.garfiec.librechat.core.model.response.TermsResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.path

class UserApi constructor(
    private val client: HttpClient,
) {
    suspend fun getUser(): User =
        client.get {
            url { path("api/user") }
        }.body()

    suspend fun deleteUser(token: String? = null, backupCode: String? = null) {
        client.delete {
            url { path("api/user/delete") }
            if (token != null || backupCode != null) {
                setBody(OtpVerificationRequest(token = token, backupCode = backupCode))
            }
        }
    }

    suspend fun verifyEmail(request: VerifyEmailRequest) {
        client.post {
            url { path("api/user/verify") }
            setBody(request)
        }
    }

    suspend fun resendVerification(request: ResendVerificationRequest) {
        client.post {
            url { path("api/user/verify/resend") }
            setBody(request)
        }
    }

    suspend fun getTerms(): TermsResponse =
        client.get {
            url { path("api/user/terms") }
        }.body()

    suspend fun acceptTerms(): TermsAcceptResponse =
        client.post {
            url { path("api/user/terms/accept") }
        }.body()

    /**
     * `POST /api/files/images/avatar`. Multer infers the type from [fileName]'s extension, so it must
     * match [imageBytes]. The server rejects anything over `fileConfig.avatarSizeLimit` (2 MB by
     * default). `manual=true` is what makes the server write the URL to the user's `avatar` field.
     */
    suspend fun uploadAvatar(imageBytes: ByteArray, mimeType: String, fileName: String): AvatarUploadResponse =
        client.submitFormWithBinaryData(
            formData = formData {
                append("file", imageBytes, Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                    append(HttpHeaders.ContentType, mimeType)
                })
                append("manual", "true")
            },
        ) {
            url { path("api/files/images/avatar") }
        }.body()
}
