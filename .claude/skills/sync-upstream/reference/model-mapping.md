# Model Mapping: Official TypeScript → Android Kotlin

Maps official TypeScript types from `packages/data-provider/src/` to Android Kotlin data classes in `core/model/`.

## Core Models (Implemented)

| TypeScript Type | Kotlin Class | File | Notes |
|----------------|-------------|------|-------|
| `TMessage` | `Message` | `Message.kt` | Includes sender, text, children, content parts |
| `TConversation` | `Conversation` | `Conversation.kt` | Core conversation metadata |
| `TConversationTag` | `ConversationTag` | `ConversationTag.kt` | Tag/bookmark on conversations |
| `TPreset` | `Preset` | `Preset.kt` | Saved endpoint configurations |
| `TPromptGroup` / `TPrompt` | `Prompt` | `Prompt.kt` | Prompts library entries |
| `TSharedLink` | `SharedLink` | `SharedLink.kt` | Shared conversation links |
| `TStartupConfig` | `StartupConfig` | `StartupConfig.kt` | `/api/config` response |
| `TEndpointConfig` | `EndpointConfig` | `EndpointConfig.kt` | Per-endpoint configuration |
| `EModelEndpoint` | `Endpoint` | `Endpoint.kt` | Enum of supported endpoints |
| `TUser` | `User` | `User.kt` | User profile data |
| `TBalance` | `Balance` | `Balance.kt` | Token balance |
| `TBanner` | `Banner` | `Banner.kt` | System banner messages |
| `TFile` | `FileModel` | `FileModel.kt` | Uploaded file metadata |
| `Agent` | `Agent` | `Agent.kt` | Agent definition |
| `AgentExpanded` | `AgentExpanded` | `AgentExpanded.kt` | Agent with full details |
| `PaginatedAgents` | `PaginatedAgents` | `PaginatedAgents.kt` | Paginated agent list response |
| `AgentCategory` | `AgentCategory` | `AgentCategory.kt` | Agent marketplace categories |
| `TMemory` | `Memory` | `Memory.kt` | Memory/context entries |
| `TUserKey` | `UserKey` | `UserKey.kt` | User API keys |
| `ApiKey` | `ApiKey` | `ApiKey.kt` | API key management |

## SSE/Streaming Models (Android-specific)

| Kotlin Class | File | Purpose |
|-------------|------|---------|
| `StreamEvent` | `StreamEvent.kt` | Sealed class for SSE event types |
| `SseContentEvent` | `SseContentEvent.kt` | Content delta events during streaming |
| `ToolCallRecord` | `ToolCallRecord.kt` | Tool call progress tracking |
| `ToolCallResult` | `ToolCallResult.kt` | Tool call completion data |
| `ToolAuthStatus` | `ToolAuthStatus.kt` | Tool authentication state |

## Android-Only Models

| Kotlin Class | File | Purpose |
|-------------|------|---------|
| `LoginOutcome` | `LoginOutcome.kt` | Auth flow result sealed class |
| `ConversationExport` | `ConversationExport.kt` | Export/import format |
| `Feedback` | `Feedback.kt` | Message feedback (thumbs up/down) |
| `SupportContact` | `SupportContact.kt` | Help/support info |
| `MessageContentPart` | `MessageContentPart.kt` | Rich content (text, image, file) |
| `ParameterDefinition` | `ParameterDefinition.kt` | Model parameter config |
| `AgentAction` | `AgentAction.kt` | Agent action definitions |
| `AgentTool` | `AgentTool.kt` | Agent tool definitions |

## Key Type Sources in Official Repo

| File | Contains |
|------|----------|
| `packages/data-provider/src/types.ts` | Main type exports |
| `packages/data-provider/src/types/queries.ts` | React Query response types |
| `packages/data-provider/src/types/mutations.ts` | Mutation request/response types |
| `packages/data-provider/src/types/agents.ts` | Agent-specific types |
| `packages/data-provider/src/types/files.ts` | File-related types |
| `packages/data-provider/src/types/runs.ts` | Run/streaming types |
| `packages/data-provider/src/schemas.ts` | Zod validation schemas |
| `packages/data-provider/src/config.ts` | Config types and VERSION constant |
| `packages/data-provider/src/api-endpoints.ts` | Canonical endpoint URL builders — check here first for renames/new routes |
| `packages/data-provider/src/data-service.ts` | HTTP client functions — actual request shapes sent by the web client |
| `packages/data-provider/src/parsers.ts` | Request/response parsers; hints at shape normalization |
| `packages/data-provider/src/permissions.ts` | Permission schemas and role gating |
