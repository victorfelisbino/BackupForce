package com.backupforce.config;

import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ConnectionManager session cache management.
 * Tests caching, retrieval, invalidation, and cleanup of database sessions.
 */
@DisplayName("Session Cache Manager Tests")
class SessionCacheManagerTest {
    
    private ConnectionManager connectionManager;
    private Connection mockConnection;
    
    @BeforeEach
    void setUp() {
        connectionManager = ConnectionManager.getInstance();
        mockConnection = mock(Connection.class);
        // Clear any existing cached sessions
        connectionManager.closeAllSessions();
    }
    
    @AfterEach
    void tearDown() {
        connectionManager.closeAllSessions();
    }
    
    @Test
    @DisplayName("getCachedSession returns null for unknown connection ID")
    void testGetCachedSessionReturnsNullForUnknown() {
        ConnectionManager.CachedSession session = 
            connectionManager.getCachedSession("unknown-connection-id");
        
        assertNull(session, "Should return null for unknown connection ID");
    }
    
    @Test
    @DisplayName("cacheSession stores and getCachedSession retrieves session")
    void testCacheAndRetrieveSession() throws SQLException {
        when(mockConnection.isClosed()).thenReturn(false);
        
        connectionManager.cacheSession("my-conn-id", mockConnection);
        
        ConnectionManager.CachedSession session = 
            connectionManager.getCachedSession("my-conn-id");
        
        assertNotNull(session, "Should retrieve cached session");
        assertEquals(mockConnection, session.getConnection());
        assertEquals("my-conn-id", session.getConnectionId());
    }
    
    @Test
    @DisplayName("hasCachedSession returns true for valid cached session")
    void testHasCachedSessionReturnsTrue() throws SQLException {
        when(mockConnection.isClosed()).thenReturn(false);
        
        connectionManager.cacheSession("session-check-id", mockConnection);
        
        assertTrue(connectionManager.hasCachedSession("session-check-id"));
    }
    
    @Test
    @DisplayName("hasCachedSession returns false for unknown connection")
    void testHasCachedSessionReturnsFalseForUnknown() {
        assertFalse(connectionManager.hasCachedSession("not-cached-id"));
    }
    
    @Test
    @DisplayName("hasCachedSession returns false for closed connection")
    void testHasCachedSessionReturnsFalseForClosed() throws SQLException {
        when(mockConnection.isClosed()).thenReturn(true);
        
        connectionManager.cacheSession("closed-conn-id", mockConnection);
        
        assertFalse(connectionManager.hasCachedSession("closed-conn-id"));
    }
    
    @Test
    @DisplayName("getCachedSession removes and closes invalid sessions")
    void testGetCachedSessionRemovesInvalidSession() throws SQLException {
        when(mockConnection.isClosed()).thenReturn(true);
        
        connectionManager.cacheSession("invalid-session-id", mockConnection);
        
        ConnectionManager.CachedSession session = 
            connectionManager.getCachedSession("invalid-session-id");
        
        assertNull(session, "Should return null for invalid session");
        verify(mockConnection, times(1)).close();
    }
    
    @Test
    @DisplayName("invalidateSession removes and closes the session")
    void testInvalidateSession() throws SQLException {
        when(mockConnection.isClosed()).thenReturn(false);
        
        connectionManager.cacheSession("to-invalidate-id", mockConnection);
        assertTrue(connectionManager.hasCachedSession("to-invalidate-id"));
        
        connectionManager.invalidateSession("to-invalidate-id");
        
        assertFalse(connectionManager.hasCachedSession("to-invalidate-id"));
        verify(mockConnection, times(1)).close();
    }
    
    @Test
    @DisplayName("invalidateSession handles unknown connection ID gracefully")
    void testInvalidateSessionUnknownId() {
        assertDoesNotThrow(() -> 
            connectionManager.invalidateSession("never-existed-id"));
    }
    
    @Test
    @DisplayName("cacheSession replaces existing session and closes old one")
    void testCacheSessionReplacesExisting() throws SQLException {
        Connection firstConnection = mock(Connection.class);
        Connection secondConnection = mock(Connection.class);
        when(firstConnection.isClosed()).thenReturn(false);
        when(secondConnection.isClosed()).thenReturn(false);
        
        connectionManager.cacheSession("replace-test-id", firstConnection);
        connectionManager.cacheSession("replace-test-id", secondConnection);
        
        // First connection should be closed
        verify(firstConnection, times(1)).close();
        
        // Second connection should be cached
        ConnectionManager.CachedSession session = 
            connectionManager.getCachedSession("replace-test-id");
        assertEquals(secondConnection, session.getConnection());
    }
    
    @Test
    @DisplayName("closeAllSessions closes all cached sessions")
    void testCloseAllSessions() throws SQLException {
        Connection conn1 = mock(Connection.class);
        Connection conn2 = mock(Connection.class);
        Connection conn3 = mock(Connection.class);
        when(conn1.isClosed()).thenReturn(false);
        when(conn2.isClosed()).thenReturn(false);
        when(conn3.isClosed()).thenReturn(false);
        
        connectionManager.cacheSession("conn-1", conn1);
        connectionManager.cacheSession("conn-2", conn2);
        connectionManager.cacheSession("conn-3", conn3);
        
        connectionManager.closeAllSessions();
        
        verify(conn1, times(1)).close();
        verify(conn2, times(1)).close();
        verify(conn3, times(1)).close();
        
        assertFalse(connectionManager.hasCachedSession("conn-1"));
        assertFalse(connectionManager.hasCachedSession("conn-2"));
        assertFalse(connectionManager.hasCachedSession("conn-3"));
    }
    
    @Test
    @DisplayName("Multiple connections can be cached simultaneously")
    void testMultipleConnectionsCached() throws SQLException {
        Connection conn1 = mock(Connection.class);
        Connection conn2 = mock(Connection.class);
        when(conn1.isClosed()).thenReturn(false);
        when(conn2.isClosed()).thenReturn(false);
        
        connectionManager.cacheSession("snowflake-prod", conn1);
        connectionManager.cacheSession("snowflake-dev", conn2);
        
        assertTrue(connectionManager.hasCachedSession("snowflake-prod"));
        assertTrue(connectionManager.hasCachedSession("snowflake-dev"));
        
        assertEquals(conn1, connectionManager.getCachedSession("snowflake-prod").getConnection());
        assertEquals(conn2, connectionManager.getCachedSession("snowflake-dev").getConnection());
    }
}
