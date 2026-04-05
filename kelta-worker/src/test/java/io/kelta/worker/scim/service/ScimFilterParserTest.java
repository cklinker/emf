package io.kelta.worker.scim.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ScimFilterParser")
class ScimFilterParserTest {

    private ScimFilterParser parser;

    @BeforeEach
    void setUp() {
        parser = new ScimFilterParser(Map.of(
                "username", "pu.email",
                "name.familyname", "pu.last_name",
                "name_familyname", "pu.last_name",
                "displayname", "pu.display_name",
                "active", "pu.active",
                "emails.value", "pu.email",
                "emails_value", "pu.email"
        ));
    }

    @Test
    @DisplayName("null filter returns 1=1")
    void nullFilterReturnsAllRows() {
        ScimFilterParser.ParsedFilter result = parser.parse(null);
        assertThat(result.sql()).isEqualTo("1=1");
        assertThat(result.params()).isEmpty();
    }

    @Test
    @DisplayName("blank filter returns 1=1")
    void blankFilterReturnsAllRows() {
        ScimFilterParser.ParsedFilter result = parser.parse("  ");
        assertThat(result.sql()).isEqualTo("1=1");
        assertThat(result.params()).isEmpty();
    }

    @Test
    @DisplayName("eq operator generates equals clause")
    void eqOperator() {
        ScimFilterParser.ParsedFilter result = parser.parse("userName eq \"john@example.com\"");
        assertThat(result.sql()).isEqualTo("pu.email = ?");
        assertThat(result.params()).containsExactly("john@example.com");
    }

    @Test
    @DisplayName("co operator generates ILIKE clause")
    void coOperator() {
        ScimFilterParser.ParsedFilter result = parser.parse("userName co \"john\"");
        assertThat(result.sql()).isEqualTo("pu.email ILIKE ?");
        assertThat(result.params()).containsExactly("%john%");
    }

    @Test
    @DisplayName("sw operator generates starts-with ILIKE clause")
    void swOperator() {
        ScimFilterParser.ParsedFilter result = parser.parse("userName sw \"john\"");
        assertThat(result.sql()).isEqualTo("pu.email ILIKE ?");
        assertThat(result.params()).containsExactly("john%");
    }

    @Test
    @DisplayName("pr operator generates IS NOT NULL clause")
    void prOperator() {
        ScimFilterParser.ParsedFilter result = parser.parse("userName pr");
        assertThat(result.sql()).isEqualTo("pu.email IS NOT NULL");
        assertThat(result.params()).isEmpty();
    }

    @Test
    @DisplayName("and operator combines with AND")
    void andOperator() {
        ScimFilterParser.ParsedFilter result = parser.parse(
                "userName eq \"john@example.com\" and name.familyName eq \"Doe\"");
        assertThat(result.sql()).isEqualTo("(pu.email = ?) AND (pu.last_name = ?)");
        assertThat(result.params()).containsExactly("john@example.com", "Doe");
    }

    @Test
    @DisplayName("or operator combines with OR")
    void orOperator() {
        ScimFilterParser.ParsedFilter result = parser.parse(
                "userName eq \"john@a.com\" or userName eq \"jane@b.com\"");
        assertThat(result.sql()).isEqualTo("(pu.email = ?) OR (pu.email = ?)");
        assertThat(result.params()).containsExactly("john@a.com", "jane@b.com");
    }

    @Test
    @DisplayName("unknown attribute returns 1=0")
    void unknownAttribute() {
        ScimFilterParser.ParsedFilter result = parser.parse("unknownAttr eq \"value\"");
        assertThat(result.sql()).isEqualTo("1=0");
    }

    @Test
    @DisplayName("dotted attribute path resolves correctly")
    void dottedAttributePath() {
        ScimFilterParser.ParsedFilter result = parser.parse("emails.value eq \"test@test.com\"");
        assertThat(result.sql()).isEqualTo("pu.email = ?");
        assertThat(result.params()).containsExactly("test@test.com");
    }

    @Test
    @DisplayName("LIKE wildcards are escaped in filter values")
    void escapesLikeWildcards() {
        ScimFilterParser.ParsedFilter result = parser.parse("userName co \"50%_off\"");
        assertThat(result.sql()).isEqualTo("pu.email ILIKE ?");
        assertThat(result.params()).containsExactly("%50\\%\\_off%");
    }
}
