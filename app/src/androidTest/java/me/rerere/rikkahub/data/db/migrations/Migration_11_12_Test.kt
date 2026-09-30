package me.rerere.rikkahub.data.db.migrations

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class Migration_11_12_Test {
    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate11To12_createsMessageNodeTableWithCorrectSchema() {
        // Create a version 11 database
        helper.createDatabase(TEST_DB, 11).apply {
            close()
        }

        // Run the migration to version 12
        val db = helper.runMigrationsAndValidate(TEST_DB, 12, true, Migration_11_12)

        // Verify the table structure
        val cursor = db.query("SELECT * FROM message_node LIMIT 0")
        val columnNames = cursor.columnNames.toList()
        cursor.close()

        assertTrue("message_node table should exist", columnNames.isNotEmpty())
        assertTrue("Should have 'id' column", columnNames.contains("id"))
        assertTrue("Should have 'conversation_id' column", columnNames.contains("conversation_id"))
        assertTrue("Should have 'node_index' column", columnNames.contains("node_index"))
        assertTrue("Should have 'messages' column", columnNames.contains("messages"))
        assertTrue("Should have 'select_index' column", columnNames.contains("select_index"))

        db.close()
    }

    @Test
    fun migrate11To12_migratesSimpleConversationCorrectly() {
        // Prepare test data
        val conversationId = Uuid.random().toString()
        val messageNodes = listOf(
            MessageNode(
                id = Uuid.random(),
                messages = listOf(
                    UIMessage(
                        role = MessageRole.USER,
                        parts = listOf(UIMessagePart.Text("Hello"))
                    )
                ),
                selectIndex = 0
            ),
            MessageNode(
                id = Uuid.random(),
                messages = listOf(
                    UIMessage(
                        role = MessageRole.ASSISTANT,
                        parts = listOf(UIMessagePart.Text("Hi there!")),
                        modelId = Uuid.random(),
                        usage = TokenUsage(promptTokens = 10, completionTokens = 5)
                    )
                ),
                selectIndex = 0
            )
        )
        val nodesJson = JsonInstant.encodeToString(messageNodes)

        // Create a version 11 database and insert data
        helper.createDatabase(TEST_DB, 11).apply {
            val values = ContentValues().apply {
                put("id", conversationId)
                put("assistant_id", Uuid.random().toString())
                put("title", "Test Conversation")
                put("nodes", nodesJson)
                put("truncate_index", -1)
                put("suggestions", "[]")
                put("is_pinned", 0)
                put("create_at", Instant.now().toEpochMilli())
                put("update_at", Instant.now().toEpochMilli())
            }
            insert("conversationentity", SQLiteDatabase.CONFLICT_NONE, values)
            close()
        }

        // Run the migration
        val db = helper.runMigrationsAndValidate(TEST_DB, 12, true, Migration_11_12)

        // Verify the migration result
        val cursor = db.query(
            "SELECT * FROM message_node WHERE conversation_id = ? ORDER BY node_index ASC",
            arrayOf(conversationId)
        )

        assertEquals("Should have migrated 2 message nodes", 2, cursor.count)

        // Verify the first node
        assertTrue(cursor.moveToFirst())
        val firstNodeId = cursor.getString(cursor.getColumnIndex("id"))
        val firstConversationId = cursor.getString(cursor.getColumnIndex("conversation_id"))
        val firstNodeIndex = cursor.getInt(cursor.getColumnIndex("node_index"))
        val firstMessagesJson = cursor.getString(cursor.getColumnIndex("messages"))
        val firstSelectIndex = cursor.getInt(cursor.getColumnIndex("select_index"))

        assertNotNull("First node should have ID", firstNodeId)
        assertEquals("Conversation ID should match", conversationId, firstConversationId)
        assertEquals("First node index should be 0", 0, firstNodeIndex)
        assertEquals("First node selectIndex should be 0", 0, firstSelectIndex)

        val firstMessages = JsonInstant.decodeFromString<List<UIMessage>>(firstMessagesJson)
        assertEquals("First node should have 1 message", 1, firstMessages.size)
        assertEquals("First message should be from USER", MessageRole.USER, firstMessages[0].role)
        assertEquals(
            "First message content should match",
            "Hello",
            (firstMessages[0].parts[0] as UIMessagePart.Text).text
        )

        // Verify the second node
        assertTrue(cursor.moveToNext())
        val secondNodeIndex = cursor.getInt(cursor.getColumnIndex("node_index"))
        val secondMessagesJson = cursor.getString(cursor.getColumnIndex("messages"))

        assertEquals("Second node index should be 1", 1, secondNodeIndex)

        val secondMessages = JsonInstant.decodeFromString<List<UIMessage>>(secondMessagesJson)
        assertEquals("Second node should have 1 message", 1, secondMessages.size)
        assertEquals(
            "Second message should be from ASSISTANT",
            MessageRole.ASSISTANT,
            secondMessages[0].role
        )
        assertEquals(
            "Second message content should match",
            "Hi there!",
            (secondMessages[0].parts[0] as UIMessagePart.Text).text
        )

        cursor.close()

        // Verify the nodes field in the original conversationentity table has been cleared
        val conversationCursor = db.query(
            "SELECT nodes FROM conversationentity WHERE id = ?",
            arrayOf(conversationId)
        )
        assertTrue(conversationCursor.moveToFirst())
        val updatedNodes = conversationCursor.getString(0)
        assertEquals("Original nodes should be cleared to empty array", "[]", updatedNodes)
        conversationCursor.close()

        db.close()
    }

    @Test
    fun migrate11To12_handlesBranchedMessages() {
        // Prepare branched test data (one node with several alternative messages)
        val conversationId = Uuid.random().toString()
        val messageNodes = listOf(
            MessageNode(
                id = Uuid.random(),
                messages = listOf(
                    UIMessage(
                        role = MessageRole.ASSISTANT,
                        parts = listOf(UIMessagePart.Text("Response 1")),
                        modelId = Uuid.random()
                    ),
                    UIMessage(
                        role = MessageRole.ASSISTANT,
                        parts = listOf(UIMessagePart.Text("Response 2")),
                        modelId = Uuid.random()
                    ),
                    UIMessage(
                        role = MessageRole.ASSISTANT,
                        parts = listOf(UIMessagePart.Text("Response 3")),
                        modelId = Uuid.random()
                    )
                ),
                selectIndex = 1 // Select the second message
            )
        )
        val nodesJson = JsonInstant.encodeToString(messageNodes)

        // Create a version 11 database and insert data
        helper.createDatabase(TEST_DB, 11).apply {
            val values = ContentValues().apply {
                put("id", conversationId)
                put("assistant_id", Uuid.random().toString())
                put("title", "Branched Conversation")
                put("nodes", nodesJson)
                put("truncate_index", -1)
                put("suggestions", "[]")
                put("is_pinned", 0)
                put("create_at", Instant.now().toEpochMilli())
                put("update_at", Instant.now().toEpochMilli())
            }
            insert("conversationentity", SQLiteDatabase.CONFLICT_NONE, values)
            close()
        }

        // Run the migration
        val db = helper.runMigrationsAndValidate(TEST_DB, 12, true, Migration_11_12)

        // Verify the result
        val cursor = db.query(
            "SELECT * FROM message_node WHERE conversation_id = ?",
            arrayOf(conversationId)
        )

        assertEquals("Should have migrated 1 message node", 1, cursor.count)
        assertTrue(cursor.moveToFirst())

        val messagesJson = cursor.getString(cursor.getColumnIndex("messages"))
        val selectIndex = cursor.getInt(cursor.getColumnIndex("select_index"))

        val messages = JsonInstant.decodeFromString<List<UIMessage>>(messagesJson)
        assertEquals("Node should have 3 messages", 3, messages.size)
        assertEquals("selectIndex should be preserved", 1, selectIndex)
        assertEquals(
            "Should preserve all message variants",
            "Response 2",
            (messages[1].parts[0] as UIMessagePart.Text).text
        )

        cursor.close()
        db.close()
    }

    @Test
    fun migrate11To12_handlesEmptyConversations() {
        // Prepare test data with an empty node list
        val conversationId = Uuid.random().toString()
        val nodesJson = "[]"

        // Create a version 11 database and insert data
        helper.createDatabase(TEST_DB, 11).apply {
            val values = ContentValues().apply {
                put("id", conversationId)
                put("assistant_id", Uuid.random().toString())
                put("title", "Empty Conversation")
                put("nodes", nodesJson)
                put("truncate_index", -1)
                put("suggestions", "[]")
                put("is_pinned", 0)
                put("create_at", Instant.now().toEpochMilli())
                put("update_at", Instant.now().toEpochMilli())
            }
            insert("conversationentity", SQLiteDatabase.CONFLICT_NONE, values)
            close()
        }

        // Run the migration
        val db = helper.runMigrationsAndValidate(TEST_DB, 12, true, Migration_11_12)

        // Verify the result
        val cursor = db.query(
            "SELECT * FROM message_node WHERE conversation_id = ?",
            arrayOf(conversationId)
        )

        assertEquals("Empty conversation should have no message nodes", 0, cursor.count)
        cursor.close()

        // Verify the conversation still exists
        val conversationCursor = db.query(
            "SELECT id FROM conversationentity WHERE id = ?",
            arrayOf(conversationId)
        )
        assertEquals("Conversation should still exist", 1, conversationCursor.count)
        conversationCursor.close()

        db.close()
    }

    @Test
    fun migrate11To12_handlesMultipleConversations() {
        // Prepare test data with multiple conversations
        val conversationId1 = Uuid.random().toString()
        val conversationId2 = Uuid.random().toString()

        val nodes1 = listOf(
            MessageNode(
                id = Uuid.random(),
                messages = listOf(
                    UIMessage(
                        role = MessageRole.USER,
                        parts = listOf(UIMessagePart.Text("Conversation 1"))
                    )
                ),
                selectIndex = 0
            )
        )

        val nodes2 = listOf(
            MessageNode(
                id = Uuid.random(),
                messages = listOf(
                    UIMessage(
                        role = MessageRole.USER,
                        parts = listOf(UIMessagePart.Text("Conversation 2 - Message 1"))
                    )
                ),
                selectIndex = 0
            ),
            MessageNode(
                id = Uuid.random(),
                messages = listOf(
                    UIMessage(
                        role = MessageRole.USER,
                        parts = listOf(UIMessagePart.Text("Conversation 2 - Message 2"))
                    )
                ),
                selectIndex = 0
            )
        )

        // Create a version 11 database and insert data
        helper.createDatabase(TEST_DB, 11).apply {
            val values1 = ContentValues().apply {
                put("id", conversationId1)
                put("assistant_id", Uuid.random().toString())
                put("title", "Conversation 1")
                put("nodes", JsonInstant.encodeToString(nodes1))
                put("truncate_index", -1)
                put("suggestions", "[]")
                put("is_pinned", 0)
                put("create_at", Instant.now().toEpochMilli())
                put("update_at", Instant.now().toEpochMilli())
            }
            insert("conversationentity", SQLiteDatabase.CONFLICT_NONE, values1)

            val values2 = ContentValues().apply {
                put("id", conversationId2)
                put("assistant_id", Uuid.random().toString())
                put("title", "Conversation 2")
                put("nodes", JsonInstant.encodeToString(nodes2))
                put("truncate_index", -1)
                put("suggestions", "[]")
                put("is_pinned", 0)
                put("create_at", Instant.now().toEpochMilli())
                put("update_at", Instant.now().toEpochMilli())
            }
            insert("conversationentity", SQLiteDatabase.CONFLICT_NONE, values2)
            close()
        }

        // Run the migration
        val db = helper.runMigrationsAndValidate(TEST_DB, 12, true, Migration_11_12)

        // Verify the node count of the first conversation
        val cursor1 = db.query(
            "SELECT * FROM message_node WHERE conversation_id = ?",
            arrayOf(conversationId1)
        )
        assertEquals("Conversation 1 should have 1 message node", 1, cursor1.count)
        cursor1.close()

        // Verify the node count of the second conversation
        val cursor2 = db.query(
            "SELECT * FROM message_node WHERE conversation_id = ?",
            arrayOf(conversationId2)
        )
        assertEquals("Conversation 2 should have 2 message nodes", 2, cursor2.count)
        cursor2.close()

        // Verify the total node count
        val cursorAll = db.query("SELECT * FROM message_node")
        assertEquals("Total should have 3 message nodes", 3, cursorAll.count)
        cursorAll.close()

        db.close()
    }

    @Test
    fun migrate11To12_createsIndexOnConversationId() {
        // Create a version 11 database
        helper.createDatabase(TEST_DB, 11).apply {
            close()
        }

        // Run the migration
        val db = helper.runMigrationsAndValidate(TEST_DB, 12, true, Migration_11_12)

        // Verify the index was created
        val cursor = db.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='message_node' AND name='index_message_node_conversation_id'"
        )

        assertTrue("Index on conversation_id should exist", cursor.count > 0)
        cursor.close()
        db.close()
    }

    @Test
    fun migrate11To12_handlesVeryLargeConversations() {
        // Prepare one very large conversation and one normal conversation
        val largeConversationId = Uuid.random().toString()
        val normalConversationId = Uuid.random().toString()

        // Create a very large conversation with many message nodes (simulates SQLiteBlobTooBigException)
        val largeNodes = buildList {
            repeat(5000) { i ->
                add(
                    MessageNode(
                        id = Uuid.random(),
                        messages = listOf(
                            UIMessage(
                                role = MessageRole.USER,
                                parts = listOf(
                                    UIMessagePart.Text(
                                        "Message $i with some content to increase size " + "x".repeat(
                                            100
                                        )
                                    )
                                )
                            ),
                            UIMessage(
                                role = MessageRole.ASSISTANT,
                                parts = listOf(
                                    UIMessagePart.Text(
                                        "Response $i with some content to increase size " + "y".repeat(
                                            100
                                        )
                                    )
                                ),
                                modelId = Uuid.random()
                            )
                        ),
                        selectIndex = 0
                    )
                )
            }
        }

        val normalNodes = listOf(
            MessageNode(
                id = Uuid.random(),
                messages = listOf(
                    UIMessage(
                        role = MessageRole.USER,
                        parts = listOf(UIMessagePart.Text("Normal conversation message"))
                    )
                ),
                selectIndex = 0
            )
        )

        // Create a version 11 database and insert data
        helper.createDatabase(TEST_DB, 11).apply {
            // Insert the very large conversation
            val largeValues = ContentValues().apply {
                put("id", largeConversationId)
                put("assistant_id", Uuid.random().toString())
                put("title", "Very Large Conversation")
                put("nodes", JsonInstant.encodeToString(largeNodes))
                put("truncate_index", -1)
                put("suggestions", "[]")
                put("is_pinned", 0)
                put("create_at", Instant.now().toEpochMilli())
                put("update_at", Instant.now().toEpochMilli())
            }
            insert("conversationentity", SQLiteDatabase.CONFLICT_NONE, largeValues)

            // Insert the normal conversation
            val normalValues = ContentValues().apply {
                put("id", normalConversationId)
                put("assistant_id", Uuid.random().toString())
                put("title", "Normal Conversation")
                put("nodes", JsonInstant.encodeToString(normalNodes))
                put("truncate_index", -1)
                put("suggestions", "[]")
                put("is_pinned", 0)
                put("create_at", Instant.now().toEpochMilli())
                put("update_at", Instant.now().toEpochMilli())
            }
            insert("conversationentity", SQLiteDatabase.CONFLICT_NONE, normalValues)
            close()
        }

        // Run the migration - it should not fail even if the very large conversation can't be processed
        val db = helper.runMigrationsAndValidate(TEST_DB, 12, true, Migration_11_12)

        // Verify the message nodes of the very large conversation (skipped or migrated depending on the actual blob size)
        val largeCursor = db.query(
            "SELECT * FROM message_node WHERE conversation_id = ?",
            arrayOf(largeConversationId)
        )
        val largeNodesMigrated = largeCursor.count
        largeCursor.close()

        // The normal conversation should migrate successfully
        val normalCursor = db.query(
            "SELECT * FROM message_node WHERE conversation_id = ?",
            arrayOf(normalConversationId)
        )
        assertEquals("Normal conversation should be migrated successfully", 1, normalCursor.count)
        normalCursor.close()

        // Verify both conversation records still exist
        val conversationsCursor = db.query("SELECT id FROM conversationentity")
        assertEquals("Both conversations should still exist", 2, conversationsCursor.count)
        conversationsCursor.close()

        // If the very large conversation was skipped, its nodes field should still hold the original data
        // If it migrated successfully, the nodes field should be cleared to "[]"
        val largeConvCursor = db.query(
            "SELECT nodes FROM conversationentity WHERE id = ?",
            arrayOf(largeConversationId)
        )
        assertTrue(largeConvCursor.moveToFirst())
        val largeConvNodes = largeConvCursor.getString(0)
        largeConvCursor.close()

        // The nodes of the normal conversation should be cleared
        val normalConvCursor = db.query(
            "SELECT nodes FROM conversationentity WHERE id = ?",
            arrayOf(normalConversationId)
        )
        assertTrue(normalConvCursor.moveToFirst())
        val normalConvNodes = normalConvCursor.getString(0)
        assertEquals("Normal conversation nodes should be cleared", "[]", normalConvNodes)
        normalConvCursor.close()

        Log.i(
            "Migration_11_12_Test",
            "Large conversation migration result: $largeNodesMigrated nodes migrated, nodes field: ${if (largeConvNodes == "[]") "cleared" else "preserved"}"
        )

        db.close()
    }
}
