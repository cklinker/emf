package io.kelta.worker.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Subject normalisation, which decides whether a reply threads or starts a new conversation.
 *
 * <p>Getting this wrong is not cosmetic: under-normalising means every reply opens a new thread
 * and the SLA clock restarts each time, so a conversation can never breach.
 */
@DisplayName("MailboxThreadRepository.normalizeSubject")
class MailboxThreadRepositorySubjectTest {

    @ParameterizedTest(name = "[{0}] -> [{1}]")
    @CsvSource({
            "'Re: Booking question','booking question'",
            "'RE: Booking question','booking question'",
            "'Re: Re: Re: Booking question','booking question'",
            "'Fwd: Booking question','booking question'",
            "'FW: Booking question','booking question'",
            // Non-English prefixes are the ones usually missed: a German or French client's
            // replies would each open a new thread without these.
            "'AW: Booking question','booking question'",
            "'TR: Booking question','booking question'",
            "'SV: Booking question','booking question'",
            "'Re[2]: Booking question','booking question'",
            "'  Booking   question  ','booking question'",
    })
    @DisplayName("Reply and forward prefixes are stripped, whitespace collapsed, case folded")
    void normalizes(String input, String expected) {
        assertThat(MailboxThreadRepository.normalizeSubject(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("A subject that is only a prefix normalises to nothing, not an empty string")
    void prefixOnlyBecomesNull() {
        // Returning "" would make every such message thread with every other one.
        assertThat(MailboxThreadRepository.normalizeSubject("Re:")).isNull();
        assertThat(MailboxThreadRepository.normalizeSubject("   ")).isNull();
        assertThat(MailboxThreadRepository.normalizeSubject(null)).isNull();
    }

    @Test
    @DisplayName("Long subjects are truncated to fit the column")
    void truncatesLongSubjects() {
        String result = MailboxThreadRepository.normalizeSubject("a".repeat(400));
        assertThat(result).hasSize(255);
    }

    @Test
    @DisplayName("A prefix-like word inside the subject is not stripped")
    void doesNotStripMidSubject() {
        assertThat(MailboxThreadRepository.normalizeSubject("Question re: my booking"))
                .isEqualTo("question re: my booking");
    }
}
