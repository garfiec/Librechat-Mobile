# API Route Mapping: Official → Mobile

Maps official LibreChat API route files to their mobile `*Api.kt` counterparts.

## Implemented

| Official Route | Mobile API File | Notes |
|---------------|-----------------|-------|
| `routes/auth.js` | `AuthApi.kt` | Login, register, refresh, logout, 2FA |
| `routes/oauth.js` | `AuthApi.kt` | OAuth flows (shared file) |
| `routes/config.js` | `ConfigApi.kt` | Startup config, version check |
| `routes/convos.js` | `ConversationsApi.kt` | CRUD, archive, share, fork, duplicate; v0.8.8 per-conversation background tasks (list + cancel) |
| `routes/messages.js` | `MessagesApi.kt` | Get messages by conversation |
| `routes/search.js` | `SearchApi.kt` | Conversation search |
| `routes/tags.js` | `TagsApi.kt` | Conversation tags/bookmarks |
| `routes/presets.js` | `PresetsApi.kt` | Save/load presets |
| `routes/prompts.js` | `PromptsApi.kt` | Prompts library |
| `routes/agents/` (dir) | `AgentsApi.kt` | Agent CRUD, marketplace |
| `routes/files/` (dir) | `FilesApi.kt`, `FilesExtApi.kt` | Upload, list, delete, external files |
| `routes/share.js` | `ShareApi.kt` | Shared conversation links |
| `routes/user.js` | `UserApi.kt` | Profile, delete account |
| `routes/balance.js` | `BalanceApi.kt` | Token balance check |
| `routes/banner.js` | `BannerApi.kt` | System banners |
| `routes/keys.js` | `KeysApi.kt` | User API keys |
| `routes/mcp.js` | `McpApi.kt` | MCP server management |
| `routes/memories.js` | `MemoriesApi.kt` | Memory/context management |
| `routes/skills.js` | `SkillsApi.kt` | Skills CRUD, import, flat file list/upload/delete; v0.8.8 conditional per-file edit (`POST /:id/files/*relativePath` + `expectedFileId`), inline-only file writes |
| N/A | `ChatApi.kt` | SSE streaming (POST + GET, not a standard route) |
| N/A | `SpeechApi.kt` | Text-to-speech |
| N/A | `ApiKeysApi.kt` | API key management |

## Gaps (no mobile counterpart yet)

| Official Route | What It Does | Priority |
|---------------|-------------|----------|
| `routes/admin/auth.js` | Admin-only authentication routes | Low — admin feature, not exposed in mobile |
| `routes/categories.js` | Agent categories | Low — categories embedded in agent responses |
| `routes/endpoints.js` | List available endpoints | Medium — Mobile uses config response instead |
| `routes/models.js` | List available models per endpoint | Medium — Mobile uses config response instead |
| `routes/roles.js` | Role-based access control (admin) | Low — admin feature |
| `routes/settings.js` | User settings CRUD | Medium — Mobile uses local DataStore |
| `routes/actions.js` | Custom actions/plugins | Low — plugin system |
| `routes/assistants/` (dir) | OpenAI Assistants API | Low — agents supersede assistants |
| `routes/accessPermissions.js` | File access permissions | Low — admin feature |
| `routes/static.js` | Static file serving | N/A — not applicable to mobile |
