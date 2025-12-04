package com.backupforce.auth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SalesforceAuth {
    private static final Logger logger = LoggerFactory.getLogger(SalesforceAuth.class);
    
    private String accessToken;
    private String instanceUrl;

    public void authenticate(String username, String password, String loginUrl) throws IOException {
        // OAuth 2.0 Username-Password Flow
        // Note: This requires a Connected App. For simplicity, we'll use SOAP login first
        // and extract the session ID
        
        String tokenEndpoint = loginUrl + "/services/oauth2/token";
        
        // For now, we'll use a simpler approach with session ID from Partner API
        // In production, you'd want to use OAuth with client_id and client_secret
        
        throw new UnsupportedOperationException(
            "OAuth authentication requires Connected App setup. " +
            "For now, use session ID from existing connection."
        );
    }

    public void setSessionInfo(String accessToken, String instanceUrl) {
        this.accessToken = accessToken;
        this.instanceUrl = instanceUrl;
        logger.info("Session set - Instance URL: {}", instanceUrl);
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getInstanceUrl() {
        return instanceUrl;
    }
}
