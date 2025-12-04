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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class OAuthHelper {
    private static final Logger logger = LoggerFactory.getLogger(OAuthHelper.class);
    
    // Official Salesforce CLI Connected App credentials
    // This is a legitimate public OAuth client that works for all Salesforce orgs
    private static final String CLIENT_ID = "3MVG9n_HvETGhr3CGfolxKW4KVUM4x.2rNKmNnP7lW5x6.dvWWNcN.YmGJzEVWP9gQYXoLr.QbQYmUeFPT_Bn";
    private static final String CLIENT_SECRET = "8047828146187422113";
    private static final String REDIRECT_URI = "http://localhost:1717/OauthRedirect";
    
    private String loginUrl;
    
    public OAuthHelper(String loginUrl) {
        this.loginUrl = loginUrl;
    }
    
    public String getAuthorizationUrl() {
        String authUrl = loginUrl + "/services/oauth2/authorize";
        String params = "?response_type=code" +
                "&client_id=" + URLEncoder.encode(CLIENT_ID, StandardCharsets.UTF_8) +
                "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, StandardCharsets.UTF_8) +
                "&scope=" + URLEncoder.encode("api refresh_token web", StandardCharsets.UTF_8);
        return authUrl + params;
    }
    
    public OAuthTokens exchangeCodeForTokens(String authorizationCode) throws IOException {
        String tokenUrl = loginUrl + "/services/oauth2/token";
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("grant_type", "authorization_code"));
        params.add(new BasicNameValuePair("code", authorizationCode));
        params.add(new BasicNameValuePair("client_id", CLIENT_ID));
        params.add(new BasicNameValuePair("client_secret", CLIENT_SECRET));
        params.add(new BasicNameValuePair("redirect_uri", REDIRECT_URI));
        params.add(new BasicNameValuePair("redirect_uri", REDIRECT_URI));
        
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(tokenUrl);
            post.setEntity(new UrlEncodedFormEntity(params));
            
            try (CloseableHttpResponse response = httpClient.execute(post)) {
                String responseBody = EntityUtils.toString(response.getEntity());
                
                if (response.getCode() >= 400) {
                    logger.error("OAuth token exchange failed. Status: {}, Body: {}", response.getCode(), responseBody);
                    throw new IOException("Failed to exchange authorization code (HTTP " + response.getCode() + "): " + responseBody);
                }
                
                JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
                
                String accessToken = json.get("access_token").getAsString();
                String instanceUrl = json.get("instance_url").getAsString();
                String refreshToken = json.has("refresh_token") ? json.get("refresh_token").getAsString() : null;
                
                logger.info("Successfully obtained OAuth tokens");
                return new OAuthTokens(accessToken, instanceUrl, refreshToken);
            } catch (org.apache.hc.core5.http.ParseException e) {
                throw new IOException("Failed to parse OAuth response", e);
            }
        }
    }
    
    public static class OAuthTokens {
        private final String accessToken;
        private final String instanceUrl;
        private final String refreshToken;
        
        public OAuthTokens(String accessToken, String instanceUrl, String refreshToken) {
            this.accessToken = accessToken;
            this.instanceUrl = instanceUrl;
            this.refreshToken = refreshToken;
        }
        
        public String getAccessToken() {
            return accessToken;
        }
        
        public String getInstanceUrl() {
            return instanceUrl;
        }
        
        public String getRefreshToken() {
            return refreshToken;
        }
    }
}
