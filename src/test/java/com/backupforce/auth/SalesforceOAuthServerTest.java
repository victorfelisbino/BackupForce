package com.backupforce.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SalesforceOAuthServer
 */
public class SalesforceOAuthServerTest {
    
    private SalesforceOAuthServer oauthServer;
    
    @AfterEach
    void cleanup() {
        if (oauthServer != null) {
            oauthServer.stopServer();
            oauthServer = null;
        }
    }
    
    @Test
    void testOAuthResult_successConstruction() {
        SalesforceOAuthServer.OAuthResult result = new SalesforceOAuthServer.OAuthResult(
            "test_access_token",
            "https://test.salesforce.com",
            "test_refresh_token"
        );
        
        assertTrue(result.isSuccess());
        assertFalse(result.isCancelled());
        assertEquals("test_access_token", result.accessToken);
        assertEquals("https://test.salesforce.com", result.instanceUrl);
        assertEquals("test_refresh_token", result.refreshToken);
        assertNull(result.error);
    }
    
    @Test
    void testOAuthResult_errorConstruction() {
        SalesforceOAuthServer.OAuthResult result = new SalesforceOAuthServer.OAuthResult("Test error message");
        
        assertFalse(result.isSuccess());
        assertFalse(result.isCancelled());
        assertEquals("Test error message", result.error);
        assertNull(result.accessToken);
        assertNull(result.instanceUrl);
        assertNull(result.refreshToken);
    }
    
    @Test
    void testOAuthResult_cancelledState() {
        SalesforceOAuthServer.OAuthResult result = new SalesforceOAuthServer.OAuthResult("CANCELLED");
        
        assertFalse(result.isSuccess());
        assertTrue(result.isCancelled());
        assertEquals("CANCELLED", result.error);
    }
    
    @Test
    void testSetTimeout() {
        oauthServer = new SalesforceOAuthServer();
        
        // Default timeout should be 180 seconds
        assertEquals(180, oauthServer.getTimeout());
        
        // Set custom timeout
        oauthServer.setTimeout(60);
        assertEquals(60, oauthServer.getTimeout());
        
        // Set another timeout
        oauthServer.setTimeout(300);
        assertEquals(300, oauthServer.getTimeout());
    }
    
    @Test
    void testCancel_beforeAuthentication() {
        oauthServer = new SalesforceOAuthServer();
        
        // Cancel should not throw even if not authenticated yet
        assertDoesNotThrow(() -> oauthServer.cancel());
    }
    
    @Test
    void testStopServer_beforeStart() {
        oauthServer = new SalesforceOAuthServer();
        
        // stopServer should not throw even if server not started
        assertDoesNotThrow(() -> oauthServer.stopServer());
    }
    
    @Test
    void testMultipleStopServerCalls() {
        oauthServer = new SalesforceOAuthServer();
        
        // Multiple stops should be safe
        assertDoesNotThrow(() -> {
            oauthServer.stopServer();
            oauthServer.stopServer();
            oauthServer.stopServer();
        });
    }
    
    @Test
    void testCancelStopsServer() {
        oauthServer = new SalesforceOAuthServer();
        
        // Cancel should also stop the server
        assertDoesNotThrow(() -> {
            oauthServer.cancel();
            // Should be safe to call stop again
            oauthServer.stopServer();
        });
    }
}
