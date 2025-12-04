package com.backupforce.auth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;

public class OAuthCallbackServer {
    private static final Logger logger = LoggerFactory.getLogger(OAuthCallbackServer.class);
    // PlatformCLI requires port 1717 with path /OauthRedirect
    private static final int PORT = 1717;
    
    private HttpServer server;
    private CompletableFuture<String> authCodeFuture;
    
    public CompletableFuture<String> start() throws IOException {
        authCodeFuture = new CompletableFuture<>();
        
        server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/OauthRedirect", new CallbackHandler());
        server.setExecutor(null);
        server.start();
        
        logger.info("OAuth callback server started on port {}", PORT);
        return authCodeFuture;
    }
    
    public void stop() {
        if (server != null) {
            server.stop(0);
            logger.info("OAuth callback server stopped");
        }
    }
    
    private class CallbackHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery();
            String authCode = null;
            String error = null;
            
            if (query != null) {
                String[] params = query.split("&");
                for (String param : params) {
                    String[] pair = param.split("=");
                    if (pair.length == 2) {
                        if ("code".equals(pair[0])) {
                            authCode = pair[1];
                        } else if ("error".equals(pair[0])) {
                            error = pair[1];
                        }
                    }
                }
            }
            
            String response;
            if (authCode != null) {
                response = "<!DOCTYPE html><html><head><meta charset='UTF-8'><title>Success</title><style>" +
                          "body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Arial,sans-serif;margin:0;padding:0;display:flex;align-items:center;justify-content:center;min-height:100vh;background:linear-gradient(135deg,#667eea 0%,#764ba2 100%);}" +
                          ".container{background:white;padding:60px 40px;border-radius:20px;box-shadow:0 20px 60px rgba(0,0,0,0.3);text-align:center;max-width:400px;}" +
                          ".checkmark{width:80px;height:80px;border-radius:50%;display:inline-block;background:#5cb85c;margin-bottom:20px;position:relative;}" +
                          ".checkmark:after{content:'✓';position:absolute;top:50%;left:50%;transform:translate(-50%,-50%);color:white;font-size:50px;font-weight:bold;}" +
                          "h1{color:#333;margin:20px 0;font-size:28px;font-weight:600;}" +
                          "p{color:#666;font-size:16px;margin:15px 0;line-height:1.6;}" +
                          "</style></head><body>" +
                          "<div class='container'><div class='checkmark'></div>" +
                          "<h1>Authentication Successful!</h1>" +
                          "<p>You can close this window and return to BackupForce.</p>" +
                          "<script>setTimeout(function(){window.close()},3000);</script>" +
                          "</div></body></html>";
                authCodeFuture.complete(authCode);
            } else {
                response = "<!DOCTYPE html><html><head><meta charset='UTF-8'><title>Error</title><style>" +
                          "body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Arial,sans-serif;margin:0;padding:0;display:flex;align-items:center;justify-content:center;min-height:100vh;background:linear-gradient(135deg,#f093fb 0%,#f5576c 100%);}" +
                          ".container{background:white;padding:60px 40px;border-radius:20px;box-shadow:0 20px 60px rgba(0,0,0,0.3);text-align:center;max-width:400px;}" +
                          ".error-icon{width:80px;height:80px;border-radius:50%;display:inline-block;background:#d9534f;margin-bottom:20px;position:relative;}" +
                          ".error-icon:after{content:'✗';position:absolute;top:50%;left:50%;transform:translate(-50%,-50%);color:white;font-size:50px;font-weight:bold;}" +
                          "h1{color:#333;margin:20px 0;font-size:28px;font-weight:600;}" +
                          "p{color:#666;font-size:16px;margin:15px 0;line-height:1.6;}" +
                          ".error-detail{background:#f8f8f8;padding:10px;border-radius:5px;font-family:monospace;font-size:14px;color:#d9534f;margin-top:20px;}" +
                          "</style></head><body>" +
                          "<div class='container'><div class='error-icon'></div>" +
                          "<h1>Authentication Failed</h1>" +
                          "<p>Please close this window and try again in BackupForce.</p>" +
                          (error != null ? "<div class='error-detail'>Error: " + error + "</div>" : "") +
                          "</div></body></html>";
                authCodeFuture.completeExceptionally(new Exception("OAuth failed: " + error));
            }
            
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, response.getBytes().length);
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
            
            // Stop server after a short delay
            new Thread(() -> {
                try {
                    Thread.sleep(2000);
                    stop();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        }
    }
}
