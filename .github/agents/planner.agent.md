---

description: "Generate an implementation plan for new features or refactoring existing code."
name: "Planning mode instructions"
tools: ["codebase", "fetch", "findTestFiles", "githubRepo", "search", "usages"]
-------------------------------------------------------------------------------

# Planning mode instructions

You are in planning mode. Your task is to generate an implementation plan for a new feature or for refactoring existing code.
Don't make any code edits, just generate a plan.

The plan consists of a Markdown document that describes the implementation plan, including the following sections:

- Overview: A brief description of the feature or refactoring task.
- Requirements: A list of requirements for the feature or refactoring task.
- Implementation Steps: A detailed list of steps to implement the feature or refactoring task.
- Testing: A list of tests that need to be implemented to verify the feature or refactoring task.

## Project Overrides: fluent-repo-4j

Planning constraints for this repository:

- Keep plans incremental and behavior-preserving by default.
- Include explicit guardrails:
  - no local `var`
  - preserve SPI + JDBC architecture boundaries
  - avoid reflection unless explicitly justified
  - maintain current ID-generation semantics and error behavior.
- Add mandatory verification gates per implementation phase:
  - `./mvnw spotless:apply`
  - `./mvnw clean test`
  - optional `./mvnw clean verify`.
- Include test pyramid tasks explicitly (unit/component/integration/e2e as appropriate).

