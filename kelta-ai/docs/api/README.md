# kelta-ai API Reference

Base path: `/api/ai`

All endpoints require a valid JWT in the `Authorization: Bearer <token>` header. The token must be issued by `kelta-auth` and must include a resolvable tenant claim.

---

## POST /api/ai/chat

Send a message to the AI assistant and receive a complete response.

**Request**

```json
{
  "conversationId": "uuid (optional — omit to start a new conversation)",
  "message": "What fields are on the Opportunity collection?"
}
```

**Response** `200 OK`

```json
{
  "conversationId": "550e8400-e29b-41d4-a716-446655440000",
  "reply": "The Opportunity collection has the following fields: ...",
  "inputTokens": 312,
  "outputTokens": 128,
  "model": "claude-opus-4-5"
}
```

**Error Responses**

| Status | Meaning |
|--------|---------|
| 400 | Missing or invalid request body |
| 401 | Missing or invalid JWT |
| 429 | Anthropic rate limit exceeded — retry after the indicated delay |
| 502 | Upstream Anthropic API error |

---

## POST /api/ai/chat/stream

Send a message and receive the response as a Server-Sent Events stream.

**Request** — same body as `/api/ai/chat`

**Response** `200 OK` with `Content-Type: text/event-stream`

Each SSE event contains a JSON delta:

```
data: {"type":"content_block_delta","delta":{"type":"text_delta","text":"The "}}

data: {"type":"content_block_delta","delta":{"type":"text_delta","text":"Opportunity "}}

data: {"type":"message_stop","conversationId":"550e8400-...","inputTokens":312,"outputTokens":128}
```

The stream ends with a `message_stop` event that includes final token counts and the `conversationId` (use this to continue the conversation).

---

## GET /api/ai/conversations/{conversationId}

Retrieve the full message history for a conversation.

**Response** `200 OK`

```json
{
  "conversationId": "550e8400-e29b-41d4-a716-446655440000",
  "messages": [
    { "role": "user", "content": "What fields are on the Opportunity collection?", "createdAt": "2026-04-06T10:00:00Z" },
    { "role": "assistant", "content": "The Opportunity collection has ...", "createdAt": "2026-04-06T10:00:02Z" }
  ]
}
```

---

## DELETE /api/ai/conversations/{conversationId}

Delete a conversation and its cached context.

**Response** `204 No Content`

---

## GET /actuator/health

Standard Spring Boot actuator health endpoint. Returns `{"status":"UP"}` when the service is healthy.
