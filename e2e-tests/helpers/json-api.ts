/**
 * JSON:API payload builders for test data.
 */

export function jsonApiPayload(
  type: string,
  attributes: Record<string, unknown>,
  id?: string,
) {
  return {
    data: {
      ...(id ? { id } : {}),
      type,
      attributes,
    },
  };
}

export function jsonApiListResponse(
  type: string,
  items: Array<{ id: string; attributes: Record<string, unknown> }>,
) {
  return {
    data: items.map((item) => ({
      id: item.id,
      type,
      attributes: item.attributes,
    })),
    meta: {
      totalCount: items.length,
      currentPage: 1,
      pageSize: 25,
      totalPages: 1,
    },
  };
}
