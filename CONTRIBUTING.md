# Contributing to BackupForce

Thank you for your interest in contributing to BackupForce! 🎉

## How to Contribute

### Reporting Bugs
1. Check if the bug has already been reported in [Issues](https://github.com/victorfelisbino/backupforce-javafx/issues)
2. If not, create a new issue with:
   - Clear title describing the problem
   - Steps to reproduce
   - Expected vs actual behavior
   - Your environment (Windows version, Java version if building from source)

### Suggesting Features
1. Open an issue with the `enhancement` label
2. Describe the feature and why it would be useful
3. Include mockups or examples if possible

### Submitting Code Changes

1. **Fork the repository**
2. **Create a feature branch**
   ```bash
   git checkout -b feature/your-feature-name
   ```
3. **Make your changes**
   - Follow existing code style
   - Add tests if applicable
   - Update documentation if needed
4. **Test your changes**
   ```bash
   mvn test
   mvn javafx:run
   ```
5. **Commit with a clear message**
   ```bash
   git commit -m "Add: description of your change"
   ```
6. **Push and create a Pull Request**
   ```bash
   git push origin feature/your-feature-name
   ```

### Pull Request Guidelines
- PRs require approval before merging
- Keep changes focused and atomic
- Include screenshots for UI changes
- Reference related issues (e.g., "Fixes #123")

## Development Setup

### Prerequisites
- Java 11+
- Maven 3.6+

### Building
```bash
# Run in development mode
mvn javafx:run

# Build JAR
mvn clean package

# Build portable exe
.\build-portable.ps1

# Run tests
mvn test
```

### Project Structure
```
src/main/java/com/backupforce/
├── auth/          # OAuth and Salesforce authentication
├── bulkv2/        # Bulk API v2 client
├── config/        # Configuration management
├── sink/          # Data export (CSV, databases)
│   └── dialect/   # Database-specific SQL dialects
└── ui/            # JavaFX controllers and UI logic
```

## Code Style
- Use 4 spaces for indentation
- Follow Java naming conventions
- Add Javadoc for public methods
- Keep methods focused and small

## Questions?
Open an issue or reach out to the maintainer.

Thank you for contributing! 🙏
