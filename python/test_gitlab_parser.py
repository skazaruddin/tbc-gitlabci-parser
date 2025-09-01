"""
Tests for GitLab CI/CD Template Parser Python implementation.
"""

import pytest
from gitlab_template_parser import GitLabTemplateParser


class TestGitLabTemplateParser:

    def test_apply_java21_upgrade(self, tmp_path):
        before_yaml = """
include:
  - component: $CI_SERVER_FQDN/to-be-continuous/maven/gitlab-ci-maven@5.0
    inputs:
      image: "maven:3.8-openjdk-11"

maven-job:
  stage: build
  image: maven:3.8-openjdk-11
  script:
    - mvn package
sonar-job:
  stage: scan
  image: sonarsource/sonar-scanner-cli:4.8
  script:
    - sonar-scanner
"""
        after_yaml = """
include:
  - component: $CI_SERVER_FQDN/to-be-continuous/maven/gitlab-ci-maven@5.0
    inputs:
      image: maven:3.8-openjdk-21
maven-job:
  stage: build
  image: maven:3.8-openjdk-21
  script:
    - mvn package
sonar-job:
  stage: scan
  image: sonarsource/sonar-scanner-cli:latest
  script:
    - sonar-scanner
"""
        import os
        parser = GitLabTemplateParser()
        # Create temp file
        gitlab_file = tmp_path / "gitlab-ci.yml"
        gitlab_file.write_text(before_yaml)
        print("Before Upgrade:")
        print(before_yaml)

        # Apply upgrade to file
        parser.apply_java21_upgrade(str(gitlab_file))

        # Read back file content
        output_yaml = gitlab_file.read_text()
        print("After Upgrade:")
        print(output_yaml)
        assert output_yaml.strip() == after_yaml.strip() 
    # Compare YAML structures for robustness
    def setup_method(self):
        self.parser = GitLabTemplateParser()
