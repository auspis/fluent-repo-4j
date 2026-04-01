# Tests

## Tests Coverage (JaCoCo)

Generate a JaCoCo HTML report

```bash
./mvnw clean test jacoco:report
```

The HTML report is generated at `target/site/jacoco/index.html`.

If the JaCoCo `report` goal is bound to the `verify` phase in the POM, you can run:

```bash
./mvnw clean verify
```

CI / non-interactive builds

```bash
./mvnw -B clean verify jacoco:report
```

or, if `report` is bound to `verify`:

```bash
./mvnw -B clean verify
```

Integrating JaCoCo with the test plugins
- Start the JaCoCo agent before tests using the `prepare-agent` execution.
- Configure `prepare-agent` to expose the agent JVM options in a property (commonly `jacocoArgLine`).
- Add `${jacocoArgLine}` to the `argLine` configuration of both `maven-surefire-plugin` and `maven-failsafe-plugin` so unit and integration tests are instrumented.

Coverage enforcement (optional)
- The JaCoCo `check` goal enforces coverage thresholds and fails the build when thresholds are not met. This repository does not enable `check` by default. To enable it later, add a `check` execution bound to `verify` with the desired limits (for example, line >= 0.75, branch >= 0.70) and run `mvn verify` in CI.

Troubleshooting
- No report generated: explicitly run `./mvnw jacoco:report` or verify that `report` is bound to an appropriate lifecycle phase.
- Integration tests not instrumented: ensure `${jacocoArgLine}` is added to Failsafe `argLine` as well as Surefire.
- Multi-module aggregation: collect per-module exec files and use `report-aggregate` or `merge`+`report` in an aggregator POM to produce a combined report.

This file is the canonical reference for running tests and generating coverage reports in this repository. The root `README.md` links to it.

If you want, I can also run `./mvnw -B clean verify jacoco:report` to generate the report now.
