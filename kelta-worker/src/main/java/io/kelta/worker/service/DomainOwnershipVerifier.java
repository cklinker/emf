package io.kelta.worker.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.Hashtable;

/**
 * Confirms domain ownership by reading the customer-published DNS TXT record
 * at {@code _kelta-verify.<domain>} and comparing it to the token we issued
 * at registration time.
 *
 * <p>JNDI's DNS provider is used directly (no extra deps) so this works in
 * GraalVM native images without resource-config plumbing.
 */
@Component
public class DomainOwnershipVerifier {

    private static final Logger log = LoggerFactory.getLogger(DomainOwnershipVerifier.class);
    private static final String CHALLENGE_PREFIX = "_kelta-verify.";

    /** Name customers create the TXT record under (e.g. {@code _kelta-verify.acme.com}). */
    public String challengeRecordName(String domain) {
        return CHALLENGE_PREFIX + domain;
    }

    /**
     * Returns {@code true} when {@code _kelta-verify.<domain>} resolves to a
     * TXT record whose value equals {@code expectedToken}. Quote / unquote
     * idiosyncrasies (some DNS UIs surround the value in literal quotes) are
     * normalised before comparison.
     */
    public boolean verify(String domain, String expectedToken) {
        if (domain == null || domain.isBlank() || expectedToken == null || expectedToken.isBlank()) {
            return false;
        }
        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
            env.put("com.sun.jndi.dns.timeout.initial", "2000");
            env.put("com.sun.jndi.dns.timeout.retries", "2");
            DirContext ctx = new InitialDirContext(env);
            try {
                Attributes attrs = ctx.getAttributes(challengeRecordName(domain), new String[] { "TXT" });
                Attribute txt = attrs.get("TXT");
                if (txt == null) return false;
                NamingEnumeration<?> values = txt.getAll();
                while (values.hasMore()) {
                    String value = strip(values.next().toString());
                    if (expectedToken.equals(value)) return true;
                }
                return false;
            } finally {
                ctx.close();
            }
        } catch (Exception e) {
            log.debug("TXT verification lookup for {} failed: {}", domain, e.getMessage());
            return false;
        }
    }

    private static String strip(String s) {
        if (s == null) return "";
        String v = s.trim();
        if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
            v = v.substring(1, v.length() - 1);
        }
        return v;
    }
}
