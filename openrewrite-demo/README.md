# OpenRewrite GitLab CI Demo

This module demonstrates how to use OpenRewrite recipes with the GitLab Template Parser to automatically update Docker image tags in GitLab CI templates.

## Features

- **OpenRewrite Recipe**: `UpdateGitLabImageTags` - Updates Docker image tags using GitLab Template Parser
- **Plaintext Configuration**: YAML-based recipe definitions for common upgrade scenarios
- **Demonstration Classes**: Shows both OpenRewrite integration and direct parser usage

## Usage

### 1. Using OpenRewrite Maven Plugin

```bash
# Apply Maven Java 17 upgrade recipe
mvn rewrite:run -Drewrite.activeRecipes=com.gitlab.template.recipes.MavenJava17Upgrade

# Apply SonarQube latest upgrade recipe  
mvn rewrite:run -Drewrite.activeRecipes=com.gitlab.template.recipes.SonarQubeLatestUpgrade

# Apply full upgrade suite
mvn rewrite:run -Drewrite.activeRecipes=com.gitlab.template.recipes.GitLabImageUpgrade
```

### 2. Using Custom Configuration

```bash
# Use custom rewrite.yml configuration
mvn rewrite:run -Drewrite.configLocation=rewrite.yml
```

### 3. Running the Demo

```bash
# Compile and run the demonstration
mvn compile exec:java -Dexec.mainClass="com.gitlab.template.openrewrite.GitLabRecipeDemo"

# Run tests
mvn test
```

## Recipe Configuration

The `rewrite.yml` file contains plaintext recipe configurations:

- `GitLabImageUpgrade` - Complete upgrade suite for Maven and SonarQube
- `MavenJava17Upgrade` - Maven Java 11 → 17 upgrade only
- `SonarQubeLatestUpgrade` - SonarQube → latest upgrade only

## Example Transformations

### Maven Java 11 to 17 Upgrade

**Before:**
```yaml
include:
  - component: $CI_SERVER_FQDN/to-be-continuous/maven/gitlab-ci-maven@5.0
    inputs:
      image: "maven:3.8-openjdk-11"

test-job:
  image: maven:3.8-openjdk-11
  script:
    - mvn test
```

**After:**
```yaml
include:
  - component: $CI_SERVER_FQDN/to-be-continuous/maven/gitlab-ci-maven@5.0
    inputs:
      image: "maven:3.8-openjdk-17"

test-job:
  image: maven:3.8-openjdk-17
  script:
    - mvn test
```

## Integration with GitLab Template Parser

The OpenRewrite recipe uses the GitLab Template Parser to:

1. Parse GitLab CI YAML with support for custom tags (`!reference`, `!include`)
2. Update image references in TBC components and job definitions
3. Preserve YAML structure, anchors, and merge keys
4. Serialize back to valid GitLab CI YAML

This approach ensures that complex GitLab CI features are properly handled during automated updates.