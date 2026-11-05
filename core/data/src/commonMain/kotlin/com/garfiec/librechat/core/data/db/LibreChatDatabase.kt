package com.garfiec.librechat.core.data.db

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import com.garfiec.librechat.core.data.db.converter.Converters
import com.garfiec.librechat.core.data.db.dao.AccountClaimDao
import com.garfiec.librechat.core.data.db.dao.AgentDao
import com.garfiec.librechat.core.data.db.dao.ArtifactShortcutDao
import com.garfiec.librechat.core.data.db.dao.ConversationDao
import com.garfiec.librechat.core.data.db.dao.ConversationTagDao
import com.garfiec.librechat.core.data.db.dao.DraftDao
import com.garfiec.librechat.core.data.db.dao.MessageDao
import com.garfiec.librechat.core.data.db.dao.MessageSearchDao
import com.garfiec.librechat.core.data.db.dao.PrefetchWatermarkDao
import com.garfiec.librechat.core.data.db.dao.PresetDao
import com.garfiec.librechat.core.data.db.dao.ProjectOrderDao
import com.garfiec.librechat.core.data.db.dao.ServerDao
import com.garfiec.librechat.core.data.db.dao.ToolCallPartDao
import com.garfiec.librechat.core.data.db.entity.AgentEntity
import com.garfiec.librechat.core.data.db.entity.ArtifactShortcutEntity
import com.garfiec.librechat.core.data.db.entity.ConversationEntity
import com.garfiec.librechat.core.data.db.entity.ConversationTagEntity
import com.garfiec.librechat.core.data.db.entity.DraftEntity
import com.garfiec.librechat.core.data.db.entity.MessageEntity
import com.garfiec.librechat.core.data.db.entity.MessageSearchEntity
import com.garfiec.librechat.core.data.db.entity.MessageSearchFtsEntity
import com.garfiec.librechat.core.data.db.entity.PrefetchWatermarkEntity
import com.garfiec.librechat.core.data.db.entity.PresetEntity
import com.garfiec.librechat.core.data.db.entity.ProjectOrderEntity
import com.garfiec.librechat.core.data.db.entity.ServerEntity
import com.garfiec.librechat.core.data.db.entity.ToolCallPartEntity

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        AgentEntity::class,
        PresetEntity::class,
        ConversationTagEntity::class,
        DraftEntity::class,
        ArtifactShortcutEntity::class,
        ServerEntity::class,
        PrefetchWatermarkEntity::class,
        MessageSearchEntity::class,
        MessageSearchFtsEntity::class,
        ProjectOrderEntity::class,
        ToolCallPartEntity::class,
    ],
    version = 15,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        // 3 -> 4 and 4 -> 5 are manual (MIGRATION_3_4 / MIGRATION_4_5 in Migrations.kt, registered
        // via addMigrations in the platform DI); 4 -> 5 is account-tenancy: accountId + drop files
        // table. 5 -> 6 adds every nullable v0.8.7 column in one hop — `pinned` and `chatProjectId`
        // on conversations (pinning + Chat Projects assignment) and `quotes` on messages
        // (referenced-text excerpts). Bundled into a single hop because none shipped as a released
        // DB version — there is no in-the-wild v6 to migrate through.
        AutoMigration(from = 5, to = 6),
        // 6 -> 7 adds the device-scoped artifact_shortcuts table (home-screen artifact snapshots).
        AutoMigration(from = 6, to = 7),
        // 7 -> 8 adds the device-scoped servers table (per-deployment registry; gateway headers).
        AutoMigration(from = 7, to = 8),
        // 8 -> 9 adds the account-scoped prefetch_watermarks table (background cache warming).
        AutoMigration(from = 8, to = 9),
        // 9 -> 10 adds the message search side table and its FTS4 index, both empty: DDL only, it
        // never reads `messages`. SearchIndexBackfillSessionTask fills them after launch.
        AutoMigration(from = 9, to = 10),
        AutoMigration(from = 10, to = 11),
        // 11 -> 12 adds `manualSkills` and `alwaysAppliedSkills` on messages, so a user turn's
        // skill pills survive a reload and a regenerate after reload replays its picks.
        AutoMigration(from = 11, to = 12),
        // 12 -> 13 adds the account-scoped project_order table (Switchboard-only drawer folder order).
        AutoMigration(from = 12, to = 13),
        // 13 -> 14 adds every nullable v0.8.9-rc1 column in one hop (none shipped as a released DB
        // version). On messages: `isUserSubmitted`, so a user's own text that quotes a wake-up header
        // stays a user bubble when the chat opens from the cache; `reasoningOverride`, so an edit or
        // regenerate of a cached turn sends the one-turn reasoning choice it was sent with; and
        // `privacyRevision`, so a redacted turn opened from the cache still says its private details
        // were hidden and fetches the original (only the marker: the original text is never stored).
        // On conversations: the five read-state columns (unseen replies); rows cached before it read
        // as seen until the next list page restamps them. And `isCodeConversation` (null = not known),
        // so the drawer asks for pull requests only for code chats from a cold start.
        AutoMigration(from = 13, to = 14),
        // 14 -> 15 adds the account-scoped tool_call_parts table: full tool calls fetched for previewed
        // cards of finished messages, so a later or offline session opens them without a refetch.
        AutoMigration(from = 14, to = 15),
    ],
)
@TypeConverters(Converters::class)
@ConstructedBy(LibreChatDatabaseConstructor::class)
abstract class LibreChatDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun agentDao(): AgentDao
    abstract fun presetDao(): PresetDao
    abstract fun conversationTagDao(): ConversationTagDao
    abstract fun draftDao(): DraftDao
    abstract fun accountClaimDao(): AccountClaimDao
    abstract fun artifactShortcutDao(): ArtifactShortcutDao
    abstract fun serverDao(): ServerDao
    abstract fun prefetchWatermarkDao(): PrefetchWatermarkDao
    abstract fun messageSearchDao(): MessageSearchDao
    abstract fun projectOrderDao(): ProjectOrderDao
    abstract fun toolCallPartDao(): ToolCallPartDao
}

// Room KSP auto-generates the actual implementations for each platform
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object LibreChatDatabaseConstructor : RoomDatabaseConstructor<LibreChatDatabase>
