# core:model

All `@Serializable` data classes -- domain models, DTOs, request/response wrappers. This is the shared contract between network, data, and feature modules.

## What This Module Provides

- **Domain models**: `User`, `Conversation`, `Message`, `MessageContentPart`, `Agent`, `Preset`, `Prompt`, `PromptGroup`, `ConversationTag`, `SharedLink`, `FileObject`, `Balance`, `StartupConfig`, `ModelSpec`
- **Enums**: `EModelEndpoint`, `ContentType`, `StepType`, `ToolCallType`, `FeedbackRating`, `Provider`
- **StreamEvent sealed hierarchy**: `ContentDelta`, `ToolCallStart`, `ToolCallComplete`, `ThinkingDelta`, `Final`, `Sync`, `Error`, `Created`, `Step`, `AttachmentCreated`
- **Request/response wrappers**: `LoginRequest`, `LoginResponse`, `RegisterRequest`, `ChatRequest`, `ConvoUpdateBody`, `ForkConvoRequest`, etc.

## Key Patterns

- **`arg` wrapper for mutation endpoints**: The backend reads `req.body.arg` on conversation update/delete/archive. Request bodies use: `{ "arg": { "conversationId": "...", "title": "..." } }`. See `ConvoUpdateBody` / `ConvoDeleteBody`.
- **Nullable fields with defaults**: Most fields are nullable with `= null` defaults. The backend schema is loose (Mongoose + Zod). Always use `@SerialName` when the JSON key differs from Kotlin naming.
- **`ignoreUnknownKeys = true`**: The server may add new fields at any time. Never assume the response shape is exhaustive.

## Directory Convention

- Top-level: domain model classes (Conversation.kt, Message.kt, etc.)
- `request/`: Request body data classes (LoginRequest, ChatRequest, etc.)
- `response/`: API response wrappers (LoginResponse, ConversationListResponse, etc.)

## Rules

- **Pure Kotlin only.** NO Android framework dependencies. No `Context`, no `Parcelable`.
- Only dependency beyond `:core:common` is `kotlinx-serialization-json`.
- Use `@Serializable` on every data class. Use `@SerialName` for snake_case JSON fields.
- Use `JsonObject` / `JsonElement` for truly polymorphic/mixed-type fields (e.g., `Agent.avatar`, `Feedback.tag`).
- Convention plugins: `librechat.mobile.library` + `librechat.kotlin.serialization`.
