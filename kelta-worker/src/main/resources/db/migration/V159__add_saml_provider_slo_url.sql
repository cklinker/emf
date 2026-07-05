-- Rec 8 (SLO): per-IdP Single Logout service URL.
-- The IdP's SingleLogoutService location. When set, the SP advertises an SLO
-- endpoint in its metadata and honors IdP-initiated LogoutRequests (and can send
-- SP-initiated LogoutRequests to this URL). Nullable — providers without SLO keep
-- working exactly as before.
ALTER TABLE saml_provider ADD COLUMN slo_url VARCHAR(500);
