package com.qolve.fluyo.data.dto

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class CreateRpcParamsTest {

    @Test
    fun `expense create parameters use exact database argument names`() {
        val json = Json.encodeToJsonElement(
            ExpenseCreateRpcParams(
                requestId = "request-1",
                amount = 12.34,
                categoryId = null,
                description = "Taxi",
                expenseDate = "2026-07-22",
                source = "manual",
                recipient = null,
                imageUrl = null,
            ),
        ).jsonObject

        assertEquals(
            setOf(
                "p_request_id",
                "p_amount",
                "p_category_id",
                "p_description",
                "p_expense_date",
                "p_source",
                "p_recipient",
                "p_image_url",
            ),
            json.keys,
        )
        assertEquals("request-1", json.getValue("p_request_id").jsonPrimitive.content)
        assertEquals("manual", json.getValue("p_source").jsonPrimitive.content)
    }

    @Test
    fun `goal create parameters use exact database argument names`() {
        val json = Json.encodeToJsonElement(
            GoalCreateRpcParams(
                requestId = "request-2",
                name = "Laptop",
                targetAmount = 1000.0,
                deadline = null,
            ),
        ).jsonObject

        assertEquals(
            setOf("p_request_id", "p_name", "p_target_amount", "p_deadline"),
            json.keys,
        )
        assertEquals("request-2", json.getValue("p_request_id").jsonPrimitive.content)
        assertEquals("Laptop", json.getValue("p_name").jsonPrimitive.content)
    }

    @Test
    fun `expense page sends explicit snapshot cursor and bounded page size`() {
        val json = Json.encodeToJsonElement(
            ExpensePageRpcParams(
                from = "2000-01-01",
                to = "2026-07-22",
                snapshotAt = null,
                beforeCreatedAt = null,
                beforeId = null,
                pageSize = 500,
            ),
        ).jsonObject

        assertEquals(
            setOf(
                "p_from",
                "p_to",
                "p_snapshot_at",
                "p_before_created_at",
                "p_before_id",
                "p_page_size",
            ),
            json.keys,
        )
        assertEquals("500", json.getValue("p_page_size").jsonPrimitive.content)
    }
}
