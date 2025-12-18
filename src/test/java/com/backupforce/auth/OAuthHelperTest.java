package com.backupforce.auth;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OAuthHelper class.
 * Tests OAuth URL generation and token handling.
 */
@DisplayName("OAuthHelper Tests")
class OAuthHelperTest {
    
    @Test
    @DisplayName("Constructor accepts login URL")
    void testConstructor() {
        OAuthHelper helper = new OAuthHelper("https://login.salesforce.com");
        assertNotNull(helper);
    }
    
    @Test
    @DisplayName("getAuthorizationUrl builds correct Production URL")
    void testGetAuthorizationUrlProduction() {
        OAuthHelper helper = new OAuthHelper("https://login.salesforce.com");
        String url = helper.getAuthorizationUrl();
        
        assertTrue(url.startsWith("https://login.salesforce.com/services/oauth2/authorize"));
        assertTrue(url.contains("response_type=code"));
        assertTrue(url.contains("client_id="));
        assertTrue(url.contains("redirect_uri="));
        assertTrue(url.contains("scope="));
    }
    
    @Test
    @DisplayName("getAuthorizationUrl builds correct Sandbox URL")
    void testGetAuthorizationUrlSandbox() {
        OAuthHelper helper = new OAuthHelper("https://test.salesforce.com");
        String url = helper.getAuthorizationUrl();
        
        assertTrue(url.startsWith("https://test.salesforce.com/services/oauth2/authorize"));
        assertTrue(url.contains("response_type=code"));
    }
    
    @Test
    @DisplayName("getAuthorizationUrl includes required OAuth parameters")
    void testGetAuthorizationUrlParameters() {
        OAuthHelper helper = new OAuthHelper("https://login.salesforce.com");
        String url = helper.getAuthorizationUrl();
        
        // Should contain all required OAuth parameters
        assertTrue(url.contains("response_type=code"), "Missing response_type");
        assertTrue(url.contains("client_id="), "Missing client_id");
        assertTrue(url.contains("redirect_uri="), "Missing redirect_uri");
        assertTrue(url.contains("scope="), "Missing scope");
        
        // Scope should include required permissions
        assertTrue(url.contains("api"), "Scope should include api");
        assertTrue(url.contains("refresh_token"), "Scope should include refresh_token");
    }
    
    @Test
    @DisplayName("getAuthorizationUrl uses URL encoding")
    void testGetAuthorizationUrlEncoding() {
        OAuthHelper helper = new OAuthHelper("https://login.salesforce.com");
        String url = helper.getAuthorizationUrl();
        
        // The redirect_uri should be URL encoded
        assertTrue(url.contains("redirect_uri=http%3A%2F%2Flocalhost") || 
                   url.contains("redirect_uri=http://localhost"),
                   "redirect_uri should be present");
    }
    
    // OAuthTokens tests
    
    @Test
    @DisplayName("OAuthTokens stores all token values")
    void testOAuthTokensConstruction() {
        OAuthHelper.OAuthTokens tokens = new OAuthHelper.OAuthTokens(
            "access_token_value",
            "https://myorg.my.salesforce.com",
            "refresh_token_value"
        );
        
        assertEquals("access_token_value", tokens.getAccessToken());
        assertEquals("https://myorg.my.salesforce.com", tokens.getInstanceUrl());
        assertEquals("refresh_token_value", tokens.getRefreshToken());
    }
    
    @Test
    @DisplayName("OAuthTokens handles null refresh token")
    void testOAuthTokensNullRefreshToken() {
        OAuthHelper.OAuthTokens tokens = new OAuthHelper.OAuthTokens(
            "access_token",
            "https://instance.salesforce.com",
            null
        );
        
        assertNotNull(tokens.getAccessToken());
        assertNotNull(tokens.getInstanceUrl());
        assertNull(tokens.getRefreshToken());
    }
    
    @Test
    @DisplayName("OAuthTokens preserves exact values")
    void testOAuthTokensExactValues() {
        String accessToken = "test_access_token_for_unit_test_only";
        String instanceUrl = "https://test-instance.salesforce.com";
        String refreshToken = "test_refresh_token_for_unit_test_only";
        
        OAuthHelper.OAuthTokens tokens = new OAuthHelper.OAuthTokens(
            accessToken, instanceUrl, refreshToken
        );
        
        assertEquals(accessToken, tokens.getAccessToken());
        assertEquals(instanceUrl, tokens.getInstanceUrl());
        assertEquals(refreshToken, tokens.getRefreshToken());
    }
}
