# Contributing to OverWatch-ML

First off, thank you for considering contributing to OverWatch-ML! It's people like you that make this project great.

## Welcome

We welcome contributions in several key areas:
- **ML Algorithms:** Enhancing our Isolation Forest or Autoencoder models, or adding new features to the behavior vector.
- **GUI:** Improving the Staff Menu, adding new management screens, or optimizing inventory interactions.
- **Documentation:** Expanding our wiki, improving this README, or writing tutorials.
- **Testing:** Running OverWatch-ML on real, populated servers and reporting edge cases or false positives.

## Bug Reports

When filing a bug report, please include as much detail as possible. Use the following template:
- **Plugin Version:** (e.g., 2.0.0)
- **Server Version:** (e.g., Paper 1.21)
- **Description:** A clear and concise description of what the bug is.
- **Steps to Reproduce:** Exactly what you did to trigger the bug.
- **Expected Behavior:** What you expected to happen.
- **Actual Behavior:** What actually happened.
- **Console Output:** Any error logs or stack traces (use pastebin or code blocks).

## Feature Suggestions

We love new ideas, but we need to understand the *problem* you're trying to solve. Please provide a clear **use case** for your feature suggestion, not just the technical solution. Explain how it benefits server administrators or improves detection accuracy.

## Development Setup

1. **Fork** the repository on GitHub.
2. **Clone** your fork locally.
3. Ensure you have **Java 21** installed.
4. Run the **Maven Build**: `mvn clean package` to ensure everything compiles.
5. Set up a local **Test Server** running Paper to deploy and test your changes.

## Code Style

- Follow standard **Java naming conventions**.
- Keep lines to a maximum of **120 characters**.
- Provide **Javadoc** for all public methods and classes.
- All comments and variable names **must be in English**.

## Pull Request Process

1. Create a new branch for your feature (`git checkout -b feature/my-new-feature`).
2. Make your changes, adhering to the code style.
3. Test your changes thoroughly on a local server.
4. Commit your changes with clear, descriptive commit messages.
5. Push your branch to your fork and submit a **Pull Request** to the `main` branch of this repository.

## ML Contributions

Changes to the Machine Learning pipeline require extra scrutiny. Any proposed changes to detection algorithms, feature engineering, or model parameters must be accompanied by:
- A clear **justification** explaining the research or logic behind the change.
- **Metrics** demonstrating the impact (e.g., detection rates, false positive rates, performance impact) before and after the change on a substantial dataset.
