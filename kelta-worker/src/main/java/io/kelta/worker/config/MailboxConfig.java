package io.kelta.worker.config;

import io.kelta.runtime.workflow.BeforeSaveHookRegistry;
import io.kelta.worker.repository.EmailRepository;
import io.kelta.worker.service.mailbox.MailboxTemplateGuardHook;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring for the support mailbox.
 *
 * @since 1.0.0
 */
@Configuration
public class MailboxConfig {

    /**
     * Registers the author-time guard on {@code mailbox-templates}.
     *
     * <p>Registered as a hook rather than enforced in a controller because the collection is
     * writable over the generic JSON:API route — a controller-only check would be reachable
     * around by writing the record directly.
     */
    @Bean
    public MailboxTemplateGuardHook mailboxTemplateGuardHook(BeforeSaveHookRegistry hookRegistry,
                                                             EmailRepository emailRepository) {
        MailboxTemplateGuardHook hook = new MailboxTemplateGuardHook(emailRepository);
        hookRegistry.register(hook);
        return hook;
    }
}
