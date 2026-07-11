package ai.abundera.sign.model;

/** Offset + cursor pagination block shared by every list endpoint. */
public record Pagination(Integer page, Integer totalPages, int limit, int total, String nextCursor) {
}
