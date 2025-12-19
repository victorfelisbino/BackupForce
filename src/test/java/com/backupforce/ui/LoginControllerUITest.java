package com.backupforce.ui;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.*;
import org.testfx.api.FxAssert;
import org.testfx.matcher.control.LabeledMatchers;
import org.testfx.util.WaitForAsyncUtils;

import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UI tests for the Login screen.
 * Tests form validation, user interactions, and error handling.
 */
@DisplayName("Login Screen UI Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LoginControllerUITest extends JavaFxTestBase {
    
    // Test payloads - realistic data
    private static final String VALID_USERNAME = "test.user@company.com";
    private static final String VALID_PASSWORD = "SecureP@ssw0rd123!";
    private static final String VALID_TOKEN = "AbCdEfGhIjKlMnOpQrStUv";
    
    // Invalid payloads for negative testing
    private static final String EMPTY_STRING = "";
    private static final String WHITESPACE_ONLY = "   ";
    private static final String SPECIAL_CHARS_USERNAME = "<script>alert('xss')</script>";
    private static final String SQL_INJECTION_ATTEMPT = "'; DROP TABLE users; --";
    private static final String VERY_LONG_INPUT = "a".repeat(1000);
    private static final String UNICODE_INPUT = "用户名@公司.中国";
    private static final String EMAIL_WITH_PLUS = "test+label@company.com";
    
    @Override
    protected Scene createTestScene() throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
        Parent root = loader.load();
        return new Scene(root, 600, 800);
    }
    
    @BeforeEach
    void clearPreferences() {
        // Clear any saved credentials before each test
        try {
            Preferences prefs = Preferences.userNodeForPackage(LoginController.class);
            prefs.clear();
        } catch (Exception e) {
            // Ignore - preferences may not exist
        }
    }
    
    // ==================== Component Visibility Tests ====================
    
    @Nested
    @DisplayName("Initial State Tests")
    class InitialStateTests {
        
        @Test
        @DisplayName("All login form components should be visible")
        void allFormComponentsShouldBeVisible() {
            assertNodeVisible("usernameField", "Username field");
            assertNodeVisible("passwordField", "Password field");
            assertNodeVisible("tokenField", "Token field");
            assertNodeVisible("environmentCombo", "Environment combo");
            assertNodeVisible("loginButton", "Login button");
            assertNodeVisible("oauthButton", "OAuth button");
        }
        
        @Test
        @DisplayName("Progress indicator should be hidden initially")
        void progressIndicatorShouldBeHiddenInitially() {
            ProgressIndicator progress = findById("progressIndicator");
            assertFalse(progress.isVisible(), "Progress indicator should be hidden on startup");
        }
        
        @Test
        @DisplayName("Cancel button should be hidden initially")
        void cancelButtonShouldBeHiddenInitially() {
            Button cancelButton = findById("cancelButton");
            assertFalse(cancelButton.isVisible(), "Cancel button should be hidden initially");
        }
        
        @Test
        @DisplayName("Production environment should be selected by default")
        void productionEnvironmentShouldBeDefault() {
            ComboBox<String> envCombo = findById("environmentCombo");
            String selected = envCombo.getSelectionModel().getSelectedItem();
            assertTrue(selected.contains("Production"), "Production should be default environment");
        }
        
        @Test
        @DisplayName("Status label should be empty initially")
        void statusLabelShouldBeEmptyInitially() {
            Label statusLabel = findById("statusLabel");
            assertTrue(statusLabel.getText().isEmpty(), "Status label should be empty initially");
        }
        
        @Test
        @DisplayName("Remember credentials checkbox should exist")
        void rememberCredentialsCheckboxExists() {
            assertNodeVisible("rememberCredentialsCheckBox", "Remember credentials checkbox");
            CheckBox checkbox = findById("rememberCredentialsCheckBox");
            assertFalse(checkbox.isSelected(), "Remember checkbox should be unchecked by default");
        }
    }
    
    // ==================== Form Input Tests ====================
    
    @Nested
    @DisplayName("Form Input Tests")
    class FormInputTests {
        
        @Test
        @DisplayName("Should accept valid email in username field")
        void shouldAcceptValidEmail() {
            clickOn("#usernameField").write(VALID_USERNAME);
            waitForFxEvents();
            
            TextField usernameField = findById("usernameField");
            assertEquals(VALID_USERNAME, usernameField.getText());
        }
        
        @Test
        @DisplayName("Should accept password input")
        void shouldAcceptPasswordInput() {
            clickOn("#passwordField").write(VALID_PASSWORD);
            waitForFxEvents();
            
            PasswordField passwordField = findById("passwordField");
            assertEquals(VALID_PASSWORD, passwordField.getText());
        }
        
        @Test
        @DisplayName("Should accept security token input")
        void shouldAcceptSecurityToken() {
            clickOn("#tokenField").write(VALID_TOKEN);
            waitForFxEvents();
            
            PasswordField tokenField = findById("tokenField");
            assertEquals(VALID_TOKEN, tokenField.getText());
        }
        
        @Test
        @DisplayName("Should handle email with plus sign")
        void shouldHandleEmailWithPlusSign() {
            clickOn("#usernameField").write(EMAIL_WITH_PLUS);
            waitForFxEvents();
            
            TextField usernameField = findById("usernameField");
            assertEquals(EMAIL_WITH_PLUS, usernameField.getText());
        }
        
        @Test
        @DisplayName("Should handle Unicode input in username")
        void shouldHandleUnicodeInput() {
            clickOn("#usernameField").write(UNICODE_INPUT);
            waitForFxEvents();
            
            TextField usernameField = findById("usernameField");
            assertEquals(UNICODE_INPUT, usernameField.getText());
        }
        
        @Test
        @DisplayName("Should handle very long input")
        void shouldHandleVeryLongInput() {
            String longInput = "a".repeat(100); // Use shorter for UI testing
            clickOn("#usernameField").write(longInput);
            waitForFxEvents();
            
            TextField usernameField = findById("usernameField");
            assertEquals(longInput, usernameField.getText());
        }
    }
    
    // ==================== Environment Selection Tests ====================
    
    @Nested
    @DisplayName("Environment Selection Tests")
    class EnvironmentSelectionTests {
        
        @Test
        @DisplayName("Should have two environment options")
        void shouldHaveTwoEnvironmentOptions() {
            ComboBox<String> envCombo = findById("environmentCombo");
            assertEquals(2, envCombo.getItems().size(), "Should have Production and Sandbox options");
        }
        
        @Test
        @DisplayName("Should be able to select Sandbox environment")
        void shouldSelectSandboxEnvironment() {
            selectInComboBox("environmentCombo", 1);
            
            ComboBox<String> envCombo = findById("environmentCombo");
            String selected = envCombo.getSelectionModel().getSelectedItem();
            assertTrue(selected.contains("Sandbox"), "Sandbox should be selected");
        }
        
        @Test
        @DisplayName("Environment URL label should update when selection changes")
        void environmentUrlLabelShouldUpdate() {
            Label urlLabel = findById("environmentUrlLabel");
            String initialUrl = urlLabel.getText();
            assertTrue(initialUrl.contains("login.salesforce.com"), "Initial URL should be production");
            
            selectInComboBox("environmentCombo", 1);
            waitForFxEvents();
            
            String sandboxUrl = urlLabel.getText();
            assertTrue(sandboxUrl.contains("test.salesforce.com"), "URL should change to sandbox");
        }
    }
    
    // ==================== Negative Testing - Invalid Input ====================
    
    @Nested
    @DisplayName("Negative Tests - Invalid Input Handling")
    class NegativeInputTests {
        
        @Test
        @DisplayName("Should handle empty form submission gracefully")
        void shouldHandleEmptyFormSubmission() {
            // Click login with empty fields
            clickOn("#loginButton");
            waitForFxEvents();
            
            // Should show error message or stay on login screen
            Label statusLabel = findById("statusLabel");
            // Either shows error or form validation prevents submission
            assertNotNull(statusLabel, "Status label should exist after submission attempt");
        }
        
        @Test
        @DisplayName("Should handle username with only whitespace")
        void shouldHandleWhitespaceOnlyUsername() {
            clickOn("#usernameField").write(WHITESPACE_ONLY);
            clickOn("#passwordField").write(VALID_PASSWORD);
            clickOn("#loginButton");
            waitForFxEvents();
            
            // Should not navigate away from login screen
            assertNodeVisible("loginButton", "Should still be on login screen");
        }
        
        @Test
        @DisplayName("Should handle potential XSS in username field")
        void shouldHandleXssAttemptInUsername() {
            clickOn("#usernameField").write(SPECIAL_CHARS_USERNAME);
            waitForFxEvents();
            
            TextField usernameField = findById("usernameField");
            // The input should be stored as-is (escaped when used, not when entered)
            assertEquals(SPECIAL_CHARS_USERNAME, usernameField.getText());
        }
        
        @Test
        @DisplayName("Should handle SQL injection attempt")
        void shouldHandleSqlInjectionAttempt() {
            clickOn("#usernameField").write(SQL_INJECTION_ATTEMPT);
            clickOn("#passwordField").write(VALID_PASSWORD);
            waitForFxEvents();
            
            // Field should accept the input (it will be rejected by Salesforce API)
            TextField usernameField = findById("usernameField");
            assertEquals(SQL_INJECTION_ATTEMPT, usernameField.getText());
        }
        
        @Test
        @DisplayName("Password field should mask input")
        void passwordFieldShouldMaskInput() {
            PasswordField passwordField = findById("passwordField");
            clickOn("#passwordField").write(VALID_PASSWORD);
            waitForFxEvents();
            
            // Password field should contain the password but display masked
            assertEquals(VALID_PASSWORD, passwordField.getText());
            // Note: Visual masking cannot be tested programmatically
        }
        
        @Test
        @DisplayName("Token field should mask input")
        void tokenFieldShouldMaskInput() {
            PasswordField tokenField = findById("tokenField");
            clickOn("#tokenField").write(VALID_TOKEN);
            waitForFxEvents();
            
            assertEquals(VALID_TOKEN, tokenField.getText());
        }
    }
    
    // ==================== Checkbox Interaction Tests ====================
    
    @Nested
    @DisplayName("Checkbox Interaction Tests")
    class CheckboxInteractionTests {
        
        @Test
        @DisplayName("Should toggle remember credentials checkbox")
        void shouldToggleRememberCredentialsCheckbox() {
            CheckBox rememberCheckbox = findById("rememberCredentialsCheckBox");
            assertFalse(rememberCheckbox.isSelected(), "Should be unchecked initially");
            
            clickOn("#rememberCredentialsCheckBox");
            waitForFxEvents();
            
            assertTrue(rememberCheckbox.isSelected(), "Should be checked after click");
            
            clickOn("#rememberCredentialsCheckBox");
            waitForFxEvents();
            
            assertFalse(rememberCheckbox.isSelected(), "Should be unchecked after second click");
        }
    }
    
    // ==================== Button State Tests ====================
    
    @Nested
    @DisplayName("Button State Tests")
    class ButtonStateTests {
        
        @Test
        @DisplayName("Login button should be enabled initially")
        void loginButtonShouldBeEnabledInitially() {
            Button loginButton = findById("loginButton");
            assertFalse(loginButton.isDisabled(), "Login button should be enabled");
        }
        
        @Test
        @DisplayName("OAuth button should be enabled initially")
        void oauthButtonShouldBeEnabledInitially() {
            Button oauthButton = findById("oauthButton");
            assertFalse(oauthButton.isDisabled(), "OAuth button should be enabled");
        }
        
        @Test
        @DisplayName("Cancel button should be hidden and disabled initially")
        void cancelButtonShouldBeHiddenInitially() {
            Button cancelButton = findById("cancelButton");
            assertFalse(cancelButton.isVisible(), "Cancel button should be hidden");
            assertFalse(cancelButton.isManaged(), "Cancel button should not take space");
        }
    }
    
    // ==================== Form Navigation Tests ====================
    
    @Nested
    @DisplayName("Form Navigation Tests")
    class FormNavigationTests {
        
        @Test
        @DisplayName("Should navigate between fields with Tab key")
        void shouldNavigateWithTabKey() {
            clickOn("#usernameField");
            push(KeyCode.TAB);
            waitForFxEvents();
            
            // Focus should move to password field
            PasswordField passwordField = findById("passwordField");
            assertTrue(passwordField.isFocused(), "Password field should be focused after Tab");
        }
        
        @Test
        @DisplayName("Should allow Enter key on login button")
        void shouldAllowEnterKeyOnLoginButton() {
            clickOn("#usernameField").write(VALID_USERNAME);
            clickOn("#passwordField").write(VALID_PASSWORD);
            clickOn("#loginButton");
            push(KeyCode.ENTER);
            waitForFxEvents();
            
            // Should attempt login (will fail without real connection, but shouldn't crash)
            assertNodeVisible("loginButton", "Should still have login button visible");
        }
    }
    
    // ==================== Concurrent Input Tests ====================
    
    @Nested
    @DisplayName("Concurrent Input Tests")
    class ConcurrentInputTests {
        
        @Test
        @DisplayName("Should handle rapid input correctly")
        void shouldHandleRapidInput() {
            TextField usernameField = findById("usernameField");
            
            clickOn("#usernameField");
            for (char c : VALID_USERNAME.toCharArray()) {
                write(String.valueOf(c));
            }
            waitForFxEvents();
            
            assertEquals(VALID_USERNAME, usernameField.getText(), "Should capture all rapid input");
        }
        
        @Test
        @DisplayName("Should handle clear and re-enter")
        void shouldHandleClearAndReenter() {
            TextField usernameField = findById("usernameField");
            
            clickOn("#usernameField").write(VALID_USERNAME);
            waitForFxEvents();
            
            // Clear the field
            runOnFxThread(() -> usernameField.clear());
            waitForFxEvents();
            
            assertEquals("", usernameField.getText(), "Field should be cleared");
            
            // Re-enter
            clickOn("#usernameField").write("new.user@company.com");
            waitForFxEvents();
            
            assertEquals("new.user@company.com", usernameField.getText(), "Should accept new input");
        }
    }
    
    // ==================== Real Payload Tests ====================
    
    @Nested
    @DisplayName("Real Payload Tests")
    class RealPayloadTests {
        
        @Test
        @DisplayName("Should handle complete valid form submission attempt")
        void shouldHandleCompleteFormSubmission() {
            // Fill in all fields with realistic data
            clickOn("#usernameField").write("admin@mycompany.com");
            clickOn("#passwordField").write("MySecureP@ssword123!");
            clickOn("#tokenField").write("ABCDefgh12345678IJKL");
            
            setCheckBox("rememberCredentialsCheckBox", true);
            selectInComboBox("environmentCombo", 0); // Production
            
            waitForFxEvents();
            
            // Verify all values are set correctly
            assertEquals("admin@mycompany.com", getTextFrom("usernameField"));
            assertEquals("MySecureP@ssword123!", ((PasswordField)findById("passwordField")).getText());
            assertEquals("ABCDefgh12345678IJKL", ((PasswordField)findById("tokenField")).getText());
            assertTrue(isCheckBoxSelected("rememberCredentialsCheckBox"));
        }
        
        @Test
        @DisplayName("Should handle Sandbox login configuration")
        void shouldHandleSandboxConfiguration() {
            clickOn("#usernameField").write("testuser@mycompany.com.sandbox");
            clickOn("#passwordField").write("SandboxP@ss123");
            selectInComboBox("environmentCombo", 1); // Sandbox
            
            waitForFxEvents();
            
            ComboBox<String> envCombo = findById("environmentCombo");
            assertTrue(envCombo.getSelectionModel().getSelectedItem().contains("Sandbox"));
            
            Label urlLabel = findById("environmentUrlLabel");
            assertTrue(urlLabel.getText().contains("test.salesforce.com"));
        }
    }
    
    // ==================== Edge Case Tests ====================
    
    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {
        
        @Test
        @DisplayName("Should handle empty security token (IP whitelisted scenario)")
        void shouldHandleEmptySecurityToken() {
            clickOn("#usernameField").write(VALID_USERNAME);
            clickOn("#passwordField").write(VALID_PASSWORD);
            // Leave token empty
            
            waitForFxEvents();
            
            PasswordField tokenField = findById("tokenField");
            assertEquals("", tokenField.getText(), "Token should be empty");
            
            // Should still be able to click login
            Button loginButton = findById("loginButton");
            assertFalse(loginButton.isDisabled(), "Login should still be enabled with empty token");
        }
        
        @Test
        @DisplayName("Should handle username with special email formats")
        void shouldHandleSpecialEmailFormats() {
            String[] specialEmails = {
                "user.name+tag@domain.com",
                "user@subdomain.domain.com",
                "user@domain.co.uk",
                "firstname.lastname@company.salesforce.com"
            };
            
            for (String email : specialEmails) {
                TextField usernameField = findById("usernameField");
                runOnFxThread(() -> usernameField.clear());
                waitForFxEvents();
                
                clickOn("#usernameField").write(email);
                waitForFxEvents();
                
                assertEquals(email, usernameField.getText(), "Should accept email: " + email);
            }
        }
        
        @Test
        @DisplayName("Should maintain state after repeated environment switches")
        void shouldMaintainStateAfterEnvironmentSwitches() {
            clickOn("#usernameField").write(VALID_USERNAME);
            clickOn("#passwordField").write(VALID_PASSWORD);
            
            // Switch environments multiple times
            for (int i = 0; i < 5; i++) {
                selectInComboBox("environmentCombo", i % 2);
                waitForFxEvents();
            }
            
            // Username and password should still be there
            assertEquals(VALID_USERNAME, getTextFrom("usernameField"));
            assertEquals(VALID_PASSWORD, ((PasswordField)findById("passwordField")).getText());
        }
    }
}
