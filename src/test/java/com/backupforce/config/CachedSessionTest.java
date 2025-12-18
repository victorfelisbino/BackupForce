package com.backupforce.config;

import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ConnectionManager.CachedSession class.
 * Tests the session caching feature for SSO database connections.
 */
@DisplayName("CachedSession Tests")
class CachedSessionTest {
    
    private Connection mockConnection;
    
    @BeforeEach
    void setUp() {
        mockConnection = mock(Connection.class);
    }
    
    @Test
    @DisplayName("CachedSession stores connection and ID correctly")
    void testCachedSessionConstruction() {
        ConnectionManager.CachedSession session = 
            new ConnectionManager.CachedSession("test-conn-id", mockConnection);
        
        assertEquals("test-conn-id", session.getConnectionId());
        assertEquals(mockConnection, session.getConnection());
        assertTrue(session.getCreatedAt() > 0);
        assertTrue(session.getCreatedAt() <= System.currentTimeMillis());
    }
    
    @Test
    @DisplayName("CachedSession is valid when connection is open and not timed out")
    void testCachedSessionIsValid() throws SQLException {
        when(mockConnection.isClosed()).thenReturn(false);
        
        ConnectionManager.CachedSession session = 
            new ConnectionManager.CachedSession("test-id", mockConnection);
        
        assertTrue(session.isValid(), "Fresh session with open connection should be valid");
    }
    
    @Test
    @DisplayName("CachedSession is invalid when connection is closed")
    void testCachedSessionInvalidWhenClosed() throws SQLException {
        when(mockConnection.isClosed()).thenReturn(true);
        
        ConnectionManager.CachedSession session = 
            new ConnectionManager.CachedSession("test-id", mockConnection);
        
        assertFalse(session.isValid(), "Session with closed connection should be invalid");
    }
    
    @Test
    @DisplayName("CachedSession is invalid when connection is null")
    void testCachedSessionInvalidWhenNull() {
        ConnectionManager.CachedSession session = 
            new ConnectionManager.CachedSession("test-id", null);
        
        assertFalse(session.isValid(), "Session with null connection should be invalid");
    }
    
    @Test
    @DisplayName("CachedSession is invalid when connection throws SQLException")
    void testCachedSessionInvalidOnSqlException() throws SQLException {
        when(mockConnection.isClosed()).thenThrow(new SQLException("Connection error"));
        
        ConnectionManager.CachedSession session = 
            new ConnectionManager.CachedSession("test-id", mockConnection);
        
        assertFalse(session.isValid(), "Session should be invalid when isClosed() throws");
    }
    
    @Test
    @DisplayName("CachedSession close() closes the connection")
    void testCachedSessionClose() throws SQLException {
        ConnectionManager.CachedSession session = 
            new ConnectionManager.CachedSession("test-id", mockConnection);
        
        session.close();
        
        verify(mockConnection, times(1)).close();
    }
    
    @Test
    @DisplayName("CachedSession close() handles null connection gracefully")
    void testCachedSessionCloseWithNull() {
        ConnectionManager.CachedSession session = 
            new ConnectionManager.CachedSession("test-id", null);
        
        assertDoesNotThrow(() -> session.close());
    }
    
    @Test
    @DisplayName("CachedSession close() handles SQLException gracefully")
    void testCachedSessionCloseHandlesSqlException() throws SQLException {
        doThrow(new SQLException("Close failed")).when(mockConnection).close();
        
        ConnectionManager.CachedSession session = 
            new ConnectionManager.CachedSession("test-id", mockConnection);
        
        assertDoesNotThrow(() -> session.close());
    }
}
